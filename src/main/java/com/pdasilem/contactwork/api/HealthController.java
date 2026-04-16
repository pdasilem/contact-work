package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.mail.MailHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final MailHealthService mailHealthService;

    public HealthController(MailHealthService mailHealthService) {
        this.mailHealthService = mailHealthService;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("OK");
    }

    @GetMapping("/mail")
    public HealthResponse mailHealth() {
        mailHealthService.verifyConnections();
        return new HealthResponse("OK");
    }
}
