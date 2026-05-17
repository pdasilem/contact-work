package com.pdasilem.contactwork.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class OllamaModelCatalogServiceTest {

    @Test
    void parsesInstalledLocalModelNames() {
        OllamaModelCatalogService service = new OllamaModelCatalogService(
                HttpClient.newHttpClient(), new ObjectMapper(), "http://localhost:11434");

        String json = """
                {
                  "models": [
                    {"name": "gemma4:e2b"},
                    {"name": "llama3.2:latest"},
                    {"name": "gemma4:e2b"},
                    {"name": ""}
                  ]
                }
                """;

        assertThat(service.localModels(json))
                .containsExactly("gemma4:e2b", "llama3.2:latest");
    }
}
