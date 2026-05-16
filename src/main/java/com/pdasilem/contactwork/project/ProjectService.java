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
        if (updates.getStatus() != null) {
            project.setStatus(updates.getStatus());
        }
        if (updates.getLetterTemplate() != null) {
            project.setLetterTemplate(updates.getLetterTemplate());
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
        project.setMailFrom(updates.getMailFrom());
        project.setMailFromName(updates.getMailFromName());
        if (updates.getSendDelayMs() >= 0) {
            project.setSendDelayMs(updates.getSendDelayMs());
        }
        project.setMaxMessagesPerBatch(updates.getMaxMessagesPerBatch());
        if (updates.getInboxSyncCron() != null) {
            project.setInboxSyncCron(updates.getInboxSyncCron());
        }
        project.setGmailUsername(updates.getGmailUsername());
        project.setGmailAppPassword(updates.getGmailAppPassword());
        return projectRepository.save(project);
    }

    @Transactional
    public Project updateSenderIdentity(
            UUID projectId,
            String mailFrom,
            String mailFromName
    ) {
        Project project = getProject(projectId);
        project.setMailFrom(mailFrom);
        project.setMailFromName(mailFromName);
        return projectRepository.save(project);
    }

    private void applyDefaults(Project project) {
        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.NEW);
        }
        if (project.getSendDelayMs() < 0) {
            project.setSendDelayMs(appProperties.mail().sendDelayMs());
        }
        if (project.getInboxSyncCron() == null) {
            project.setInboxSyncCron(appProperties.mail().inboxSyncCron());
        }
    }

}
