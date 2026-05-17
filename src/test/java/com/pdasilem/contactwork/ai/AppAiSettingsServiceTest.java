package com.pdasilem.contactwork.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.project.AiProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AppAiSettingsServiceTest {

    @Test
    void currentFallsBackToDefaultLocalSettings() {
        AppAiSettingsRepository repository = repository(Optional.empty());
        AppAiSettingsService service = new AppAiSettingsService(repository, new StubModelCatalog(List.of()), "local-default");

        AppAiSettings settings = service.current();

        assertThat(settings.getProvider()).isEqualTo(AiProvider.LOCAL_OLLAMA);
        assertThat(settings.getModel()).isEqualTo("local-default");
        assertThat(settings.getTemperature()).isEqualTo(AppAiSettingsService.DEFAULT_AI_TEMPERATURE);
    }

    @Test
    void saveValidatesAndPersistsSingleton() {
        AppAiSettingsRepository repository = repository(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(AppAiSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AppAiSettingsService service = new AppAiSettingsService(repository, new StubModelCatalog(List.of("gemini-free")),
                "local-default");

        AppAiSettings settings = service.save(AiProvider.GOOGLE_GENAI, " gemini-free ", 0.7);

        assertThat(settings.getId()).isEqualTo(AppAiSettings.SINGLETON_ID);
        assertThat(settings.getProvider()).isEqualTo(AiProvider.GOOGLE_GENAI);
        assertThat(settings.getModel()).isEqualTo("gemini-free");
        assertThat(settings.getTemperature()).isEqualTo(0.7);
    }

    @Test
    void saveRejectsUnknownModelAndOutOfRangeTemperature() {
        AppAiSettingsService service = new AppAiSettingsService(repository(Optional.empty()),
                new StubModelCatalog(List.of("gemini-free")), "local-default");

        assertThatThrownBy(() -> service.save(AiProvider.GOOGLE_GENAI, "gemini-paid", 0.7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed Google GenAI chat model");

        assertThatThrownBy(() -> service.save(AiProvider.GOOGLE_GENAI, "gemini-free", 3.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
    }

    private AppAiSettingsRepository repository(Optional<AppAiSettings> current) {
        AppAiSettingsRepository repository = org.mockito.Mockito.mock(AppAiSettingsRepository.class);
        when(repository.findById(AppAiSettings.SINGLETON_ID)).thenReturn(current);
        return repository;
    }

    private static class StubModelCatalog extends AiModelCatalogService {
        private final List<String> googleModels;

        StubModelCatalog(List<String> googleModels) {
            super(null, null);
            this.googleModels = googleModels;
        }

        @Override
        public List<String> requiredModelsFor(AiProvider provider) {
            return provider == AiProvider.GOOGLE_GENAI ? googleModels : List.of();
        }
    }
}
