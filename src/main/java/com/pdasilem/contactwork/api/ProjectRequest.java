package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.project.ProjectStatus;

public record ProjectRequest(
        String name,
        String description,
        ProjectStatus status,
        String letterTemplate,
        String pitchDeck,
        String mailSubject,
        String mailBody,
        String letterAttachmentFilename,
        String pitchDeckAttachmentFilename,
        String mailFrom,
        Long sendDelayMs,
        String inboxSyncCron,
        String gmailUsername,
        String gmailAppPassword
) {
}
