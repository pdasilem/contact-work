package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.contact.ContactStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ContactResponse(
        UUID id,
        String organizationName,
        String country,
        String contactName,
        String email,
        String preclinicalNotes,
        String note,
        ContactStatus status,
        String outboundMessageId,
        OffsetDateTime sentAt,
        OffsetDateTime replyReceivedAt,
        OffsetDateTime bounceReceivedAt,
        OffsetDateTime lastErrorAt,
        String lastErrorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
