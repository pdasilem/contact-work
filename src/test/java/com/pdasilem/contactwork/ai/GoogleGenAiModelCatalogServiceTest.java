package com.pdasilem.contactwork.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class GoogleGenAiModelCatalogServiceTest {

    @Test
    void listsModelsThatSupportGenerateContent() {
        GoogleGenAiModelCatalogService service = new GoogleGenAiModelCatalogService(
                HttpClient.newHttpClient(), new ObjectMapper(), "test-key");

        String json = """
                {
                  "models": [
                    {
                      "name": "models/gemini-flash",
                      "supportedGenerationMethods": ["generateContent"]
                    },
                    {
                      "name": "models/gemini-pro",
                      "supportedGenerationMethods": ["countTokens", "generateContent"]
                    },
                    {
                      "name": "models/gemini-no-methods"
                    },
                    {
                      "name": "models/gemini-embed",
                      "supportedGenerationMethods": ["embedContent"]
                    }
                  ]
                }
                """;

        assertThat(service.chatModels(json))
                .containsExactly("gemini-flash", "gemini-pro");
    }
}
