package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.history.ContactMessage;
import org.springframework.stereotype.Component;

@Component
public class ContactMessageMapper {

    public ContactMessageResponse toResponse(ContactMessage message) {
        return new ContactMessageResponse(
                message.getId(),
                message.getContact().getId(),
                message.getDirection(),
                message.getEventType(),
                message.getMessageId(),
                message.getRelatedMessageId(),
                message.getSenderEmail(),
                message.getRecipientEmail(),
                message.getSubject(),
                message.getBodyText(),
                message.getMessageTimestamp(),
                message.getCreatedAt()
        );
    }
}
