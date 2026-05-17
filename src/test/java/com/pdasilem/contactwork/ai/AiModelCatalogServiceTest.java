package com.pdasilem.contactwork.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdasilem.contactwork.project.AiProvider;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiModelCatalogServiceTest {

    @Test
    void keepsCurrentModelWhenCatalogIsEmpty() {
        AiModelCatalogService service = new AiModelCatalogService(
                new StubOllamaCatalog(List.of()),
                new StubGoogleCatalog(List.of())
        );

        assertThat(service.modelsFor(AiProvider.LOCAL_OLLAMA, "custom-local"))
                .containsExactly("custom-local");
    }

    @Test
    void appendsCurrentModelWhenCatalogDoesNotContainIt() {
        AiModelCatalogService service = new AiModelCatalogService(
                new StubOllamaCatalog(List.of("gemma4:e2b")),
                new StubGoogleCatalog(List.of())
        );

        assertThat(service.modelsFor(AiProvider.LOCAL_OLLAMA, "custom-local"))
                .containsExactly("gemma4:e2b", "custom-local");
    }

    @Test
    void doesNotAppendFilteredGoogleCurrentModel() {
        AiModelCatalogService service = new AiModelCatalogService(
                new StubOllamaCatalog(List.of()),
                new StubGoogleCatalog(List.of("gemini-free"))
        );

        assertThat(service.modelsFor(AiProvider.GOOGLE_GENAI, "gemini-paid"))
                .containsExactly("gemini-free");
    }

    private static class StubOllamaCatalog extends OllamaModelCatalogService {
        private final List<String> models;

        StubOllamaCatalog(List<String> models) {
            super(HttpClient.newHttpClient(), new ObjectMapper(), "http://localhost:11434");
            this.models = models;
        }

        @Override
        public List<String> localModels() {
            return models;
        }
    }

    private static class StubGoogleCatalog extends GoogleGenAiModelCatalogService {
        private final List<String> models;

        StubGoogleCatalog(List<String> models) {
            super(HttpClient.newHttpClient(), new ObjectMapper(), "test-key");
            this.models = models;
        }

        @Override
        public List<String> chatModels() {
            return models;
        }
    }
}
