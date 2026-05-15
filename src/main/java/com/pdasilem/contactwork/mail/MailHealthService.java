package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.inbox.InboxSyncService;
import com.pdasilem.contactwork.project.ProjectService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MailHealthService {

    private final InboxSyncService inboxSyncService;
    private final OutboundMailService outboundMailService;
    private final ProjectService projectService;

    public MailHealthService(
            InboxSyncService inboxSyncService,
            OutboundMailService outboundMailService,
            ProjectService projectService
    ) {
        this.inboxSyncService = inboxSyncService;
        this.outboundMailService = outboundMailService;
        this.projectService = projectService;
    }

    public void verifyConnections(UUID projectId) {
        var project = projectService.getProject(projectId);
        outboundMailService.verifySmtp(project);
        inboxSyncService.verifyConnections(projectId);
    }
}
