package com.pdasilem.contactwork.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.project.ProjectService;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GmailAliasService {
    static final String GMAIL_SETTINGS_SCOPE = "https://www.googleapis.com/auth/gmail.settings.basic";

    private static final String AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SEND_AS_URL = "https://gmail.googleapis.com/gmail/v1/users/me/settings/sendAs";

    private final ProjectService projectService;
    private final AppProperties appProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GmailAliasService(
            ProjectService projectService,
            AppProperties appProperties,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.appProperties = appProperties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public String authorizationUrl(UUID projectId) {
        var oauth = oauthProperties();
        requireConfigured(oauth);
        return AUTHORIZATION_URL
                + "?client_id=" + encode(oauth.clientId())
                + "&redirect_uri=" + encode(oauth.redirectUri())
                + "&response_type=code"
                + "&scope=" + encode(GMAIL_SETTINGS_SCOPE)
                + "&access_type=online"
                + "&prompt=consent"
                + "&state=" + encode(projectId.toString());
    }

    public void syncDefaultAlias(UUID projectId) {
        throw new GmailAuthorizationRequiredException(authorizationUrl(projectId));
    }

    public void exchangeCodeAndSyncAlias(UUID projectId, String code) {
        var oauth = oauthProperties();
        requireConfigured(oauth);
        TokenResponse token = exchangeCode(oauth, code);
        GmailAlias alias = fetchDefaultAlias(token.accessToken());
        projectService.updateSenderIdentity(projectId, alias.email(), alias.displayName());
    }

    private TokenResponse exchangeCode(AppProperties.OAuth oauth, String code) {
        String body = form(
                "code", code,
                "client_id", oauth.clientId(),
                "client_secret", oauth.clientSecret(),
                "redirect_uri", oauth.redirectUri(),
                "grant_type", "authorization_code"
        );
        JsonNode node = postToken(body);
        return new TokenResponse(text(node, "access_token"));
    }

    private JsonNode postToken(String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendJson(request, "Google OAuth token request");
    }

    private GmailAlias fetchDefaultAlias(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Google OAuth did not return an access token");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(SEND_AS_URL))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return readDefaultAlias(send(request, "Gmail send-as alias request"));
    }

    static GmailAlias readDefaultAlias(String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode aliases = mapper.readTree(responseBody).path("sendAs");
            if (!aliases.isArray()) {
                throw new IllegalStateException("Gmail sendAs response did not contain aliases");
            }
            for (JsonNode alias : aliases) {
                if (alias.path("isDefault").asBoolean(false)) {
                    String email = text(alias, "sendAsEmail");
                    String displayName = blankToNull(text(alias, "displayName"));
                    if (email == null || email.isBlank()) {
                        throw new IllegalStateException("Default Gmail alias is missing an email address");
                    }
                    return new GmailAlias(email, displayName);
                }
            }
            throw new IllegalStateException("Gmail default send-as alias was not found");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse Gmail send-as aliases", ex);
        }
    }

    private JsonNode sendJson(HttpRequest request, String label) {
        try {
            return objectMapper.readTree(send(request, label));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse Google OAuth response", ex);
        }
    }

    private String send(HttpRequest request, String label) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        label + " failed with HTTP " + response.statusCode() + responseDetail(response.body()));
            }
            return response.body();
        } catch (IOException ex) {
            throw new IllegalStateException(label + " failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(label + " was interrupted", ex);
        }
    }

    private String responseDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String message = text(node, "error_description");
            if (message == null || message.isBlank()) {
                message = text(node, "error");
            }
            if ((message == null || message.isBlank()) && node.path("error").isObject()) {
                message = text(node.path("error"), "message");
            }
            if (message == null || message.isBlank()) {
                message = text(node, "message");
            }
            if (message != null && !message.isBlank()) {
                return ": " + message;
            }
        } catch (IOException ignored) {
            // Fall through to compact raw body.
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.isBlank() ? "" : ": " + compact;
    }

    private AppProperties.OAuth oauthProperties() {
        AppProperties.Gmail gmail = appProperties.mail().gmail();
        return gmail == null ? null : gmail.oauth();
    }

    private void requireConfigured(AppProperties.OAuth oauth) {
        if (oauth == null) {
            throw new IllegalStateException("Google OAuth client ID, client secret, and redirect URI must be configured");
        }
        if (isBlank(oauth.clientId()) || isBlank(oauth.clientSecret()) || isBlank(oauth.redirectUri())) {
            throw new IllegalStateException("Google OAuth client ID, client secret, and redirect URI must be configured");
        }
    }

    private static String form(String... pairs) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                builder.append('&');
            }
            builder.append(encode(pairs[i])).append('=').append(encode(pairs[i + 1]));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field == null || field.isNull() ? null : field.asText();
    }

    private record TokenResponse(String accessToken) {
    }
}
