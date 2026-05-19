package com.pdasilem.contactwork.mail;

import java.util.List;

public record MailEnvelope(
        String fromEmail,
        String fromName,
        String toEmail,
        String subject,
        String body,
        List<MailFileAttachment> attachments
) {
}
