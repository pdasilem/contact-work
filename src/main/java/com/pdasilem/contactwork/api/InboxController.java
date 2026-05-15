package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.inbox.InboxSyncService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InboxController {

    private final InboxSyncService inboxSyncService;

    public InboxController(InboxSyncService inboxSyncService) {
        this.inboxSyncService = inboxSyncService;
    }

    @PostMapping("/projects/{projectId}/inbox/sync")
    public ResponseEntity<Void> syncProject(@PathVariable UUID projectId) {
        inboxSyncService.syncInbox(projectId);
        return ResponseEntity.accepted().build();
    }
}
