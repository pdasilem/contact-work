package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.history.ContactMessageService;
import com.pdasilem.contactwork.template.GeneratedLetter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class OutboundMailService {

    private final JavaMailSender javaMailSender;
    private final AppProperties appProperties;
    private final ContactMessageService contactMessageService;

    public OutboundMailService(
            JavaMailSender javaMailSender,
            AppProperties appProperties,
            ContactMessageService contactMessageService
    ) {
        this.javaMailSender = javaMailSender;
        this.appProperties = appProperties;
        this.contactMessageService = contactMessageService;
    }

    public String send(Contact contact, GeneratedLetter generatedLetter, Resource pitchDeck) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(contact.getEmail());
            if (appProperties.mail().from() != null && !appProperties.mail().from().isBlank()) {
                helper.setFrom(appProperties.mail().from());
            }
            helper.setSubject(appProperties.mail().subject());
            helper.setText(appProperties.mail().body(), false);
            helper.addAttachment(
                    appProperties.mail().letterAttachmentFilename(),
                    new UrlResource(generatedLetter.pdfPath().toUri())
            );
            helper.addAttachment(appProperties.mail().pitchDeckAttachmentFilename(), pitchDeck);
            mimeMessage.saveChanges();
            String messageId = mimeMessage.getMessageID();
            javaMailSender.send(mimeMessage);
            contactMessageService.recordOutbound(
                    contact,
                    messageId,
                    appProperties.mail().subject(),
                    appProperties.mail().body(),
                    appProperties.mail().from(),
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
