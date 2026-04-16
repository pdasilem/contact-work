package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.mail.SendCoordinator;
import com.pdasilem.contactwork.contact.ContactLookupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/send")
public class SendController {

    private final SendCoordinator sendCoordinator;
    private final ContactLookupService contactLookupService;

    public SendController(SendCoordinator sendCoordinator, ContactLookupService contactLookupService) {
        this.sendCoordinator = sendCoordinator;
        this.contactLookupService = contactLookupService;
    }

    @PostMapping("/start")
    public ResponseEntity<Void> startSend() {
        sendCoordinator.start();
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/status")
    public SendStatusResponse getStatus() {
        return sendCoordinator.getStatus();
    }

    @PostMapping("/contact/{selector}")
    public ResponseEntity<Void> sendSingle(@PathVariable String selector) {
        sendCoordinator.sendSingle(contactLookupService.findBySelector(selector).getId());
        return ResponseEntity.accepted().build();
    }
}
