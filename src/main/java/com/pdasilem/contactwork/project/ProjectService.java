package com.pdasilem.contactwork.project;

import com.pdasilem.contactwork.config.AppProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AppProperties appProperties;

    public ProjectService(ProjectRepository projectRepository, AppProperties appProperties) {
        this.projectRepository = projectRepository;
        this.appProperties = appProperties;
    }

    public List<Project> findAll() {
        return projectRepository.findAllByOrderByCreatedAtAsc();
    }

    public Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    @Transactional
    public Project create(Project project) {
        applyDefaults(project);
        return projectRepository.save(project);
    }

    @Transactional
    public Project update(UUID projectId, Project updates) {
        Project project = getProject(projectId);
        if (updates.getName() != null) {
            project.setName(updates.getName());
        }
        project.setDescription(updates.getDescription());
        if (updates.getStatus() != null) {
            project.setStatus(updates.getStatus());
        }
        if (updates.getLetterTemplate() != null) {
            project.setLetterTemplate(updates.getLetterTemplate());
        }
        if (updates.getPitchDeck() != null) {
            project.setPitchDeck(updates.getPitchDeck());
        }
        if (updates.getMailSubject() != null) {
            project.setMailSubject(updates.getMailSubject());
        }
        if (updates.getMailBody() != null) {
            project.setMailBody(updates.getMailBody());
        }
        if (updates.getLetterAttachmentFilename() != null) {
            project.setLetterAttachmentFilename(updates.getLetterAttachmentFilename());
        }
        if (updates.getPitchDeckAttachmentFilename() != null) {
            project.setPitchDeckAttachmentFilename(updates.getPitchDeckAttachmentFilename());
        }
        project.setMailFrom(updates.getMailFrom());
        if (updates.getSendDelayMs() >= 0) {
            project.setSendDelayMs(updates.getSendDelayMs());
        }
        if (updates.getInboxSyncCron() != null) {
            project.setInboxSyncCron(updates.getInboxSyncCron());
        }
        project.setGmailUsername(updates.getGmailUsername());
        project.setGmailAppPassword(updates.getGmailAppPassword());
        return projectRepository.save(project);
    }

    private void applyDefaults(Project project) {
        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.ACTIVE);
        }
        if (project.getLetterTemplate() == null) {
            project.setLetterTemplate(appProperties.resources().letterTemplate());
        }
        if (project.getPitchDeck() == null) {
            project.setPitchDeck(appProperties.resources().pitchDeck());
        }
        if (project.getMailSubject() == null) {
            project.setMailSubject(appProperties.mail().subject());
        }
        if (project.getMailBody() == null) {
            project.setMailBody(appProperties.mail().body());
        }
        if (project.getLetterAttachmentFilename() == null) {
            project.setLetterAttachmentFilename(appProperties.mail().letterAttachmentFilename());
        }
        if (project.getPitchDeckAttachmentFilename() == null) {
            project.setPitchDeckAttachmentFilename(appProperties.mail().pitchDeckAttachmentFilename());
        }
        if (project.getMailFrom() == null) {
            project.setMailFrom(appProperties.mail().from());
        }
        if (project.getSendDelayMs() < 0) {
            project.setSendDelayMs(appProperties.mail().sendDelayMs());
        }
        if (project.getInboxSyncCron() == null) {
            project.setInboxSyncCron(appProperties.mail().inboxSyncCron());
        }
        if (project.getGmailUsername() == null) {
            project.setGmailUsername(appProperties.mail().gmail().username());
        }
        if (project.getGmailAppPassword() == null) {
            project.setGmailAppPassword(appProperties.mail().gmail().appPassword());
        }
    }

}
