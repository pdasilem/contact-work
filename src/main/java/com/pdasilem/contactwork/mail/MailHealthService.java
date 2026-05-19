package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.inbox.InboxSyncService;
import com.pdasilem.contactwork.project.MailTransportType;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MailHealthService {

    private final InboxSyncService inboxSyncService;
    private final OutboundMailService outboundMailService;
    private final ProjectService projectService;
    private final AppProperties appProperties;

    public MailHealthService(
            InboxSyncService inboxSyncService,
            OutboundMailService outboundMailService,
            ProjectService projectService,
            AppProperties appProperties
    ) {
        this.inboxSyncService = inboxSyncService;
        this.outboundMailService = outboundMailService;
        this.projectService = projectService;
        this.appProperties = appProperties;
    }

    public void verifyConnections(UUID projectId) {
        Project project = projectService.getProject(projectId);
        requireTransportCredentials(project);
        outboundMailService.verifyTransport(project);
        if (project.getMailTransport() == MailTransportType.GMAIL) {
            inboxSyncService.verifyConnections(projectId);
        }
    }

    private void requireTransportCredentials(Project project) {
        if (project.getMailTransport() == MailTransportType.GMAIL) {
            if (project.getGmailUsername() == null || project.getGmailUsername().isBlank()
                    || project.getGmailAppPassword() == null || project.getGmailAppPassword().isBlank()) {
                throw new IllegalStateException("Project Gmail credentials are required before checking mailbox");
            }
        } else {
            String brevoKey = appProperties.mail().brevo().apiKey();
            if (brevoKey == null || brevoKey.isBlank()) {
                throw new IllegalStateException("BREVO_API_KEY environment variable is not configured");
            }
        }
    }
}
