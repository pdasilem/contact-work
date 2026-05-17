package com.pdasilem.contactwork.ai;

import com.pdasilem.contactwork.project.AiProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiModelCatalogService {

    private final OllamaModelCatalogService ollamaModelCatalogService;
    private final GoogleGenAiModelCatalogService googleGenAiModelCatalogService;

    public AiModelCatalogService(
            OllamaModelCatalogService ollamaModelCatalogService,
            GoogleGenAiModelCatalogService googleGenAiModelCatalogService
    ) {
        this.ollamaModelCatalogService = ollamaModelCatalogService;
        this.googleGenAiModelCatalogService = googleGenAiModelCatalogService;
    }

    public List<String> modelsFor(AiProvider provider, String currentModel) {
        AiProvider resolvedProvider = provider == null ? AiProvider.LOCAL_OLLAMA : provider;
        List<String> models = switch (resolvedProvider) {
            case LOCAL_OLLAMA -> localModels();
            case GOOGLE_GENAI -> googleModels();
        };
        if (resolvedProvider == AiProvider.GOOGLE_GENAI) {
            return models;
        }
        String fallback = currentModel == null || currentModel.isBlank()
                ? AppAiSettingsService.FALLBACK_LOCAL_AI_MODEL
                : currentModel.trim();
        if (models.isEmpty()) {
            return List.of(fallback);
        }
        if (models.contains(fallback)) {
            return models;
        }
        List<String> withCurrent = new ArrayList<>(models);
        withCurrent.add(fallback);
        return withCurrent;
    }

    public List<String> requiredModelsFor(AiProvider provider) {
        return switch (provider == null ? AiProvider.LOCAL_OLLAMA : provider) {
            case LOCAL_OLLAMA -> localModels();
            case GOOGLE_GENAI -> googleModels();
        };
    }

    private List<String> localModels() {
        return ollamaModelCatalogService.localModels();
    }

    private List<String> googleModels() {
        return googleGenAiModelCatalogService.chatModels();
    }
}
