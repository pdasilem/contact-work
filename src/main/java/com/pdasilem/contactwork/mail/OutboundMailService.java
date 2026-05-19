package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.history.ContactMessageService;
import com.pdasilem.contactwork.project.MailTransportType;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.asset.MailAttachment;
import com.pdasilem.contactwork.template.GeneratedLetter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OutboundMailService {
    private final ContactMessageService contactMessageService;
    private final MailTransportRouter mailTransportRouter;
    private final MailTemplateRenderer mailTemplateRenderer;
    private final GmailImapService gmailImapService;

    public OutboundMailService(
            ContactMessageService contactMessageService,
            MailTransportRouter mailTransportRouter,
            MailTemplateRenderer mailTemplateRenderer,
            GmailImapService gmailImapService
    ) {
        this.contactMessageService = contactMessageService;
        this.mailTransportRouter = mailTransportRouter;
        this.mailTemplateRenderer = mailTemplateRenderer;
        this.gmailImapService = gmailImapService;
    }

    public String send(Project project, Contact contact, GeneratedLetter generatedLetter, List<MailAttachment> attachments) {
        try {
            String subject = mailTemplateRenderer.render(project.getMailSubject(), project, contact);
            String body = mailTemplateRenderer.render(project.getMailBody(), project, contact);

            List<MailFileAttachment> fileAttachments = new ArrayList<>();
            fileAttachments.add(new MailFileAttachment(
                    letterAttachmentName(project),
                    Files.readAllBytes(generatedLetter.pdfPath())
            ));
            for (MailAttachment attachment : attachments) {
                fileAttachments.add(new MailFileAttachment(
                        attachment.filename(),
                        attachment.resource().getInputStream().readAllBytes()
                ));
            }

            MailEnvelope envelope = new MailEnvelope(
                    project.getMailFrom(),
                    project.getMailFromName(),
                    contact.getEmail(),
                    subject,
                    body,
                    fileAttachments
            );

            MailSendResult result = mailTransportRouter.resolve(project).send(project, envelope);

            if (project.getMailTransport() == MailTransportType.BREVO) {
                gmailImapService.appendToSentFolder(project, envelope, result.messageId());
            }

            contactMessageService.recordOutbound(
                    project,
                    contact,
                    result.messageId(),
                    subject,
                    body,
                    project.getMailFrom(),
                    contact.getEmail(),
                    OffsetDateTime.now()
            );
            return result.messageId();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read attachment for " + contact.getEmail(), ex);
        } finally {
            deleteSilently(generatedLetter.docxPath());
            deleteSilently(generatedLetter.pdfPath());
            deleteSilently(generatedLetter.docxPath().getParent());
        }
    }

    public void verifyTransport(Project project) {
        mailTransportRouter.resolve(project).verifyConnection(project);
    }

    private String letterAttachmentName(Project project) {
        if (project.getLetterAttachmentFilename() == null || project.getLetterAttachmentFilename().isBlank()) {
            return "letter.pdf";
        }
        return project.getLetterAttachmentFilename();
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
