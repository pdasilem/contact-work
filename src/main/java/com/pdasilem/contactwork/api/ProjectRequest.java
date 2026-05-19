package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.project.MailTransportType;
import com.pdasilem.contactwork.project.ProjectStatus;

public record ProjectRequest(
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
        Long sendDelayMs,
        Integer maxMessagesPerBatch,
        String inboxSyncCron,
        String gmailUsername,
        String gmailAppPassword
) {
}
