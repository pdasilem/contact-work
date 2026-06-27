package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.project.Project;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.UnsupportedEncodingException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class GmailSmtpMailTransport implements MailTransport {
    private final MailSenderFactory mailSenderFactory;

    public GmailSmtpMailTransport(MailSenderFactory mailSenderFactory) {
        this.mailSenderFactory = mailSenderFactory;
    }

    @Override
    public MailSendResult send(Project project, MailEnvelope envelope) {
        try {
            JavaMailSenderImpl sender = mailSenderFactory.create(project);
            MimeMessage mimeMessage = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(envelope.toEmail());
            setFrom(helper, envelope);
            helper.setSubject(envelope.subject());
            helper.setText(envelope.body(), false);
            for (MailFileAttachment attachment : envelope.attachments()) {
                DataSource dataSource = new ByteArrayDataSource(attachment.content(), "application/octet-stream");
                helper.addAttachment(attachment.filename(), dataSource);
            }
            applyThreadHeaders(mimeMessage, envelope);
            mimeMessage.saveChanges();
            String messageId = mimeMessage.getMessageID();
            sender.send(mimeMessage);
            return new MailSendResult(messageId);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new IllegalStateException("Failed to send email via Gmail SMTP to " + envelope.toEmail(), ex);
        }
    }

    @Override
    public void verifyConnection(Project project) {
        try {
            mailSenderFactory.create(project).testConnection();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to connect to Gmail SMTP", ex);
        }
    }

    private void setFrom(MimeMessageHelper helper, MailEnvelope envelope)
            throws MessagingException, UnsupportedEncodingException {
        if (envelope.fromEmail() == null || envelope.fromEmail().isBlank()) {
            return;
        }
        if (envelope.fromName() != null && !envelope.fromName().isBlank()) {
            helper.setFrom(new InternetAddress(envelope.fromEmail(), envelope.fromName(), "UTF-8"));
        } else {
            helper.setFrom(envelope.fromEmail());
        }
    }

    static void applyThreadHeaders(MimeMessage message, MailEnvelope envelope) throws MessagingException {
        if (envelope.inReplyToMessageId() != null && !envelope.inReplyToMessageId().isBlank()) {
            message.setHeader("In-Reply-To", envelope.inReplyToMessageId());
        }
        if (!envelope.referencesMessageIds().isEmpty()) {
            message.setHeader("References", String.join(" ", envelope.referencesMessageIds()));
        }
    }
}
