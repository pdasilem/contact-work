package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.project.Project;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BrevoApiMailTransport implements MailTransport {
    private static final Logger log = LoggerFactory.getLogger(BrevoApiMailTransport.class);
    private static final String BREVO_BASE_URL = "https://api.brevo.com/v3";

    private final RestClient restClient;
    private final AppProperties appProperties;

    public BrevoApiMailTransport(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.restClient = RestClient.builder()
                .baseUrl(BREVO_BASE_URL)
                .build();
    }

    BrevoApiMailTransport(RestClient restClient, AppProperties appProperties) {
        this.restClient = restClient;
        this.appProperties = appProperties;
    }

    @Override
    public MailSendResult send(Project project, MailEnvelope envelope) {
        String apiKey = requireApiKey();
        Map<String, Object> sender = buildSender(envelope);
        List<Map<String, String>> to = List.of(Map.of("email", envelope.toEmail()));
        List<Map<String, String>> attachments = envelope.attachments().stream()
                .map(a -> Map.of(
                        "name", a.filename(),
                        "content", Base64.getEncoder().encodeToString(a.content())
                ))
                .toList();

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", to);
        body.put("subject", envelope.subject());
        body.put("textContent", envelope.body());
        if (!attachments.isEmpty()) {
            body.put("attachment", attachments);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/smtp/email")
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);

        String messageId = extractMessageId(response);
        log.info("Brevo email sent to {} messageId={}", envelope.toEmail(), messageId);
        return new MailSendResult(messageId);
    }

    @Override
    public void verifyConnection(Project project) {
        String apiKey = requireApiKey();
        try {
            restClient.get()
                    .uri("/account")
                    .header("api-key", apiKey)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify Brevo API key", ex);
        }
    }

    private Map<String, Object> buildSender(MailEnvelope envelope) {
        if (envelope.fromName() != null && !envelope.fromName().isBlank()) {
            return Map.of("email", envelope.fromEmail(), "name", envelope.fromName());
        }
        return Map.of("email", envelope.fromEmail());
    }

    private String requireApiKey() {
        String apiKey = appProperties.mail().brevo().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("BREVO_API_KEY environment variable is not configured");
        }
        return apiKey;
    }

    @SuppressWarnings("unchecked")
    private String extractMessageId(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object messageId = response.get("messageId");
        if (messageId != null) {
            return messageId.toString();
        }
        Object messageIds = response.get("messageIds");
        if (messageIds instanceof List<?> list && !list.isEmpty()) {
            return list.getFirst().toString();
        }
        return null;
    }
}
