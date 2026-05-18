package com.pdasilem.contactwork.ai;

import com.pdasilem.contactwork.auth.CurrentUserService;
import com.pdasilem.contactwork.project.AiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppAiSettingsService {
    public static final AiProvider DEFAULT_AI_PROVIDER = AiProvider.LOCAL_OLLAMA;
    public static final String FALLBACK_LOCAL_AI_MODEL = "gemma4:e2b";
    public static final double DEFAULT_AI_TEMPERATURE = 0.2;
    public static final double MIN_AI_TEMPERATURE = 0.0;
    public static final double MAX_AI_TEMPERATURE = 2.0;

    private final AppAiSettingsRepository repository;
    private final AiModelCatalogService modelCatalogService;
    private final CurrentUserService currentUserService;
    private final String defaultLocalAiModel;

    public AppAiSettingsService(
            AppAiSettingsRepository repository,
            AiModelCatalogService modelCatalogService,
            CurrentUserService currentUserService,
            @Value("${spring.ai.ollama.chat.options.model:" + FALLBACK_LOCAL_AI_MODEL + "}") String defaultLocalAiModel
    ) {
        this.repository = repository;
        this.modelCatalogService = modelCatalogService;
        this.currentUserService = currentUserService;
        this.defaultLocalAiModel = resolveLocalDefault(defaultLocalAiModel);
    }

    public AppAiSettings current() {
        return repository.findById(AppAiSettings.SINGLETON_ID)
                .orElseGet(this::defaults);
    }

    @Transactional
    public AppAiSettings save(AiProvider provider, String model, Double temperature) {
        currentUserService.requireAdmin();
        AiProvider resolvedProvider = provider == null ? DEFAULT_AI_PROVIDER : provider;
        String resolvedModel = resolveModel(model);
        double resolvedTemperature = resolveTemperature(temperature);
        validateModel(resolvedProvider, resolvedModel);
        AppAiSettings settings = repository.findById(AppAiSettings.SINGLETON_ID)
                .orElseGet(AppAiSettings::new);
        settings.setId(AppAiSettings.SINGLETON_ID);
        settings.setProvider(resolvedProvider);
        settings.setModel(resolvedModel);
        settings.setTemperature(resolvedTemperature);
        return repository.save(settings);
    }

    public String defaultLocalAiModel() {
        return defaultLocalAiModel;
    }

    private AppAiSettings defaults() {
        AppAiSettings settings = new AppAiSettings();
        settings.setId(AppAiSettings.SINGLETON_ID);
        settings.setProvider(DEFAULT_AI_PROVIDER);
        settings.setModel(defaultLocalAiModel);
        settings.setTemperature(DEFAULT_AI_TEMPERATURE);
        return settings;
    }

    private String resolveModel(String model) {
        if (model == null || model.isBlank()) {
            return defaultLocalAiModel;
        }
        return model.trim();
    }

    private double resolveTemperature(Double temperature) {
        if (temperature == null) {
            return DEFAULT_AI_TEMPERATURE;
        }
        if (temperature < MIN_AI_TEMPERATURE || temperature > MAX_AI_TEMPERATURE) {
            throw new IllegalArgumentException("AI temperature must be between 0.0 and 2.0");
        }
        return temperature;
    }

    private void validateModel(AiProvider provider, String model) {
        var models = modelCatalogService.requiredModelsFor(provider);
        if (models.isEmpty()) {
            if (provider == AiProvider.LOCAL_OLLAMA) {
                return;
            }
            throw new IllegalArgumentException("No Google GenAI chat models available");
        }
        if (!models.contains(model)) {
            throw new IllegalArgumentException(provider == AiProvider.GOOGLE_GENAI
                    ? "AI model must be an allowed Google GenAI chat model"
                    : "AI model must be an installed local Ollama model");
        }
    }

    private String resolveLocalDefault(String model) {
        if (model == null || model.isBlank()) {
            return FALLBACK_LOCAL_AI_MODEL;
        }
        return model.trim();
    }
}
