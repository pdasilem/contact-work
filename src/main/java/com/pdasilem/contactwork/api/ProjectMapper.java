package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.project.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public Project toEntity(ProjectRequest request) {
        Project project = new Project();
        apply(project, request);
        return project;
    }

    public void apply(Project project, ProjectRequest request) {
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStatus(request.status());
        project.setMailTransport(request.mailTransport());
        project.setLetterTemplate(request.letterTemplate());
        project.setMailSubject(request.mailSubject());
        project.setMailBody(request.mailBody());
        project.setLetterAttachmentFilename(request.letterAttachmentFilename());
        project.setMailFrom(request.mailFrom());
        project.setMailFromName(request.mailFromName());
        if (request.sendDelayMs() != null) {
            project.setSendDelayMs(request.sendDelayMs());
        } else {
            project.setSendDelayMs(-1);
        }
        project.setMaxMessagesPerBatch(request.maxMessagesPerBatch());
        project.setInboxSyncCron(request.inboxSyncCron());
        project.setGmailUsername(request.gmailUsername());
        project.setGmailAppPassword(request.gmailAppPassword());
    }

    public ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getMailTransport(),
                project.getLetterTemplate(),
                project.getMailSubject(),
                project.getMailBody(),
                project.getLetterAttachmentFilename(),
                project.getMailFrom(),
                project.getMailFromName(),
                project.getSendDelayMs(),
                project.getMaxMessagesPerBatch(),
                project.getInboxSyncCron(),
                project.getGmailUsername(),
                project.getGmailAppPassword() != null && !project.getGmailAppPassword().isBlank(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
