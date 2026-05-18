package com.pdasilem.contactwork.inbox;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboxSyncSchedulerTest {

    @Mock
    private ProjectService projectService;

    @Mock
    private InboxSyncService inboxSyncService;

    @Test
    void scheduledSyncDelegatesConfiguredProjectsToInboxSyncService() {
        Project configured = project(UUID.randomUUID());
        Project unconfigured = project(UUID.randomUUID());
        InboxSyncScheduler scheduler = new InboxSyncScheduler(projectService, inboxSyncService);
        when(projectService.findAllForSystem()).thenReturn(List.of(configured, unconfigured));
        when(inboxSyncService.isConfigured(configured)).thenReturn(true);
        when(inboxSyncService.isConfigured(unconfigured)).thenReturn(false);

        scheduler.scheduledSync();

        verify(inboxSyncService).syncInboxForSystem(configured.getId());
        verify(inboxSyncService, never()).syncInboxForSystem(unconfigured.getId());
    }

    private Project project(UUID id) {
        Project project = new Project();
        project.setId(id);
        project.setName("Project");
        return project;
    }
}
