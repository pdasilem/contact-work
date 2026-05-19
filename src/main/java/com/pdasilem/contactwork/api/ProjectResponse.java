package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.project.MailTransportType;
import com.pdasilem.contactwork.project.ProjectStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        ProjectStatus status,
        MailTransportType mailTransport,
        String letterTemplate,
        String mailSubject,
        String mailBody,
        String letterAttachmentFilename,
        String mailFrom,
        String mailFromName,
        long sendDelayMs,
        Integer maxMessagesPerBatch,
        String inboxSyncCron,
        String gmailUsername,
        boolean gmailAppPasswordConfigured,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
