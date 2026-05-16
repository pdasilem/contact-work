package com.pdasilem.contactwork.contact;

import com.pdasilem.contactwork.api.ContactResponse;
import org.springframework.stereotype.Component;

@Component
public class ContactMapper {

    public ContactResponse toResponse(Contact contact) {
        return new ContactResponse(
                contact.getId(),
                contact.getProject().getId(),
                contact.getOrganizationName(),
                contact.getContactName(),
                contact.getEmail(),
                contact.getNote(),
                contact.getStatus(),
                contact.getOutboundMessageId(),
                contact.getSentAt(),
                contact.getReplyReceivedAt(),
                contact.getBounceReceivedAt(),
                contact.getLastErrorAt(),
                contact.getLastErrorMessage(),
                contact.getCreatedAt(),
                contact.getUpdatedAt()
        );
    }
}
