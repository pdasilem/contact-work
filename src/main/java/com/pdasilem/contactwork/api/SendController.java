package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.mail.SendCoordinator;
import com.pdasilem.contactwork.contact.ContactLookupService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SendController {

    private final SendCoordinator sendCoordinator;
    private final ContactLookupService contactLookupService;

    public SendController(SendCoordinator sendCoordinator, ContactLookupService contactLookupService) {
        this.sendCoordinator = sendCoordinator;
        this.contactLookupService = contactLookupService;
    }

    @PostMapping("/projects/{projectId}/send/start")
    public ResponseEntity<Void> startProjectSend(@PathVariable UUID projectId) {
        sendCoordinator.start(projectId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/projects/{projectId}/send/status")
    public SendStatusResponse getProjectStatus(@PathVariable UUID projectId) {
        return sendCoordinator.getStatus(projectId);
    }

    @PostMapping("/projects/{projectId}/send/contact/{selector}")
    public ResponseEntity<Void> sendProjectSingle(@PathVariable UUID projectId, @PathVariable String selector) {
        sendCoordinator.sendSingle(projectId, contactLookupService.findBySelector(projectId, selector).getId());
        return ResponseEntity.accepted().build();
    }
}
