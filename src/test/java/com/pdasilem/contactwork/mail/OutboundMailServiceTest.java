package com.pdasilem.contactwork.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.history.ContactMessageService;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.asset.MailAttachment;
import com.pdasilem.contactwork.template.GeneratedLetter;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.core.io.PathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class OutboundMailServiceTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @TempDir
    Path tempDir;

    @Test
    void shouldSendMessageWithTwoPdfAttachments() throws Exception {
        greenMail.setUser("receiver@localhost", "receiver@localhost", "secret");

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(ServerSetupTest.SMTP.getPort());
        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.smtp.auth", "false");

        AppProperties properties = new AppProperties(
                new AppProperties.Resources("classpath:data/Letter.docx", tempDir.toString()),
                new AppProperties.Mail(
                        "Outbound Test",
                        "Body line",
                        "letter.pdf",
                        "sender@localhost",
                        0,
                        "0 */5 * * * *",
                        new AppProperties.Gmail("", "")
                )
        );

        ContactMessageService contactMessageService = Mockito.mock(ContactMessageService.class);
        OutboundMailService service = new OutboundMailService(sender, properties, contactMessageService);

        Path letterDocx = Files.createFile(tempDir.resolve("letter.docx"));
        Path letterPdf = Files.createFile(tempDir.resolve("letter.pdf"));
        Files.writeString(letterPdf, "pdf");
        Files.writeString(letterDocx, "docx");
        Path attachment = Files.createFile(tempDir.resolve("Pitch_deck_en.pdf"));
        Files.writeString(attachment, "pitch");

        Contact contact = new Contact() {
            @Override
            public Project getProject() {
                throw new AssertionError("outbound send must not read Contact.project");
            }
        };
        contact.setId(UUID.randomUUID());
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setName("Default Project");
        project.setMailSubject("Outbound Test");
        project.setMailBody("Body line");
        project.setMailFrom("sender@localhost");
        project.setLetterAttachmentFilename("letter.pdf");
        contact.setEmail("receiver@localhost");
        contact.setContactName("Receiver");
        contact.setOrganizationName("Org");
        contact.setStatus(ContactStatus.NEW);

        service.send(project, contact, new GeneratedLetter(letterDocx, letterPdf),
                List.of(new MailAttachment("Pitch_deck_en.pdf", new PathResource(attachment))));

        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertThat(receivedMessages).hasSize(1);
        assertThat(receivedMessages[0].getSubject()).isEqualTo("Outbound Test");
        InternetAddress from = (InternetAddress) receivedMessages[0].getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("sender@localhost");
        assertThat(from.getPersonal()).isNull();

        Multipart multipart = (Multipart) receivedMessages[0].getContent();
        assertThat(multipart.getCount()).isEqualTo(3);
        BodyPart attachmentOne = multipart.getBodyPart(1);
        BodyPart attachmentTwo = multipart.getBodyPart(2);
        assertThat(attachmentOne.getFileName()).isEqualTo("letter.pdf");
        assertThat(attachmentTwo.getFileName()).isEqualTo("Pitch_deck_en.pdf");
        Mockito.verify(contactMessageService).recordOutbound(
                Mockito.eq(project),
                Mockito.eq(contact),
                Mockito.any(),
                Mockito.eq("Outbound Test"),
                Mockito.eq("Body line"),
                Mockito.eq("sender@localhost"),
                Mockito.eq("receiver@localhost"),
                Mockito.any()
        );
    }

    @Test
    void shouldSendMessageWithSenderDisplayName() throws Exception {
        greenMail.setUser("receiver@localhost", "receiver@localhost", "secret");

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(ServerSetupTest.SMTP.getPort());
        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.smtp.auth", "false");

        AppProperties properties = new AppProperties(
                new AppProperties.Resources("classpath:data/Letter.docx", tempDir.toString()),
                new AppProperties.Mail(
                        "Outbound Test",
                        "Body line",
                        "letter.pdf",
                        "sender@localhost",
                        0,
                        "0 */5 * * * *",
                        new AppProperties.Gmail("", "")
                )
        );

        ContactMessageService contactMessageService = Mockito.mock(ContactMessageService.class);
        OutboundMailService service = new OutboundMailService(sender, properties, contactMessageService);

        Path letterDocx = Files.createFile(tempDir.resolve("letter-with-name.docx"));
        Path letterPdf = Files.createFile(tempDir.resolve("letter-with-name.pdf"));
        Files.writeString(letterPdf, "pdf");
        Files.writeString(letterDocx, "docx");

        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setName("Default Project");
        project.setMailSubject("Outbound Test");
        project.setMailBody("Body line");
        project.setMailFrom("contact@shviltashvilebi.ge");
        project.setMailFromName("Shviltashvilebi Ltd");
        project.setLetterAttachmentFilename("letter.pdf");
        contact.setEmail("receiver@localhost");
        contact.setContactName("Receiver");
        contact.setOrganizationName("Org");
        contact.setStatus(ContactStatus.NEW);

        service.send(project, contact, new GeneratedLetter(letterDocx, letterPdf), List.of());

        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertThat(receivedMessages).hasSize(1);
        InternetAddress from = (InternetAddress) receivedMessages[0].getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("contact@shviltashvilebi.ge");
        assertThat(from.getPersonal()).isEqualTo("Shviltashvilebi Ltd");
        Mockito.verify(contactMessageService).recordOutbound(
                Mockito.eq(project),
                Mockito.eq(contact),
                Mockito.any(),
                Mockito.eq("Outbound Test"),
                Mockito.eq("Body line"),
                Mockito.eq("contact@shviltashvilebi.ge"),
                Mockito.eq("receiver@localhost"),
                Mockito.any()
        );
    }
}
