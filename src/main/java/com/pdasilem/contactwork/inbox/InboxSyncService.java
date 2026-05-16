package com.pdasilem.contactwork.inbox;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.history.ContactMessageService;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxSyncService {
    private static final Logger log = LoggerFactory.getLogger(InboxSyncService.class);
    private static final Pattern MESSAGE_ID_PATTERN = Pattern.compile("<[^>]+>");
    private static final String IMAP_HOST = "imap.gmail.com";
    private static final int IMAP_PORT = 993;

    private final ContactRepository contactRepository;
    private final MailSyncStateRepository mailSyncStateRepository;
    private final ContactMessageService contactMessageService;
    private final ProjectService projectService;
    private final AppProperties appProperties;

    public InboxSyncService(
            ContactRepository contactRepository,
            MailSyncStateRepository mailSyncStateRepository,
            ContactMessageService contactMessageService,
            ProjectService projectService,
            AppProperties appProperties
    ) {
        this.contactRepository = contactRepository;
        this.mailSyncStateRepository = mailSyncStateRepository;
        this.contactMessageService = contactMessageService;
        this.projectService = projectService;
        this.appProperties = appProperties;
    }

    public void verifyConnections(UUID projectId) {
        Project project = projectService.getProject(projectId);
        if (!isConfigured(project)) {
            throw new IllegalStateException("Mail credentials are not configured");
        }
        try (Store store = createImapStore(project)) {
            // Store is already connected in createImapStore.
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to connect to IMAP", ex);
        }
    }

    @Transactional
    public void syncInbox(UUID projectId) {
        Project project = projectService.getProject(projectId);
        if (!isConfigured(project)) {
            throw new IllegalStateException("Mail credentials are not configured");
        }

        MailSyncState state = mailSyncStateRepository.findById(projectId)
                .orElseGet(() -> newSyncState(project));

        String username = resolvedGmailUsername(project);
        log.info("Starting inbox sync for project {} mailbox {}", projectId, username);
        int scannedCount = 0;
        int replyCount = 0;
        int bounceCount = 0;
        try (Store store = createImapStore(project)) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            UIDFolder uidFolder = (UIDFolder) inbox;
            Message[] messages = uidFolder.getMessagesByUID(state.getLastProcessedUid() + 1, UIDFolder.LASTUID);
            long maxUid = state.getLastProcessedUid();

            if (messages != null) {
                for (Message message : messages) {
                    long uid = uidFolder.getUID(message);
                    scannedCount++;
                    try {
                        SyncOutcome outcome = processMessage(project, message);
                        if (outcome == SyncOutcome.REPLY) {
                            replyCount++;
                        } else if (outcome == SyncOutcome.BOUNCE) {
                            bounceCount++;
                        }
                    } catch (Exception ex) {
                        throw new IllegalStateException("Failed to process inbox message UID " + uid, ex);
                    }
                    maxUid = Math.max(maxUid, uid);
                }
            }

            state.setLastProcessedUid(maxUid);
            mailSyncStateRepository.save(state);
            inbox.close(false);
            log.info(
                    "Finished inbox sync for project {} mailbox {} scanned={} replies={} bounces={} lastUid={}",
                    projectId,
                    username,
                    scannedCount,
                    replyCount,
                    bounceCount,
                    maxUid
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sync inbox", ex);
        }
    }

    private SyncOutcome processMessage(Project project, Message message) throws Exception {
        Optional<String> replyReference = firstHeader(message, "In-Reply-To");
        if (replyReference.isEmpty()) {
            replyReference = firstHeader(message, "References");
        }

        if (replyReference.isPresent()) {
            return updateReplyStatus(project, message, replyReference.get()) ? SyncOutcome.REPLY : SyncOutcome.NONE;
        }

        if (isBounce(message)) {
            Optional<String> messageId = extractMessageId(message);
            if (messageId.isPresent()) {
                return updateBounceStatus(project, message, messageId.get()) ? SyncOutcome.BOUNCE : SyncOutcome.NONE;
            }
            log.warn("Bounce message did not contain a matchable outbound message id");
        }
        return SyncOutcome.NONE;
    }

    private boolean updateReplyStatus(Project project, Message message, String headerValue) throws Exception {
        Optional<String> messageId = findFirstMessageId(headerValue);
        if (messageId.isEmpty()) {
            return false;
        }
        Optional<Contact> contact = contactRepository.findByProjectIdAndOutboundMessageId(project.getId(), messageId.get());
        if (contact.isEmpty()) {
            return false;
        }
        markReplied(contact.get(), message, messageId.get());
        return true;
    }

    private boolean updateBounceStatus(Project project, Message message, String messageId) {
        Optional<Contact> contact = contactRepository.findByProjectIdAndOutboundMessageId(project.getId(), messageId);
        if (contact.isEmpty()) {
            return false;
        }
        Contact matchedContact = contact.get();
        matchedContact.setStatus(ContactStatus.BOUNCED);
        if (matchedContact.getBounceReceivedAt() == null) {
            matchedContact.setBounceReceivedAt(messageTimestamp(message));
        }
        contactRepository.save(matchedContact);
        recordInboundMessage(matchedContact, message, messageId, true);
        log.info("Marked contact {} as BOUNCED", matchedContact.getEmail());
        return true;
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

    private Store createImapStore(Project project) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", IMAP_HOST);
        properties.put("mail.imaps.port", String.valueOf(IMAP_PORT));
        properties.put("mail.imaps.ssl.enable", "true");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imaps");
        store.connect(
                IMAP_HOST,
                resolvedGmailUsername(project),
                resolvedGmailAppPassword(project)
        );
        return store;
    }

    boolean isConfigured(Project project) {
        return resolvedGmailUsername(project) != null
                && resolvedGmailAppPassword(project) != null;
    }

    private String resolvedGmailUsername(Project project) {
        String projectValue = blankToNull(project.getGmailUsername());
        return projectValue != null ? projectValue : blankToNull(appProperties.mail().gmail().username());
    }

    private String resolvedGmailAppPassword(Project project) {
        String projectValue = blankToNull(project.getGmailAppPassword());
        return projectValue != null ? projectValue : blankToNull(appProperties.mail().gmail().appPassword());
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private MailSyncState newSyncState(Project project) {
        MailSyncState state = new MailSyncState();
        state.setProject(project);
        state.setLastProcessedUid(0);
        return state;
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

    private enum SyncOutcome {
        NONE,
        REPLY,
        BOUNCE
    }
}
