package com.pdasilem.contactwork.mail;

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
import org.springframework.core.io.UrlResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class OutboundMailService {
    private final ContactMessageService contactMessageService;
    private final MailSenderFactory mailSenderFactory;
    private final MailTemplateRenderer mailTemplateRenderer;

    public OutboundMailService(
            ContactMessageService contactMessageService,
            MailSenderFactory mailSenderFactory,
            MailTemplateRenderer mailTemplateRenderer
    ) {
        this.contactMessageService = contactMessageService;
        this.mailSenderFactory = mailSenderFactory;
        this.mailTemplateRenderer = mailTemplateRenderer;
    }

    public String send(Project project, Contact contact, GeneratedLetter generatedLetter, List<MailAttachment> attachments) {
        try {
            String subject = mailTemplateRenderer.render(project.getMailSubject(), project, contact);
            String body = mailTemplateRenderer.render(project.getMailBody(), project, contact);
            JavaMailSender javaMailSender = mailSenderFactory.create(project);
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
            helper.setSubject(subject);
            helper.setText(body, false);
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
                    subject,
                    body,
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
            mailSenderFactory.create(project).testConnection();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to connect to SMTP", ex);
        }
    }

    private void deleteSilently(java.nio.file.Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
        }
    }
}
