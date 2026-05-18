package com.pdasilem.contactwork.inbox;

import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class InboxSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(InboxSyncScheduler.class);

    private final ProjectService projectService;
    private final InboxSyncService inboxSyncService;

    public InboxSyncScheduler(ProjectService projectService, InboxSyncService inboxSyncService) {
        this.projectService = projectService;
        this.inboxSyncService = inboxSyncService;
    }

    @Scheduled(cron = "${app.mail.inbox-sync-cron}")
    public void scheduledSync() {
        projectService.findAllForSystem().stream()
                .filter(inboxSyncService::isConfigured)
                .forEach(this::syncProject);
    }

    private void syncProject(Project project) {
        try {
            inboxSyncService.syncInboxForSystem(project.getId());
        } catch (Exception ex) {
            log.warn("Scheduled inbox sync failed for project {}", project.getId(), ex);
        }
    }
}
