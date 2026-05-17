package com.pdasilem.contactwork.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleGenAiModelCatalogService {
    private static final String MODEL_LIST_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GoogleGenAiModelCatalogService(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${spring.ai.google.genai.api-key:}") String apiKey
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public List<String> chatModels() {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        HttpRequest request = HttpRequest.newBuilder(catalogUri())
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Google GenAI model catalog failed: HTTP " + response.statusCode());
            }
            return chatModels(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Google GenAI model catalog request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google GenAI model catalog request interrupted", ex);
        }
    }

    List<String> chatModels(String json) {
        try {
            JsonNode models = objectMapper.readTree(json).path("models");
            List<String> result = new ArrayList<>();
            if (!models.isArray()) {
                return result;
            }
            for (JsonNode model : models) {
                String name = normalizedName(model.path("name").asText(""));
                if (!name.isBlank() && supportsGenerateContent(model)) {
                    result.add(name);
                }
            }
            return result.stream().distinct().sorted().toList();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Google GenAI model catalog response is invalid", ex);
        }
    }

    private URI catalogUri() {
        return URI.create(MODEL_LIST_URL + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
    }

    private boolean supportsGenerateContent(JsonNode model) {
        JsonNode methods = model.path("supportedGenerationMethods");
        if (!methods.isArray()) {
            return false;
        }
        for (JsonNode method : methods) {
            if ("generateContent".equals(method.asText())) {
                return true;
            }
        }
        return false;
    }

    private String normalizedName(String name) {
        if (name.startsWith("models/")) {
            return name.substring("models/".length());
        }
        return name;
    }
}
