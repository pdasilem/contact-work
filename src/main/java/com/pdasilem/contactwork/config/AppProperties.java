package com.pdasilem.contactwork.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull Resources resources,
        @NotNull Mail mail,
        Ai ai
) {
    public AppProperties {
        if (ai == null) {
            ai = new Ai(null);
        }
    }

    public record Resources(
            @NotBlank String workingDir
    ) {
    }

    public record Mail(
            long sendDelayMs,
            @NotBlank String inboxSyncCron,
            Gmail gmail,
            Brevo brevo
    ) {
        public Mail {
            if (brevo == null) {
                brevo = new Brevo("");
            }
        }
    }

    public record Brevo(
            String apiKey
    ) {
    }

    public record Gmail(
            String inboxFolder,
            String sentFolder,
            String spamFolder,
            OAuth oauth
    ) {
        public Gmail {
            if (inboxFolder == null || inboxFolder.isBlank()) {
                inboxFolder = "INBOX";
            }
            if (sentFolder == null || sentFolder.isBlank()) {
                sentFolder = "[Gmail]/Sent Mail";
            }
            if (spamFolder == null || spamFolder.isBlank()) {
                spamFolder = "[Gmail]/Spam";
            }
        }
    }

    public record OAuth(
            String clientId,
            String clientSecret,
            String redirectUri
    ) {
    }

    public record Ai(
            Brave brave
    ) {
        public Ai {
            if (brave == null) {
                brave = new Brave("", "https://api.search.brave.com/res/v1/web/search", 5);
            }
        }
    }

    public record Brave(
            String apiKey,
            String webSearchUrl,
            int count
    ) {
        public Brave {
            if (webSearchUrl == null || webSearchUrl.isBlank()) {
                webSearchUrl = "https://api.search.brave.com/res/v1/web/search";
            }
            if (count <= 0) {
                count = 5;
            }
        }
    }
}
