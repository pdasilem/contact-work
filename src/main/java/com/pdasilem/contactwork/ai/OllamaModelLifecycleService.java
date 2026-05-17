package com.pdasilem.contactwork.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OllamaModelLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(OllamaModelLifecycleService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OllamaModelLifecycleService(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl.trim();
    }

    public void unload(String model) {
        if (model == null || model.isBlank()) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model.trim(),
                    "keep_alive", 0
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Ollama model unload failed: model={}, status={}", model, response.statusCode());
            }
        } catch (IOException ex) {
            log.warn("Ollama model unload request failed: model={}", model, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Ollama model unload interrupted: model={}", model, ex);
        }
    }
}
