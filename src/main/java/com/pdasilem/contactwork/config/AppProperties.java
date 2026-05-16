package com.pdasilem.contactwork.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull Resources resources,
        @NotNull Mail mail
) {

    public record Resources(
            @NotBlank String letterTemplate,
            @NotBlank String workingDir
    ) {
    }

    public record Mail(
            @NotBlank String subject,
            @NotBlank String body,
            @NotBlank String letterAttachmentFilename,
            String from,
            long sendDelayMs,
            @NotBlank String inboxSyncCron,
            @NotNull Gmail gmail
    ) {
    }

    public record Gmail(
            String username,
            String appPassword,
            OAuth oauth
    ) {
        public Gmail(String username, String appPassword) {
            this(username, appPassword, new OAuth(null, null, null));
        }

        public Gmail {
            if (oauth == null) {
                oauth = new OAuth(null, null, null);
            }
        }
    }

    public record OAuth(
            String clientId,
            String clientSecret,
            String redirectUri
    ) {
    }
}
