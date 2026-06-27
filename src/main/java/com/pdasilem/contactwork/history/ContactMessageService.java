package com.pdasilem.contactwork.history;

import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.project.Project;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;

    public ContactMessageService(ContactMessageRepository contactMessageRepository) {
        this.contactMessageRepository = contactMessageRepository;
    }

    public List<ContactMessage> findByProjectIdAndContactId(UUID projectId, UUID contactId) {
        return contactMessageRepository.findByProjectIdAndContactIdOrderByMessageTimestampAsc(projectId, contactId);
    }

    @Transactional
    public ContactMessage recordOutbound(
            Project project,
            Contact contact,
            String messageId,
            String subject,
            String bodyText,
            String senderEmail,
            String recipientEmail,
            OffsetDateTime messageTimestamp
    ) {
        return save(project, contact, MessageDirection.OUTBOUND, MessageEventType.EMAIL, messageId, null,
                senderEmail, recipientEmail, subject, bodyText, messageTimestamp);
    }

    @Transactional
    public ContactMessage recordOutbound(
            Project project,
            Contact contact,
            String messageId,
            String relatedMessageId,
            String subject,
            String bodyText,
            String senderEmail,
            String recipientEmail,
            OffsetDateTime messageTimestamp
    ) {
        return save(project, contact, MessageDirection.OUTBOUND, MessageEventType.EMAIL, messageId, relatedMessageId,
                senderEmail, recipientEmail, subject, bodyText, messageTimestamp);
    }

    private ContactMessage save(
            Project project,
            Contact contact,
            MessageDirection direction,
            MessageEventType eventType,
            String messageId,
            String relatedMessageId,
            String senderEmail,
            String recipientEmail,
            String subject,
            String bodyText,
            OffsetDateTime messageTimestamp
    ) {
        ContactMessage message = new ContactMessage();
        message.setId(UUID.randomUUID());
        message.setProject(project);
        message.setContact(contact);
        message.setDirection(direction);
        message.setEventType(eventType);
        message.setMessageId(messageId);
        message.setRelatedMessageId(relatedMessageId);
        message.setSenderEmail(senderEmail);
        message.setRecipientEmail(recipientEmail);
        message.setSubject(subject);
        message.setBodyText(bodyText);
        message.setMessageTimestamp(messageTimestamp != null ? messageTimestamp : OffsetDateTime.now());
        message.setCreatedAt(OffsetDateTime.now());
        return contactMessageRepository.save(message);
    }
}
