package com.pdasilem.contactwork.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.conversation.MailboxMessageRepository;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import org.junit.jupiter.api.Test;

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

    private InboxSyncService service(ProjectService projectService) {
        return new InboxSyncService(
                mock(ContactRepository.class),
                mock(MailboxMessageRepository.class),
                projectService,
                new AppProperties(
                        new AppProperties.Resources("/tmp"),
                        new AppProperties.Mail(0, "0 */5 * * * *", null),
                        null
                )
        );
    }
}
