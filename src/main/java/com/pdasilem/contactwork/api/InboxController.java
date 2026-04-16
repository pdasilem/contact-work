package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.inbox.InboxSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inbox")
public class InboxController {

    private final InboxSyncService inboxSyncService;

    public InboxController(InboxSyncService inboxSyncService) {
        this.inboxSyncService = inboxSyncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> sync() {
        inboxSyncService.syncInbox();
        return ResponseEntity.accepted().build();
    }
}
