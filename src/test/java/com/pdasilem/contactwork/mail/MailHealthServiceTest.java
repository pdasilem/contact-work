package com.pdasilem.contactwork.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pdasilem.contactwork.ai.AiModelCatalogService;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.inbox.InboxSyncService;
import com.pdasilem.contactwork.project.AiProvider;
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
    void verifyConnectionsChecksSmtpThenInboxWithoutSyncingDefaultAlias() {
        RecordingProjectService projectService = new RecordingProjectService(configuredProject());
        List<String> events = new ArrayList<>();
        RecordingOutboundMailService outboundMailService = new RecordingOutboundMailService(events);
        RecordingInboxSyncService inboxSyncService = new RecordingInboxSyncService(events);
        MailHealthService service = new MailHealthService(
                inboxSyncService,
                outboundMailService,
                projectService
        );

        Throwable thrown = catchThrowable(() -> service.verifyConnections(Project.DEFAULT_PROJECT_ID));

        assertThat(thrown).isNull();
        assertThat(events).containsExactly("smtp", "imap");
    }

    @Test
    void verifyConnectionsRequiresProjectGmailCredentials() {
        RecordingProjectService projectService = new RecordingProjectService(new Project());
        List<String> events = new ArrayList<>();
        RecordingOutboundMailService outboundMailService = new RecordingOutboundMailService(events);
        RecordingInboxSyncService inboxSyncService = new RecordingInboxSyncService(events);
        MailHealthService service = new MailHealthService(
                inboxSyncService,
                outboundMailService,
                projectService
        );

        Throwable thrown = catchThrowable(() -> service.verifyConnections(Project.DEFAULT_PROJECT_ID));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Project Gmail credentials are required before checking mailbox");
        assertThat(events).isEmpty();
    }

    @Test
    void smtpFailurePreventsInboxCheckAndAliasSync() {
        RecordingProjectService projectService = new RecordingProjectService(configuredProject());
        List<String> events = new ArrayList<>();
        RecordingOutboundMailService outboundMailService = new RecordingOutboundMailService(events, "SMTP failed");
        RecordingInboxSyncService inboxSyncService = new RecordingInboxSyncService(events);
        MailHealthService service = new MailHealthService(
                inboxSyncService,
                outboundMailService,
                projectService
        );

        Throwable thrown = catchThrowable(() -> service.verifyConnections(Project.DEFAULT_PROJECT_ID));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SMTP failed");
        assertThat(events).containsExactly("smtp");
    }

    private static Project configuredProject() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setGmailUsername("user@example.com");
        project.setGmailAppPassword("app-password");
        return project;
    }

    private static final class RecordingProjectService extends ProjectService {
        private final Project project;

        private RecordingProjectService(Project project) {
            super(projectRepository(), appProperties());
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
                new AppProperties.Mail(1000, "0 */5 * * * *", null),
                null
        );
    }

    private static final class StubModelCatalog extends AiModelCatalogService {
        private StubModelCatalog() {
            super(null, null);
        }

        @Override
        public List<String> requiredModelsFor(AiProvider provider) {
            return List.of();
        }
    }

    private static final class RecordingOutboundMailService extends OutboundMailService {
        private final List<String> events;
        private final String failure;

        private RecordingOutboundMailService(List<String> events) {
            this(events, null);
        }

        private RecordingOutboundMailService(List<String> events, String failure) {
            super(null, null);
            this.events = events;
            this.failure = failure;
        }

        @Override
        public void verifySmtp(Project project) {
            events.add("smtp");
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static final class RecordingInboxSyncService extends InboxSyncService {
        private final List<String> events;

        private RecordingInboxSyncService(List<String> events) {
            super(null, null, null, null);
            this.events = events;
        }

        @Override
        public void verifyConnections(UUID projectId) {
            events.add("imap");
        }
    }

}
