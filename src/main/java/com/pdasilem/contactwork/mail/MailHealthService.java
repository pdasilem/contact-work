package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.inbox.InboxSyncService;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MailHealthService {

    private final InboxSyncService inboxSyncService;
    private final OutboundMailService outboundMailService;
    private final GmailAliasService gmailAliasService;
    private final ProjectService projectService;

    public MailHealthService(
            InboxSyncService inboxSyncService,
            OutboundMailService outboundMailService,
            GmailAliasService gmailAliasService,
            ProjectService projectService
    ) {
        this.inboxSyncService = inboxSyncService;
        this.outboundMailService = outboundMailService;
        this.gmailAliasService = gmailAliasService;
        this.projectService = projectService;
    }

    public void verifyConnections(UUID projectId) {
        var project = projectService.getProject(projectId);
        outboundMailService.verifySmtp(project);
        inboxSyncService.verifyConnections(projectId);
    }

    public Project verifyConnectionsAndSyncAlias(UUID projectId) {
        verifyConnections(projectId);
        gmailAliasService.syncDefaultAlias(projectId);
        return projectService.getProject(projectId);
    }
}
