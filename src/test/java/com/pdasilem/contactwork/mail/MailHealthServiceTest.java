package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.auth.CurrentUserService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pdasilem.contactwork.ai.AiModelCatalogService;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.inbox.InboxSyncService;
import com.pdasilem.contactwork.project.AiProvider;
import com.pdasilem.contactwork.project.MailTransportType;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectRepository;
import com.pdasilem.contactwork.project.ProjectService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MailHealthServiceTest {

    @Test
    void verifyConnectionsChecksSmtpThenInboxForGmailTransport() {
        Project project = gmailProject();
        RecordingProjectService projectService = new RecordingProjectService(project);
        List<String> events = new ArrayList<>();
        RecordingOutboundMailService outboundMailService = new RecordingOutboundMailService(events);
        RecordingInboxSyncService inboxSyncService = new RecordingInboxSyncService(events);
        MailHealthService service = new MailHealthService(
                inboxSyncService,
                outboundMailService,
                projectService,
                appProperties()
        );

        Throwable thrown = catchThrowable(() -> service.verifyConnections(Project.DEFAULT_PROJECT_ID));

        assertThat(thrown).isNull();
        assertThat(events).containsExactly("transport", "imap");
    }

    @Test
    void verifyConnectionsChecksTransportOnlyForBrevoTransport() {
        Project project = brevoProject();
        RecordingProjectService projectService = new RecordingProjectService(project);
        List<String> events = new ArrayList<>();
        RecordingOutboundMailService outboundMailService = new RecordingOutboundMailService(events);
        RecordingInboxSyncService inboxSyncService = new RecordingInboxSyncService(events);
        MailHealthService service = new MailHealthService(
                inboxSyncService,
                outboundMailService,
                projectService,
                appPropertiesWithBrevoKey()
        );

        Throwable thrown = catchThrowable(() -> service.verifyConnections(Project.DEFAULT_PROJECT_ID));

        assertThat(thrown).isNull();
        assertThat(events).containsExactly("transport");
    }

    @Test
    void verifyConnectionsRequiresGmailCredentialsForGmailTransport() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setMailTransport(MailTransportType.GMAIL);
        RecordingProjectService projectService = new RecordingProjectService(project);
        List<String> events = new ArrayList<>();
        MailHealthService service = new MailHealthService(
                new RecordingInboxSyncService(events),
                new RecordingOutboundMailService(events),
                projectService,
                appProperties()
        );

        Throwable thrown = catchThrowable(() -> service.verifyConnections(Project.DEFAULT_PROJECT_ID));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Project Gmail credentials are required before checking mailbox");
        assertThat(events).isEmpty();
    }

    @Test
    void verifyConnectionsRequiresBrevoApiKeyForBrevoTransport() {
        Project project = brevoProject();
        RecordingProjectService projectService = new RecordingProjectService(project);
        List<String> events = new ArrayList<>();
        MailHealthService service = new MailHealthService(
                new RecordingInboxSyncService(events),
                new RecordingOutboundMailService(events),
                projectService,
                appProperties()
        );

        Throwable thrown = catchThrowable(() -> service.verifyConnections(Project.DEFAULT_PROJECT_ID));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("BREVO_API_KEY environment variable is not configured");
        assertThat(events).isEmpty();
    }

    @Test
    void transportFailurePreventsInboxCheck() {
        Project project = gmailProject();
        RecordingProjectService projectService = new RecordingProjectService(project);
        List<String> events = new ArrayList<>();
        RecordingOutboundMailService outboundMailService = new RecordingOutboundMailService(events, "SMTP failed");
        RecordingInboxSyncService inboxSyncService = new RecordingInboxSyncService(events);
        MailHealthService service = new MailHealthService(
                inboxSyncService,
                outboundMailService,
                projectService,
                appProperties()
        );

        Throwable thrown = catchThrowable(() -> service.verifyConnections(Project.DEFAULT_PROJECT_ID));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SMTP failed");
        assertThat(events).containsExactly("transport");
    }

    private static Project gmailProject() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setMailTransport(MailTransportType.GMAIL);
        project.setGmailUsername("user@example.com");
        project.setGmailAppPassword("app-password");
        return project;
    }

    private static Project brevoProject() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setMailTransport(MailTransportType.BREVO);
        return project;
    }

    private static final class RecordingProjectService extends ProjectService {
        private final Project project;

        private RecordingProjectService(Project project) {
            super(projectRepository(), appProperties(), org.mockito.Mockito.mock(CurrentUserService.class));
            this.project = project;
        }

        @Override
        public Project getProject(UUID projectId) {
            return project;
        }
    }

    private static ProjectRepository projectRepository() {
        return (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class<?>[]{ProjectRepository.class},
                (proxy, method, args) -> null
        );
    }

    private static AppProperties appProperties() {
        return new AppProperties(
                new AppProperties.Resources("/tmp/contactwork-test"),
                new AppProperties.Mail(1000, "0 */5 * * * *", null, null),
                null
        );
    }

    private static AppProperties appPropertiesWithBrevoKey() {
        return new AppProperties(
                new AppProperties.Resources("/tmp/contactwork-test"),
                new AppProperties.Mail(1000, "0 */5 * * * *", null, new AppProperties.Brevo("test-brevo-key")),
                null
        );
    }

    private static final class RecordingOutboundMailService extends OutboundMailService {
        private final List<String> events;
        private final String failure;

        private RecordingOutboundMailService(List<String> events) {
            this(events, null);
        }

        private RecordingOutboundMailService(List<String> events, String failure) {
            super(null, null, null, null);
            this.events = events;
            this.failure = failure;
        }

        @Override
        public void verifyTransport(Project project) {
            events.add("transport");
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static final class RecordingInboxSyncService extends InboxSyncService {
        private final List<String> events;

        private RecordingInboxSyncService(List<String> events) {
            super(null, null, null, null, null, null);
            this.events = events;
        }

        @Override
        public void verifyConnections(UUID projectId) {
            events.add("imap");
        }
    }

}
