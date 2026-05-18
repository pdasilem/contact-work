package com.pdasilem.contactwork.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.auth.CurrentUserService;
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
        assertThat(project.getAiSystemPrompt()).isNull();
    }

    @Test
    void createAppliesTechnicalDefaultsAndLeavesSetupFieldsEmpty() {
        Project project = new Project();
        project.setName("Empty Project");
        project.setDescription("Draft description");
        when(projectRepository.save(project)).thenReturn(project);

        ProjectService service = service(appProperties());
        Project saved = service.create(project);

        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.NEW);
        assertThat(saved.getSendDelayMs()).isEqualTo(1000);
        assertThat(saved.getInboxSyncCron()).isEqualTo("0 */5 * * * *");
        assertThat(saved.getMailSubject()).isNull();
        assertThat(saved.getMailBody()).isNull();
        assertThat(saved.getLetterTemplate()).isNull();
        assertThat(saved.getLetterAttachmentFilename()).isNull();
        assertThat(saved.getMailFrom()).isNull();
        assertThat(saved.getMailFromName()).isNull();
        assertThat(saved.getGmailUsername()).isNull();
        assertThat(saved.getGmailAppPassword()).isNull();
        assertThat(saved.getAiSystemPrompt()).isEqualTo(ProjectService.DEFAULT_AI_SYSTEM_PROMPT);
    }

    @Test
    void createDoesNotRequireGmailConfigSection() {
        Project project = new Project();
        project.setName("Empty Project");
        when(projectRepository.save(project)).thenReturn(project);

        ProjectService service = service(appPropertiesWithoutGmail());
        Project saved = service.create(project);

        assertThat(saved.getSendDelayMs()).isEqualTo(1000);
        assertThat(saved.getInboxSyncCron()).isEqualTo("0 */5 * * * *");
        assertThat(saved.getGmailUsername()).isNull();
        assertThat(saved.getGmailAppPassword()).isNull();
        assertThat(saved.getAiSystemPrompt()).isEqualTo(ProjectService.DEFAULT_AI_SYSTEM_PROMPT);
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

        ProjectService service = service(appProperties());
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

        ProjectService service = service(appProperties());
        Project updates = new Project();
        updates.setMaxMessagesPerBatch(25);
        Project saved = service.update(project.getId(), updates);

        assertThat(saved.getMaxMessagesPerBatch()).isEqualTo(25);

        Project clear = new Project();
        Project cleared = service.update(project.getId(), clear);

        assertThat(cleared.getMaxMessagesPerBatch()).isNull();
    }

    @Test
    void existingNullAiPromptResolvesToDefault() {
        Project project = new Project();

        ProjectService service = service(appProperties());

        assertThat(service.aiSystemPrompt(project)).isEqualTo(ProjectService.DEFAULT_AI_SYSTEM_PROMPT);
    }

    @Test
    void aiPromptCanBeSavedAndReset() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setName("Project");
        project.setStatus(ProjectStatus.NEW);
        project.setSendDelayMs(1000);
        project.setInboxSyncCron("0 */5 * * * *");

        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        ProjectService service = service(appProperties());
        Project saved = service.updateAiSystemPrompt(project.getId(), " Custom prompt ");

        assertThat(saved.getAiSystemPrompt()).isEqualTo("Custom prompt");

        Project reset = service.resetAiSystemPrompt(project.getId());

        assertThat(reset.getAiSystemPrompt()).isEqualTo(ProjectService.DEFAULT_AI_SYSTEM_PROMPT);
    }

    private AppProperties appProperties() {
        return new AppProperties(
                new AppProperties.Resources("/tmp/contactwork-test"),
                new AppProperties.Mail(1000, "0 */5 * * * *", gmailProperties()),
                null
        );
    }

    private AppProperties appPropertiesWithoutGmail() {
        return new AppProperties(
                new AppProperties.Resources("/tmp/contactwork-test"),
                new AppProperties.Mail(1000, "0 */5 * * * *", null),
                null
        );
    }

    private AppProperties.Gmail gmailProperties() {
        return new AppProperties.Gmail(
                "INBOX",
                "[Gmail]/Sent Mail",
                "[Gmail]/Spam",
                null
        );
    }

    private ProjectService service(AppProperties appProperties) {
        return new ProjectService(projectRepository, appProperties, mock(CurrentUserService.class));
    }
}
