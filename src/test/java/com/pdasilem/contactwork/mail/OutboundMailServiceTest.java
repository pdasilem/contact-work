package com.pdasilem.contactwork.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactColumnSource;
import com.pdasilem.contactwork.contact.ContactCustomField;
import com.pdasilem.contactwork.contact.ContactCustomFieldRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.contact.ProjectContactColumn;
import com.pdasilem.contactwork.contact.ProjectContactColumnRepository;
import com.pdasilem.contactwork.history.ContactMessageService;
import com.pdasilem.contactwork.project.MailTransportType;
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

        ContactMessageService contactMessageService = Mockito.mock(ContactMessageService.class);
        OutboundMailService service = new OutboundMailService(
                contactMessageService,
                gmailRouter(),
                mailTemplateRenderer(List.of(), List.of()),
                Mockito.mock(GmailImapService.class)
        );

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
        Project project = gmailProject();
        project.setMailSubject("Attention to {Contact}");
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
        assertThat(receivedMessages[0].getSubject()).isEqualTo("Attention to Receiver");
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
                Mockito.eq("Attention to Receiver"),
                Mockito.eq("Body line"),
                Mockito.eq("sender@localhost"),
                Mockito.eq("receiver@localhost"),
                Mockito.any()
        );
    }

    @Test
    void shouldSendMessageWithSenderDisplayName() throws Exception {
        greenMail.setUser("receiver@localhost", "receiver@localhost", "secret");

        ContactMessageService contactMessageService = Mockito.mock(ContactMessageService.class);
        OutboundMailService service = new OutboundMailService(
                contactMessageService,
                gmailRouter(),
                mailTemplateRenderer(List.of(), List.of()),
                Mockito.mock(GmailImapService.class)
        );

        Path letterDocx = Files.createFile(tempDir.resolve("letter-with-name.docx"));
        Path letterPdf = Files.createFile(tempDir.resolve("letter-with-name.pdf"));
        Files.writeString(letterPdf, "pdf");
        Files.writeString(letterDocx, "docx");

        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        Project project = gmailProject();
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

    @Test
    void shouldRenderCustomPlaceholderInBodyForMessageAndHistory() throws Exception {
        greenMail.setUser("receiver@localhost", "receiver@localhost", "secret");

        ContactMessageService contactMessageService = Mockito.mock(ContactMessageService.class);

        Project project = gmailProject();
        project.setMailSubject("Outbound Test");
        project.setMailBody("Send to { department }");
        project.setMailFrom("sender@localhost");
        project.setLetterAttachmentFilename("letter.pdf");

        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setEmail("receiver@localhost");
        contact.setContactName("Receiver");
        contact.setOrganizationName("Org");
        contact.setStatus(ContactStatus.NEW);

        ProjectContactColumn departmentColumn = new ProjectContactColumn();
        departmentColumn.setColumnKey("department");
        departmentColumn.setDisplayLabel("Department");
        departmentColumn.setSourceType(ContactColumnSource.CUSTOM);
        departmentColumn.setVisible(true);

        ContactCustomField department = new ContactCustomField();
        department.setFieldKey("department");
        department.setFieldValue("Pediatrics");

        OutboundMailService service = new OutboundMailService(
                contactMessageService,
                gmailRouter(),
                mailTemplateRenderer(List.of(departmentColumn), List.of(department)),
                Mockito.mock(GmailImapService.class)
        );

        Path letterDocx = Files.createFile(tempDir.resolve("custom-body.docx"));
        Path letterPdf = Files.createFile(tempDir.resolve("custom-body.pdf"));
        Files.writeString(letterPdf, "pdf");
        Files.writeString(letterDocx, "docx");

        service.send(project, contact, new GeneratedLetter(letterDocx, letterPdf), List.of());

        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertThat(receivedMessages).hasSize(1);
        assertThat(messageText(receivedMessages[0].getContent())).contains("Send to Pediatrics");
        Mockito.verify(contactMessageService).recordOutbound(
                Mockito.eq(project),
                Mockito.eq(contact),
                Mockito.any(),
                Mockito.eq("Outbound Test"),
                Mockito.eq("Send to Pediatrics"),
                Mockito.eq("sender@localhost"),
                Mockito.eq("receiver@localhost"),
                Mockito.any()
        );
    }

    @Test
    void shouldRejectUnknownPlaceholderWithoutSending() throws Exception {
        ContactMessageService contactMessageService = Mockito.mock(ContactMessageService.class);
        OutboundMailService service = new OutboundMailService(
                contactMessageService,
                gmailRouter(),
                mailTemplateRenderer(List.of(), List.of()),
                Mockito.mock(GmailImapService.class)
        );

        Project project = gmailProject();
        project.setMailSubject("Attention to {Departmant}");
        project.setMailBody("Body line");

        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setEmail("receiver@localhost");

        Path letterDocx = Files.createFile(tempDir.resolve("unknown.docx"));
        Path letterPdf = Files.createFile(tempDir.resolve("unknown.pdf"));

        assertThatThrownBy(() -> service.send(project, contact, new GeneratedLetter(letterDocx, letterPdf), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown email template placeholder: {Departmant}");
    }

    @Test
    void shouldRenderBlankKnownFieldAsEmptyString() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());

        String rendered = mailTemplateRenderer(List.of(), List.of()).render("Note: {Note}.", project, contact);

        assertThat(rendered).isEqualTo("Note: .");
    }

    private Project gmailProject() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setName("Default Project");
        project.setMailTransport(MailTransportType.GMAIL);
        return project;
    }

    private MailTransportRouter gmailRouter() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(ServerSetupTest.SMTP.getPort());
        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.smtp.auth", "false");

        GmailSmtpMailTransport gmailTransport = new GmailSmtpMailTransport(project -> sender);
        BrevoApiMailTransport brevoTransport = Mockito.mock(BrevoApiMailTransport.class);
        return new MailTransportRouter(gmailTransport, brevoTransport);
    }

    private MailTemplateRenderer mailTemplateRenderer(
            List<ProjectContactColumn> columns,
            List<ContactCustomField> customFields
    ) {
        ContactCustomFieldRepository contactCustomFieldRepository = Mockito.mock(ContactCustomFieldRepository.class);
        ProjectContactColumnRepository projectContactColumnRepository = Mockito.mock(ProjectContactColumnRepository.class);
        Mockito.when(contactCustomFieldRepository.findByProjectIdAndContactId(Mockito.any(), Mockito.any()))
                .thenReturn(customFields);
        Mockito.when(projectContactColumnRepository.findByProjectIdOrderByColumnOrderAsc(Mockito.any()))
                .thenReturn(columns);
        return new MailTemplateRenderer(contactCustomFieldRepository, projectContactColumnRepository);
    }

    private String messageText(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                text.append(messageText(multipart.getBodyPart(i).getContent()));
            }
            return text.toString();
        }
        return "";
    }
}
