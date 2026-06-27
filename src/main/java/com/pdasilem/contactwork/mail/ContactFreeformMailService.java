package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.ai.MessageVectorIndexer;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactService;
import com.pdasilem.contactwork.conversation.MailboxDirection;
import com.pdasilem.contactwork.conversation.MailboxFolder;
import com.pdasilem.contactwork.conversation.MailboxMessage;
import com.pdasilem.contactwork.conversation.MailboxMessageRepository;
import com.pdasilem.contactwork.history.ContactMessageService;
import com.pdasilem.contactwork.project.MailTransportType;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.project.ProjectStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactFreeformMailService {
    private static final Logger log = LoggerFactory.getLogger(ContactFreeformMailService.class);

    private final ProjectService projectService;
    private final ContactService contactService;
    private final MailTransportRouter mailTransportRouter;
    private final GmailImapService gmailImapService;
    private final ContactMessageService contactMessageService;
    private final MailboxMessageRepository mailboxMessageRepository;
    private final MessageVectorIndexer messageVectorIndexer;

    public ContactFreeformMailService(
            ProjectService projectService,
            ContactService contactService,
            MailTransportRouter mailTransportRouter,
            GmailImapService gmailImapService,
            ContactMessageService contactMessageService,
            MailboxMessageRepository mailboxMessageRepository,
            MessageVectorIndexer messageVectorIndexer
    ) {
        this.projectService = projectService;
        this.contactService = contactService;
        this.mailTransportRouter = mailTransportRouter;
        this.gmailImapService = gmailImapService;
        this.contactMessageService = contactMessageService;
        this.mailboxMessageRepository = mailboxMessageRepository;
        this.messageVectorIndexer = messageVectorIndexer;
    }

    @Transactional
    public MailboxMessage sendNew(UUID projectId, UUID contactId, String subject, String body) {
        return send(projectId, contactId, null, subject, body);
    }

    @Transactional
    public MailboxMessage sendReply(UUID projectId, UUID contactId, UUID parentMailboxMessageId, String subject, String body) {
        if (parentMailboxMessageId == null) {
            throw new IllegalArgumentException("Parent message is required for reply");
        }
        MailboxMessage parent = mailboxMessageRepository.findById(parentMailboxMessageId)
                .orElseThrow(() -> new IllegalArgumentException("Parent mailbox message not found: " + parentMailboxMessageId));
        if (!projectId.equals(parent.getProject().getId()) || !contactId.equals(parent.getContact().getId())) {
            throw new IllegalArgumentException("Parent mailbox message does not belong to this contact");
        }
        return send(projectId, contactId, parent, subject, body);
    }

    private MailboxMessage send(UUID projectId, UUID contactId, MailboxMessage parent, String subject, String body) {
        Project project = projectService.getProject(projectId);
        Contact contact = contactService.getContact(projectId, contactId);
        requireReady(project, subject, body);

        String trimmedSubject = subject.trim();
        String trimmedBody = body.trim();
        List<String> references = references(parent);
        MailEnvelope envelope = new MailEnvelope(
                project.getMailFrom(),
                project.getMailFromName(),
                contact.getEmail(),
                trimmedSubject,
                trimmedBody,
                List.of(),
                parent == null ? null : parent.getNormalizedMessageId(),
                references
        );

        MailSendResult result = mailTransportRouter.resolve(project).send(project, envelope);
        if (project.getMailTransport() == MailTransportType.BREVO) {
            gmailImapService.appendToSentFolder(project, envelope, result.messageId());
        }

        OffsetDateTime sentAt = OffsetDateTime.now();
        contactMessageService.recordOutbound(
                project,
                contact,
                result.messageId(),
                parent == null ? null : parent.getNormalizedMessageId(),
                trimmedSubject,
                trimmedBody,
                project.getMailFrom(),
                contact.getEmail(),
                sentAt
        );

        MailboxMessage saved = saveMailboxMessage(project, contact, result.messageId(), trimmedSubject, trimmedBody, sentAt);
        tryIndex(saved);
        return saved;
    }

    private void requireReady(Project project, String subject, String body) {
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE projects may send email");
        }
        if (project.getMailFrom() == null || project.getMailFrom().isBlank()) {
            throw new IllegalStateException("Project sender address is required before sending");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Body is required");
        }
    }

    private List<String> references(MailboxMessage parent) {
        if (parent == null || parent.getNormalizedMessageId() == null || parent.getNormalizedMessageId().isBlank()) {
            return List.of();
        }
        return List.of(parent.getNormalizedMessageId());
    }

    private MailboxMessage saveMailboxMessage(
            Project project,
            Contact contact,
            String messageId,
            String subject,
            String body,
            OffsetDateTime sentAt
    ) {
        MailboxMessage message = new MailboxMessage();
        message.setProject(project);
        message.setContact(contact);
        message.setFolder(MailboxFolder.SENT);
        message.setDirection(MailboxDirection.SENT);
        message.setServiceDate(sentAt);
        message.setNormalizedMessageId(normalizeMessageId(messageId));
        message.setSenderEmail(project.getMailFrom());
        message.setRecipientEmails(contact.getEmail());
        message.setCcEmails("");
        message.setSubject(subject);
        message.setBodyText(body);
        message.setContentHash(contentHash(project.getMailFrom(), contact.getEmail(), subject, body, messageId));
        return mailboxMessageRepository.save(message);
    }

    private void tryIndex(MailboxMessage message) {
        try {
            messageVectorIndexer.index(message);
        } catch (Exception ex) {
            log.warn("Failed to index freeform outbound message {}: {}", message.getId(), ex.getMessage());
        }
    }

    private String normalizeMessageId(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private String contentHash(String sender, String recipient, String subject, String body, String messageId) {
        String source = String.join("\n",
                value(sender),
                value(recipient),
                value(subject),
                value(body),
                value(messageId));
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
}
