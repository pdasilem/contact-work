package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.history.MessageDirection;
import com.pdasilem.contactwork.history.MessageEventType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ContactMessageResponse(
        UUID id,
        UUID contactId,
        MessageDirection direction,
        MessageEventType eventType,
        String messageId,
        String relatedMessageId,
        String senderEmail,
        String recipientEmail,
        String subject,
        String bodyText,
        OffsetDateTime messageTimestamp,
        OffsetDateTime createdAt
) {
}
