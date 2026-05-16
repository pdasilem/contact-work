package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.history.ContactMessageService;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.asset.MailAttachment;
import com.pdasilem.contactwork.template.GeneratedLetter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;
import org.springframework.core.io.UrlResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class OutboundMailService {
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int SMTP_PORT = 587;

    private final ContactMessageService contactMessageService;
    private final JavaMailSender testMailSender;

    @Autowired
    public OutboundMailService(ContactMessageService contactMessageService) {
        this.contactMessageService = contactMessageService;
        this.testMailSender = null;
    }

    OutboundMailService(
            JavaMailSender testMailSender,
            AppProperties ignoredAppProperties,
            ContactMessageService contactMessageService
    ) {
        this.contactMessageService = contactMessageService;
        this.testMailSender = testMailSender;
    }

    public String send(Project project, Contact contact, GeneratedLetter generatedLetter, List<MailAttachment> attachments) {
        try {
            JavaMailSender javaMailSender = testMailSender != null ? testMailSender : createSender(project);
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(contact.getEmail());
            if (project.getMailFrom() != null && !project.getMailFrom().isBlank()) {
                if (project.getMailFromName() != null && !project.getMailFromName().isBlank()) {
                    helper.setFrom(new InternetAddress(project.getMailFrom(), project.getMailFromName(), "UTF-8"));
                } else {
                    helper.setFrom(project.getMailFrom());
                }
            }
            helper.setSubject(project.getMailSubject());
            helper.setText(project.getMailBody(), false);
            helper.addAttachment(
                    letterAttachmentName(project),
                    new UrlResource(generatedLetter.pdfPath().toUri())
            );
            for (MailAttachment attachment : attachments) {
                helper.addAttachment(attachment.filename(), attachment.resource());
            }
            mimeMessage.saveChanges();
            String messageId = mimeMessage.getMessageID();
            javaMailSender.send(mimeMessage);
            contactMessageService.recordOutbound(
                    project,
                    contact,
                    messageId,
                    project.getMailSubject(),
                    project.getMailBody(),
                    project.getMailFrom(),
                    contact.getEmail(),
                    OffsetDateTime.now()
            );
            return messageId;
        } catch (MessagingException | IOException ex) {
            throw new IllegalStateException("Failed to send email to " + contact.getEmail(), ex);
        } finally {
            deleteSilently(generatedLetter.docxPath());
            deleteSilently(generatedLetter.pdfPath());
            deleteSilently(generatedLetter.docxPath().getParent());
        }
    }

    private String letterAttachmentName(Project project) {
        if (project.getLetterAttachmentFilename() == null || project.getLetterAttachmentFilename().isBlank()) {
            return "letter.pdf";
        }
        return project.getLetterAttachmentFilename();
    }

    public void verifySmtp(Project project) {
        try {
            createSender(project).testConnection();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to connect to SMTP", ex);
        }
    }

    private JavaMailSenderImpl createSender(Project project) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(SMTP_HOST);
        sender.setPort(SMTP_PORT);
        sender.setUsername(project.getGmailUsername());
        sender.setPassword(project.getGmailAppPassword());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.connectiontimeout", "15000");
        properties.put("mail.smtp.timeout", "15000");
        properties.put("mail.smtp.writetimeout", "15000");
        return sender;
    }

    private void deleteSilently(java.nio.file.Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
