package com.pdasilem.contactwork.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.history.ContactMessageService;
import com.pdasilem.contactwork.template.GeneratedLetter;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Files;
import java.nio.file.Path;
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
                new AppProperties.Resources("classpath:data/Letter.docx", "classpath:data/Pitch_deck_en.pdf", tempDir.toString()),
                new AppProperties.Mail(
                        "Outbound Test",
                        "Body line",
                        "letter.pdf",
                        "Pitch_deck_en.pdf",
                        "sender@localhost",
                        0,
                        "0 */5 * * * *",
                        new AppProperties.Gmail("", "")
                )
        );

        OutboundMailService service = new OutboundMailService(sender, properties, Mockito.mock(ContactMessageService.class));

        Path letterDocx = Files.createFile(tempDir.resolve("letter.docx"));
        Path letterPdf = Files.createFile(tempDir.resolve("letter.pdf"));
        Files.writeString(letterPdf, "pdf");
        Files.writeString(letterDocx, "docx");
        Path pitchDeck = Files.createFile(tempDir.resolve("Pitch_deck_en.pdf"));
        Files.writeString(pitchDeck, "pitch");

        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setEmail("receiver@localhost");
        contact.setContactName("Receiver");
        contact.setOrganizationName("Org");
        contact.setStatus(ContactStatus.NEW);

        service.send(contact, new GeneratedLetter(letterDocx, letterPdf), new PathResource(pitchDeck));

        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertThat(receivedMessages).hasSize(1);
        assertThat(receivedMessages[0].getSubject()).isEqualTo("Outbound Test");

        Multipart multipart = (Multipart) receivedMessages[0].getContent();
        assertThat(multipart.getCount()).isEqualTo(3);
        BodyPart attachmentOne = multipart.getBodyPart(1);
        BodyPart attachmentTwo = multipart.getBodyPart(2);
        assertThat(attachmentOne.getFileName()).isEqualTo("letter.pdf");
        assertThat(attachmentTwo.getFileName()).isEqualTo("Pitch_deck_en.pdf");
    }
}
