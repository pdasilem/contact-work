package com.pdasilem.contactwork.history;

import com.pdasilem.contactwork.contact.Contact;
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

    public List<ContactMessage> findByContactId(UUID contactId) {
        return contactMessageRepository.findByContactIdOrderByMessageTimestampAsc(contactId);
    }

    public boolean existsByMessageId(String messageId) {
        return messageId != null && contactMessageRepository.findByMessageId(messageId).isPresent();
    }

    @Transactional
    public ContactMessage recordOutbound(
            Contact contact,
            String messageId,
            String subject,
            String bodyText,
            String senderEmail,
            String recipientEmail,
            OffsetDateTime messageTimestamp
    ) {
        return save(contact, MessageDirection.OUTBOUND, MessageEventType.EMAIL, messageId, null,
                senderEmail, recipientEmail, subject, bodyText, messageTimestamp);
    }

    @Transactional
    public ContactMessage recordInboundReply(
            Contact contact,
            String messageId,
            String relatedMessageId,
            String subject,
            String bodyText,
            String senderEmail,
            String recipientEmail,
            OffsetDateTime messageTimestamp
    ) {
        return save(contact, MessageDirection.INBOUND, MessageEventType.REPLY, messageId, relatedMessageId,
                senderEmail, recipientEmail, subject, bodyText, messageTimestamp);
    }

    @Transactional
    public ContactMessage recordInboundBounce(
            Contact contact,
            String messageId,
            String relatedMessageId,
            String subject,
            String bodyText,
            String senderEmail,
            String recipientEmail,
            OffsetDateTime messageTimestamp
    ) {
        return save(contact, MessageDirection.INBOUND, MessageEventType.BOUNCE, messageId, relatedMessageId,
                senderEmail, recipientEmail, subject, bodyText, messageTimestamp);
    }

    private ContactMessage save(
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
