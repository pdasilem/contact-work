package com.pdasilem.contactwork.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.config.AppProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectTest {

    @Mock
    private ProjectRepository projectRepository;

    @Test
    void newProjectsDefaultToNewStatusAndEmptyCampaignContent() {
        Project project = new Project();
        project.setName("Empty Project");

        project.onCreate();

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.NEW);
        assertThat(project.getLetterTemplate()).isNull();
        assertThat(project.getMailSubject()).isNull();
        assertThat(project.getMailBody()).isNull();
        assertThat(project.getLetterAttachmentFilename()).isNull();
        assertThat(project.getMaxMessagesPerBatch()).isNull();
    }

    @Test
    void createDoesNotApplyDefaultGmailCredentials() {
        Project project = new Project();
        project.setName("Empty Project");
        when(projectRepository.save(project)).thenReturn(project);

        ProjectService service = new ProjectService(projectRepository, appProperties());
        Project saved = service.create(project);

        assertThat(saved.getGmailUsername()).isNull();
        assertThat(saved.getGmailAppPassword()).isNull();
    }

    @Test
    void updatesDoNotChangeImmutableNameOrDescription() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setName("Original Project");
        project.setDescription("Original description");
        project.setStatus(ProjectStatus.NEW);
        project.setSendDelayMs(1000);
        project.setInboxSyncCron("0 */5 * * * *");

        Project updates = new Project();
        updates.setName("Renamed Project");
        updates.setDescription("Changed description");
        updates.setMailSubject("New subject");
        updates.setMailBody("New body");

        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        ProjectService service = new ProjectService(projectRepository, appProperties());
        Project saved = service.update(project.getId(), updates);

        assertThat(saved.getName()).isEqualTo("Original Project");
        assertThat(saved.getDescription()).isEqualTo("Original description");
        assertThat(saved.getMailSubject()).isEqualTo("New subject");
        assertThat(saved.getMailBody()).isEqualTo("New body");
    }

    @Test
    void updateCanSetAndClearMaxMessagesPerBatch() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setName("Project");
        project.setStatus(ProjectStatus.NEW);
        project.setSendDelayMs(1000);
        project.setInboxSyncCron("0 */5 * * * *");

        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        ProjectService service = new ProjectService(projectRepository, appProperties());
        Project updates = new Project();
        updates.setMaxMessagesPerBatch(25);
        Project saved = service.update(project.getId(), updates);

        assertThat(saved.getMaxMessagesPerBatch()).isEqualTo(25);

        Project clear = new Project();
        Project cleared = service.update(project.getId(), clear);

        assertThat(cleared.getMaxMessagesPerBatch()).isNull();
    }

    private AppProperties appProperties() {
        return new AppProperties(
                new AppProperties.Resources("classpath:data/Letter.docx", "/tmp/contactwork-test"),
                new AppProperties.Mail(
                        "Subject",
                        "Body",
                        "letter.pdf",
                        "sender@example.com",
                        1000,
                        "0 */5 * * * *",
                        new AppProperties.Gmail("user@example.com", "password")
                )
        );
    }
}
