package com.pdasilem.contactwork.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.mail.GmailImapService;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.conversation.MailboxMessageRepository;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InboxSyncServiceTest {

    @Test
    void missingProjectGmailCredentialsBlockSyncWithoutAppFallback() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        ProjectService projectService = mock(ProjectService.class);
        when(projectService.getProject(Project.DEFAULT_PROJECT_ID)).thenReturn(project);
        InboxSyncService service = service(projectService);

        Throwable thrown = catchThrowable(() -> service.syncInbox(Project.DEFAULT_PROJECT_ID));

        assertThat(service.isConfigured(project)).isFalse();
        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Project Gmail credentials are required before syncing");
    }

    @Test
    void projectSyncMatchesContactsBySenderRecipientAndCcEmail() {
        ContactRepository contactRepository = mock(ContactRepository.class);
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        Contact sender = contact(project, "sender@example.com");
        Contact recipient = contact(project, "recipient@example.com");
        Contact cc = contact(project, "cc@example.com");
        when(contactRepository.findByProjectIdAndDeletedAtIsNull(project.getId()))
                .thenReturn(List.of(sender, recipient, cc));
        InboxSyncService service = service(mock(ProjectService.class), contactRepository, mock(MailboxMessageRepository.class));
        Map<String, Contact> contactsByEmail = service.contactsByEmail(project.getId(), null);

        List<Contact> matches = service.matchedContacts(
                new InboxSyncService.AddressSet("sender@example.com",
                        List.of("recipient@example.com"),
                        List.of("cc@example.com")),
                contactsByEmail
        );

        assertThat(matches).containsExactly(sender, recipient, cc);
    }

    @Test
    void contactScopedSyncMatchesOnlySelectedContactEmail() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        Contact selected = contact(project, "selected@example.com");
        Contact other = contact(project, "other@example.com");
        InboxSyncService service = service(mock(ProjectService.class), mock(ContactRepository.class), mock(MailboxMessageRepository.class));
        Map<String, Contact> contactsByEmail = service.contactsByEmail(project.getId(), selected);

        assertThat(service.matchedContacts(
                new InboxSyncService.AddressSet("other@example.com",
                        List.of("selected@example.com"),
                        List.of()),
                contactsByEmail
        )).containsExactly(selected);
        assertThat(service.matchedContacts(
                new InboxSyncService.AddressSet("other@example.com", List.of(), List.of()),
                contactsByEmail
        )).isEmpty();
    }

    @Test
    void bounceMatchingUsesReferencesRelatedOutboundMessageId() throws Exception {
        InboxSyncService service = service(mock(ProjectService.class), mock(ContactRepository.class), mock(MailboxMessageRepository.class));
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setHeader("References", "<outbound-message-id>");

        Optional<String> relatedMessageId = ReflectionTestUtils.invokeMethod(service, "extractRelatedMessageId", message);

        assertThat(relatedMessageId).contains("<outbound-message-id>");
    }

    private InboxSyncService service(ProjectService projectService) {
        return service(projectService, mock(ContactRepository.class), mock(MailboxMessageRepository.class));
    }

    private InboxSyncService service(
            ProjectService projectService,
            ContactRepository contactRepository,
            MailboxMessageRepository mailboxMessageRepository
    ) {
        AppProperties appProperties = new AppProperties(
                new AppProperties.Resources("/tmp"),
                new AppProperties.Mail(0, "0 */5 * * * *", null, null),
                null
        );
        return new InboxSyncService(
                contactRepository,
                mailboxMessageRepository,
                projectService,
                appProperties,
                new GmailImapService(appProperties)
        );
    }

    private Contact contact(Project project, String email) {
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setProject(project);
        contact.setEmail(email);
        return contact;
    }
}
