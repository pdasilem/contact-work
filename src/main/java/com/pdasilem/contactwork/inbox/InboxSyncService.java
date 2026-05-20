package com.pdasilem.contactwork.inbox;

import com.pdasilem.contactwork.common.EmailUtils;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.conversation.MailboxDirection;
import com.pdasilem.contactwork.conversation.MailboxFolder;
import com.pdasilem.contactwork.conversation.MailboxMessage;
import com.pdasilem.contactwork.conversation.MailboxMessageRepository;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.ai.MessageVectorIndexer;
import com.pdasilem.contactwork.mail.GmailImapService;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxSyncService {
    private static final Logger log = LoggerFactory.getLogger(InboxSyncService.class);
    private static final Pattern MESSAGE_ID_PATTERN = Pattern.compile("<[^>]+>");

    private final ContactRepository contactRepository;
    private final MailboxMessageRepository mailboxMessageRepository;
    private final ProjectService projectService;
    private final AppProperties appProperties;
    private final GmailImapService gmailImapService;
    private final MessageVectorIndexer messageVectorIndexer;

    public InboxSyncService(
            ContactRepository contactRepository,
            MailboxMessageRepository mailboxMessageRepository,
            ProjectService projectService,
            AppProperties appProperties,
            GmailImapService gmailImapService,
            MessageVectorIndexer messageVectorIndexer
    ) {
        this.contactRepository = contactRepository;
        this.mailboxMessageRepository = mailboxMessageRepository;
        this.projectService = projectService;
        this.appProperties = appProperties;
        this.gmailImapService = gmailImapService;
        this.messageVectorIndexer = messageVectorIndexer;
    }

    public void verifyConnections(UUID projectId) {
        Project project = projectService.getProject(projectId);
        if (!isConfigured(project)) {
            throw new IllegalStateException("Project Gmail credentials are required before syncing");
        }
        try (Store store = gmailImapService.createImapStore(project)) {
            // Store is already connected in createImapStore.
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to connect to IMAP", ex);
        }
    }

    @Transactional
    public void syncInbox(UUID projectId) {
        Project project = projectService.getProject(projectId);
        syncInbox(project, null, project.getLastMailSyncAt(), true);
    }

    @Transactional
    public void syncInboxForSystem(UUID projectId) {
        Project project = projectService.getProjectForSystem(projectId);
        syncInbox(project, null, project.getLastMailSyncAt(), true);
    }

    @Transactional
    public void syncInbox(UUID projectId, UUID contactId) {
        Project project = projectService.getProject(projectId);
        Contact contact = contactRepository.findByProjectIdAndId(projectId, contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found in project " + projectId + ": " + contactId));
        syncInbox(project, contact, null, false);
    }

    private void syncInbox(Project project, Contact scopedContact, OffsetDateTime lastSyncAt, boolean markProjectSynced) {
        UUID projectId = project.getId();
        if (!isConfigured(project)) {
            throw new IllegalStateException("Project Gmail credentials are required before syncing");
        }

        String username = resolvedGmailUsername(project);
        Map<String, Contact> contactsByEmail = contactsByEmail(projectId, scopedContact);
        TreeSet<FetchedMailboxMessage> fetched = new TreeSet<>(mailboxOrdering());

        log.info("Starting mailbox sync for project {} contactId={} mailbox {} incrementalAfter={}",
                projectId, scopedContact == null ? null : scopedContact.getId(), username, lastSyncAt);
        try (Store store = gmailImapService.createImapStore(project)) {
            for (FolderSpec folderSpec : folderSpecs()) {
                scanFolder(store, folderSpec, project, contactsByEmail, lastSyncAt, fetched);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sync mailbox", ex);
        }

        List<MailboxMessage> newMessages = new ArrayList<>();
        int savedCount = 0;
        int skippedDuplicateCount = 0;
        for (FetchedMailboxMessage message : fetched) {
            if (isDuplicate(projectId, message)) {
                skippedDuplicateCount++;
                continue;
            }
            MailboxMessage saved = mailboxMessageRepository.save(toEntity(project, message));
            newMessages.add(saved);
            applyStatusRules(message);
            savedCount++;
        }

        OffsetDateTime syncCompletedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (markProjectSynced) {
            selectedProjectMarkSynced(projectId, syncCompletedAt);
        }
        log.info(
                "Finished mailbox sync for project {} contactId={} mailbox {} fetched={} saved={} duplicates={} lastMailSyncAt={}",
                projectId,
                scopedContact == null ? null : scopedContact.getId(),
                username,
                fetched.size(),
                savedCount,
                skippedDuplicateCount,
                markProjectSynced ? syncCompletedAt : project.getLastMailSyncAt()
        );

        if (!newMessages.isEmpty()) {
            tryIndexMessages(newMessages);
        }
    }

    private void scanFolder(
            Store store,
            FolderSpec folderSpec,
            Project project,
            Map<String, Contact> contactsByEmail,
            OffsetDateTime lastSyncAt,
            Set<FetchedMailboxMessage> fetched
    ) throws Exception {
        Folder folder = store.getFolder(folderSpec.name());
        if (folder == null || !folder.exists()) {
            log.warn("Mailbox folder {} does not exist for project {}", folderSpec.name(), project.getId());
            return;
        }
        folder.open(Folder.READ_ONLY);
        try {
            Message[] messages = folder.getMessages();
            for (Message message : messages) {
                OffsetDateTime serviceDate = serviceDate(message, folderSpec.folder());
                if (lastSyncAt != null && !serviceDate.isAfter(lastSyncAt)) {
                    continue;
                }
                processFetchedMessage(project, contactsByEmail, folderSpec.folder(), message, serviceDate)
                        .ifPresent(fetched::add);
            }
        } finally {
            folder.close(false);
        }
    }

    Map<String, Contact> contactsByEmail(UUID projectId, Contact scopedContact) {
        if (scopedContact != null) {
            return Map.of(EmailUtils.normalize(scopedContact.getEmail()), scopedContact);
        }
        return contactRepository.findByProjectIdAndDeletedAtIsNull(projectId).stream()
                .collect(Collectors.toMap(contact -> EmailUtils.normalize(contact.getEmail()), Function.identity()));
    }

    private Optional<FetchedMailboxMessage> processFetchedMessage(
            Project project,
            Map<String, Contact> contactsByEmail,
            MailboxFolder folder,
            Message message,
            OffsetDateTime serviceDate
    ) throws Exception {
        if (isBounce(message)) {
            updateBounceStatus(project, message);
        }

        AddressSet addresses = addresses(message);
        List<Contact> matchedContacts = matchedContacts(addresses, contactsByEmail);
        if (matchedContacts.size() != 1) {
            if (matchedContacts.size() > 1) {
                log.warn("Skipping mailbox message with multiple contact matches: projectId={} subject={}",
                        project.getId(), message.getSubject());
            }
            return Optional.empty();
        }

        String bodyText = extractBodyText(message);
        String messageId = firstHeader(message, "Message-ID").map(this::normalizeMessageId).orElse(null);
        String contentHash = contentHash(addresses.sender(), addresses.recipients(), addresses.cc(), message.getSubject(), bodyText);
        return Optional.of(new FetchedMailboxMessage(
                matchedContacts.getFirst(),
                folder,
                folder == MailboxFolder.SENT ? MailboxDirection.SENT : MailboxDirection.RECEIVED,
                serviceDate,
                messageId,
                addresses.sender(),
                String.join(", ", addresses.recipients()),
                String.join(", ", addresses.cc()),
                message.getSubject(),
                bodyText,
                contentHash
        ));
    }

    List<Contact> matchedContacts(AddressSet addresses, Map<String, Contact> contactsByEmail) {
        return addresses.all().stream()
                .map(address -> contactsByEmail.get(EmailUtils.normalize(address)))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void applyStatusRules(FetchedMailboxMessage message) {
        if (message.direction() != MailboxDirection.RECEIVED) {
            return;
        }
        Contact contact = message.contact();
        if (!Objects.equals(EmailUtils.normalize(message.senderEmail()), EmailUtils.normalize(contact.getEmail()))) {
            return;
        }
        if (contact.getStatus() == ContactStatus.BOUNCED || contact.getStatus() == ContactStatus.SEND_FAILED) {
            return;
        }
        contact.setStatus(ContactStatus.REPLIED);
        if (contact.getReplyReceivedAt() == null) {
            contact.setReplyReceivedAt(message.serviceDate());
        }
        contactRepository.save(contact);
    }

    private void updateBounceStatus(Project project, Message message) throws Exception {
        Optional<String> messageId = extractRelatedMessageId(message);
        if (messageId.isEmpty()) {
            log.warn("Bounce message did not contain a matchable outbound message id");
            return;
        }
        contactRepository.findByProjectIdAndOutboundMessageId(project.getId(), messageId.get())
                .ifPresent(contact -> {
                    contact.setStatus(ContactStatus.BOUNCED);
                    if (contact.getBounceReceivedAt() == null) {
                        contact.setBounceReceivedAt(serviceDate(message, MailboxFolder.INBOX));
                    }
                    contactRepository.save(contact);
                    log.info("Marked contact {} as BOUNCED", contact.getEmail());
                });
    }

    private boolean isDuplicate(UUID projectId, FetchedMailboxMessage message) {
        if (message.normalizedMessageId() != null) {
            return mailboxMessageRepository.existsByProjectIdAndNormalizedMessageId(projectId, message.normalizedMessageId());
        }
        return mailboxMessageRepository.existsByProjectIdAndContentHashAndNormalizedMessageIdIsNull(projectId, message.contentHash());
    }

    private MailboxMessage toEntity(Project project, FetchedMailboxMessage fetched) {
        MailboxMessage entity = new MailboxMessage();
        entity.setProject(project);
        entity.setContact(fetched.contact());
        entity.setFolder(fetched.folder());
        entity.setDirection(fetched.direction());
        entity.setServiceDate(fetched.serviceDate());
        entity.setNormalizedMessageId(fetched.normalizedMessageId());
        entity.setSenderEmail(fetched.senderEmail());
        entity.setRecipientEmails(fetched.recipientEmails());
        entity.setCcEmails(fetched.ccEmails());
        entity.setSubject(fetched.subject());
        entity.setBodyText(fetched.bodyText());
        entity.setContentHash(fetched.contentHash());
        return entity;
    }

    private Comparator<FetchedMailboxMessage> mailboxOrdering() {
        return Comparator.comparing(FetchedMailboxMessage::serviceDate)
                .thenComparing(message -> value(message.normalizedMessageId()))
                .thenComparing(message -> message.folder().name())
                .thenComparing(FetchedMailboxMessage::contentHash);
    }

    private List<FolderSpec> folderSpecs() {
        AppProperties.Gmail gmail = appProperties.mail().gmail();
        return List.of(
                new FolderSpec(MailboxFolder.INBOX, gmail == null ? "INBOX" : gmail.inboxFolder()),
                new FolderSpec(MailboxFolder.SENT, gmail == null ? "[Gmail]/Sent Mail" : gmail.sentFolder()),
                new FolderSpec(MailboxFolder.SPAM, gmail == null ? "[Gmail]/Spam" : gmail.spamFolder())
        );
    }

    private boolean isBounce(Message message) throws Exception {
        String subject = Optional.ofNullable(message.getSubject()).orElse("").toLowerCase();
        String from = addressStrings(message.getFrom()).stream().findFirst().orElse("");
        String contentType = Optional.ofNullable(message.getContentType()).orElse("").toLowerCase();
        return from.contains("mailer-daemon")
                || subject.contains("delivery status")
                || contentType.contains("multipart/report");
    }

    private Optional<String> extractRelatedMessageId(Message message) throws Exception {
        Optional<String> fromReferences = firstHeader(message, "References")
                .flatMap(this::findFirstMessageId);
        if (fromReferences.isPresent()) {
            return fromReferences.map(this::normalizeMessageId);
        }
        Optional<String> fromReplyTo = firstHeader(message, "In-Reply-To")
                .flatMap(this::findFirstMessageId);
        if (fromReplyTo.isPresent()) {
            return fromReplyTo.map(this::normalizeMessageId);
        }
        Object content = message.getContent();
        if (content instanceof String stringContent) {
            return findFirstMessageId(stringContent).map(this::normalizeMessageId);
        }
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                Object bodyPartContent = multipart.getBodyPart(i).getContent();
                if (bodyPartContent instanceof String stringPart) {
                    Optional<String> candidate = findFirstMessageId(stringPart);
                    if (candidate.isPresent()) {
                        return candidate.map(this::normalizeMessageId);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private AddressSet addresses(Message message) throws MessagingException {
        List<String> from = addressStrings(message.getFrom());
        List<String> to = addressStrings(message.getRecipients(RecipientType.TO));
        List<String> cc = addressStrings(message.getRecipients(RecipientType.CC));
        return new AddressSet(from.isEmpty() ? null : from.getFirst(), to, cc);
    }

    private List<String> addressStrings(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Address address : addresses) {
            if (address instanceof InternetAddress internetAddress) {
                values.add(EmailUtils.normalize(internetAddress.getAddress()));
            } else {
                values.add(EmailUtils.normalize(address.toString()));
            }
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private Optional<String> firstHeader(Message message, String headerName) throws MessagingException {
        String[] values = message.getHeader(headerName);
        if (values == null || values.length == 0) {
            return Optional.empty();
        }
        return Optional.of(values[0]);
    }

    private Optional<String> findFirstMessageId(String text) {
        Matcher matcher = MESSAGE_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group());
        }
        return Optional.empty();
    }

    private String normalizeMessageId(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

boolean isConfigured(Project project) {
        return resolvedGmailUsername(project) != null
                && resolvedGmailAppPassword(project) != null;
    }

    private String resolvedGmailUsername(Project project) {
        return blankToNull(project.getGmailUsername());
    }

    private String resolvedGmailAppPassword(Project project) {
        return blankToNull(project.getGmailAppPassword());
    }

    private void tryIndexMessages(List<MailboxMessage> messages) {
        try {
            for (MailboxMessage message : messages) {
                messageVectorIndexer.index(message);
            }
            log.info("Vector indexed {} new messages", messages.size());
        } catch (Exception ex) {
            log.warn("Failed to index messages in vector store: {}", ex.getMessage());
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private OffsetDateTime serviceDate(Message message, MailboxFolder folder) {
        try {
            Date date = folder == MailboxFolder.SENT ? message.getSentDate() : message.getReceivedDate();
            if (date == null) {
                date = message.getSentDate();
            }
            if (date != null) {
                return OffsetDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
            }
        } catch (MessagingException ex) {
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    String extractBodyText(Part part) throws MessagingException, IOException {
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
            return null;
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content instanceof String stringContent ? stringContent : null;
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            return content instanceof String stringContent ? safeHtmlText(stringContent) : null;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            List<String> htmlFallbacks = new ArrayList<>();
            StringBuilder body = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                Part bodyPart = multipart.getBodyPart(index);
                if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    continue;
                }
                String text = extractBodyText(bodyPart);
                if (text != null && !text.isBlank()) {
                    if (bodyPart.isMimeType("text/html")) {
                        htmlFallbacks.add(text);
                    } else {
                        if (!body.isEmpty()) {
                            body.append("\n");
                        }
                        body.append(text);
                    }
                }
            }
            if (!body.isEmpty()) {
                return body.toString();
            }
            return htmlFallbacks.isEmpty() ? null : String.join("\n", htmlFallbacks);
        }
        return null;
    }

    private String safeHtmlText(String html) {
        return html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String contentHash(String sender, List<String> recipients, List<String> cc, String subject, String bodyText) {
        String source = String.join("\n",
                value(sender),
                String.join(",", recipients),
                String.join(",", cc),
                value(subject),
                value(bodyText));
        byte[] hash = sha256().digest(source.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : hash) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void selectedProjectMarkSynced(UUID projectId, OffsetDateTime syncCompletedAt) {
        if (projectService.canCurrentUserAccess(projectId)) {
            projectService.markMailSynced(projectId, syncCompletedAt);
        } else {
            projectService.markMailSyncedForSystem(projectId, syncCompletedAt);
        }
    }

    private record FolderSpec(MailboxFolder folder, String name) {
    }

    record AddressSet(String sender, List<String> recipients, List<String> cc) {
        List<String> all() {
            List<String> all = new ArrayList<>();
            if (sender != null) {
                all.add(sender);
            }
            all.addAll(recipients);
            all.addAll(cc);
            return all;
        }
    }

    private record FetchedMailboxMessage(
            Contact contact,
            MailboxFolder folder,
            MailboxDirection direction,
            OffsetDateTime serviceDate,
            String normalizedMessageId,
            String senderEmail,
            String recipientEmails,
            String ccEmails,
            String subject,
            String bodyText,
            String contentHash
    ) {
    }
}
