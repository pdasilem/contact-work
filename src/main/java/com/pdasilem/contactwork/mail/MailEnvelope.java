package com.pdasilem.contactwork.mail;

import java.util.List;

public record MailEnvelope(
        String fromEmail,
        String fromName,
        String toEmail,
        String subject,
        String body,
        List<MailFileAttachment> attachments,
        String inReplyToMessageId,
        List<String> referencesMessageIds
) {
    public MailEnvelope(
            String fromEmail,
            String fromName,
            String toEmail,
            String subject,
            String body,
            List<MailFileAttachment> attachments
    ) {
        this(fromEmail, fromName, toEmail, subject, body, attachments, null, List.of());
    }

    public MailEnvelope {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        referencesMessageIds = referencesMessageIds == null ? List.of() : List.copyOf(referencesMessageIds);
    }
}
