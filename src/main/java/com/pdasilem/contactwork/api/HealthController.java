package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.mail.MailHealthService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final MailHealthService mailHealthService;

    public HealthController(MailHealthService mailHealthService) {
        this.mailHealthService = mailHealthService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("OK");
    }

    @GetMapping("/projects/{projectId}/health/mail")
    public HealthResponse projectMailHealth(@PathVariable UUID projectId) {
        mailHealthService.verifyConnections(projectId);
        return new HealthResponse("OK");
    }
}
