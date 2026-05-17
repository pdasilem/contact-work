package com.pdasilem.contactwork.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OllamaModelCatalogService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OllamaModelCatalogService(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl.trim();
    }

    public List<String> localModels() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama model catalog failed: HTTP " + response.statusCode());
            }
            return localModels(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Ollama model catalog request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama model catalog request interrupted", ex);
        }
    }

    List<String> localModels(String json) {
        try {
            JsonNode models = objectMapper.readTree(json).path("models");
            List<String> result = new ArrayList<>();
            if (!models.isArray()) {
                return result;
            }
            for (JsonNode model : models) {
                String name = model.path("name").asText("");
                if (!name.isBlank()) {
                    result.add(name);
                }
            }
            return result.stream().distinct().sorted().toList();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Ollama model catalog response is invalid", ex);
        }
    }
}
