package com.pdasilem.contactwork.inbox;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.history.ContactMessageService;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxSyncService {
    private static final Logger log = LoggerFactory.getLogger(InboxSyncService.class);
    private static final short SINGLE_ROW_ID = 1;
    private static final Pattern MESSAGE_ID_PATTERN = Pattern.compile("<[^>]+>");
    private static final String IMAP_HOST = "imap.gmail.com";
    private static final int IMAP_PORT = 993;

    private final AppProperties appProperties;
    private final ContactRepository contactRepository;
    private final MailSyncStateRepository mailSyncStateRepository;
    private final ContactMessageService contactMessageService;

    public InboxSyncService(
            AppProperties appProperties,
            ContactRepository contactRepository,
            MailSyncStateRepository mailSyncStateRepository,
            ContactMessageService contactMessageService
    ) {
        this.appProperties = appProperties;
        this.contactRepository = contactRepository;
        this.mailSyncStateRepository = mailSyncStateRepository;
        this.contactMessageService = contactMessageService;
    }

    @Scheduled(cron = "${app.mail.inbox-sync-cron}")
    public void scheduledSync() {
        if (!isConfigured()) {
            return;
        }
        syncInbox();
    }

    public void verifyConnections() {
        if (!isConfigured()) {
            throw new IllegalStateException("Mail credentials are not configured");
        }
        try (Store store = createImapStore()) {
            // Store is already connected in createImapStore.
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to connect to IMAP", ex);
        }
    }

    @Transactional
    public void syncInbox() {
        if (!isConfigured()) {
            throw new IllegalStateException("Mail credentials are not configured");
        }

        MailSyncState state = mailSyncStateRepository.findById(SINGLE_ROW_ID)
                .orElseThrow(() -> new IllegalStateException("mail_sync_state row is missing"));

        try (Store store = createImapStore()) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            UIDFolder uidFolder = (UIDFolder) inbox;
            Message[] messages = uidFolder.getMessagesByUID(state.getLastProcessedUid() + 1, UIDFolder.LASTUID);
            long maxUid = state.getLastProcessedUid();

            if (messages != null) {
                for (Message message : messages) {
                    long uid = uidFolder.getUID(message);
                    processMessage(message);
                    maxUid = Math.max(maxUid, uid);
                }
            }

            state.setLastProcessedUid(maxUid);
            mailSyncStateRepository.save(state);
            inbox.close(false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sync inbox", ex);
        }
    }

    private void processMessage(Message message) throws Exception {
        Optional<String> replyReference = firstHeader(message, "In-Reply-To");
        if (replyReference.isEmpty()) {
            replyReference = firstHeader(message, "References");
        }

        if (replyReference.isPresent()) {
            updateReplyStatus(message, replyReference.get());
            return;
        }

        if (isBounce(message)) {
            extractMessageId(message).ifPresentOrElse(
                    messageId -> updateBounceStatus(message, messageId),
                    () -> log.warn("Bounce message did not contain a matchable outbound message id")
            );
        }
    }

    private void updateReplyStatus(Message message, String headerValue) throws Exception {
        findFirstMessageId(headerValue)
                .flatMap(contactRepository::findByOutboundMessageId)
                .ifPresent(contact -> {
                    markReplied(contact, message, findFirstMessageId(headerValue).orElse(null));
                });
    }

    private void updateBounceStatus(Message message, String messageId) {
        contactRepository.findByOutboundMessageId(messageId).ifPresent(contact -> {
            contact.setStatus(ContactStatus.BOUNCED);
            if (contact.getBounceReceivedAt() == null) {
                contact.setBounceReceivedAt(messageTimestamp(message));
            }
            contactRepository.save(contact);
            recordInboundMessage(contact, message, messageId, true);
            log.info("Marked contact {} as BOUNCED", contact.getEmail());
        });
    }

    private void markReplied(Contact contact, Message message, String relatedMessageId) {
        String messageId = firstHeaderUnchecked(message, "Message-ID").orElse(null);
        if (messageId != null && contactMessageService.existsByMessageId(messageId)) {
            return;
        }
        contact.setStatus(ContactStatus.REPLIED);
        if (contact.getReplyReceivedAt() == null) {
            contact.setReplyReceivedAt(messageTimestamp(message));
        }
        contactRepository.save(contact);
        recordInboundMessage(contact, message, relatedMessageId, false);
        log.info("Marked contact {} as REPLIED", contact.getEmail());
    }

    private void recordInboundMessage(Contact contact, Message message, String relatedMessageId, boolean bounce) {
        try {
            String messageId = firstHeader(message, "Message-ID").orElse(null);
            if (messageId != null && contactMessageService.existsByMessageId(messageId)) {
                return;
            }
            String senderEmail = primaryAddress(message.getFrom());
            String recipientEmail = primaryAddress(message.getRecipients(RecipientType.TO));
            String bodyText = extractBodyText(message);
            if (bounce) {
                contactMessageService.recordInboundBounce(
                        contact,
                        messageId,
                        relatedMessageId,
                        message.getSubject(),
                        bodyText,
                        senderEmail,
                        recipientEmail,
                        messageTimestamp(message)
                );
            } else {
                contactMessageService.recordInboundReply(
                        contact,
                        messageId,
                        relatedMessageId,
                        message.getSubject(),
                        bodyText,
                        senderEmail,
                        recipientEmail,
                        messageTimestamp(message)
                );
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to record inbound message", ex);
        }
    }

    private boolean isBounce(Message message) throws Exception {
        String subject = Optional.ofNullable(message.getSubject()).orElse("").toLowerCase();
        String from = Arrays.stream(message.getFrom() == null ? new Address[0] : message.getFrom())
                .map(address -> address.toString().toLowerCase())
                .findFirst()
                .orElse("");
        String contentType = Optional.ofNullable(message.getContentType()).orElse("").toLowerCase();
        return from.contains("mailer-daemon")
                || subject.contains("delivery status")
                || contentType.contains("multipart/report");
    }

    private Optional<String> extractMessageId(Message message) throws Exception {
        Optional<String> fromHeader = firstHeader(message, "References")
                .flatMap(this::findFirstMessageId);
        if (fromHeader.isPresent()) {
            return fromHeader;
        }
        Object content = message.getContent();
        if (content instanceof String stringContent) {
            return findFirstMessageId(stringContent);
        }
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                Object bodyPartContent = multipart.getBodyPart(i).getContent();
                if (bodyPartContent instanceof String stringPart) {
                    Optional<String> candidate = findFirstMessageId(stringPart);
                    if (candidate.isPresent()) {
                        return candidate;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstHeader(Message message, String headerName) throws Exception {
        String[] values = message.getHeader(headerName);
        if (values == null || values.length == 0) {
            return Optional.empty();
        }
        return Optional.of(values[0]);
    }

    private Optional<String> firstHeaderUnchecked(Message message, String headerName) {
        try {
            return firstHeader(message, headerName);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> findFirstMessageId(String text) {
        Matcher matcher = MESSAGE_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group());
        }
        return Optional.empty();
    }

    private Store createImapStore() throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", IMAP_HOST);
        properties.put("mail.imaps.port", String.valueOf(IMAP_PORT));
        properties.put("mail.imaps.ssl.enable", "true");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imaps");
        store.connect(
                IMAP_HOST,
                appProperties.mail().gmail().username(),
                appProperties.mail().gmail().appPassword()
        );
        return store;
    }

    private boolean isConfigured() {
        return appProperties.mail().gmail().username() != null
                && !appProperties.mail().gmail().username().isBlank()
                && appProperties.mail().gmail().appPassword() != null
                && !appProperties.mail().gmail().appPassword().isBlank();
    }

    private OffsetDateTime messageTimestamp(Message message) {
        try {
            if (message.getSentDate() != null) {
                return OffsetDateTime.ofInstant(message.getSentDate().toInstant(), OffsetDateTime.now().getOffset());
            }
        } catch (MessagingException ignored) {
        }
        return OffsetDateTime.now();
    }

    private String primaryAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        if (addresses[0] instanceof InternetAddress internetAddress) {
            return internetAddress.getAddress() != null ? internetAddress.getAddress().toLowerCase() : null;
        }
        return addresses[0].toString().toLowerCase();
    }

    private String extractBodyText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content instanceof String stringContent ? stringContent : null;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            StringBuilder body = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                String text = extractBodyText(multipart.getBodyPart(index));
                if (text != null && !text.isBlank()) {
                    if (!body.isEmpty()) {
                        body.append("\n");
                    }
                    body.append(text);
                }
            }
            return body.isEmpty() ? null : body.toString();
        }
        return null;
    }
}
