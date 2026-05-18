package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.auth.CurrentUserService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdasilem.contactwork.ai.AiModelCatalogService;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.project.AiProvider;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectRepository;
import com.pdasilem.contactwork.project.ProjectService;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;

class GmailAliasServiceTest {

    @Test
    void picksDefaultAliasAndMapsAddressAndDisplayName() {
        GmailAlias alias = GmailAliasService.readDefaultAlias("""
                {
                  "sendAs": [
                    {
                      "sendAsEmail": "other@example.com",
                      "displayName": "Other",
                      "isDefault": false
                    },
                    {
                      "sendAsEmail": "contact@shviltashvilebi.ge",
                      "displayName": "Shviltashvilebi Ltd",
                      "isDefault": true
                    }
                  ]
                }
                """);

        assertThat(alias.email()).isEqualTo("contact@shviltashvilebi.ge");
        assertThat(alias.displayName()).isEqualTo("Shviltashvilebi Ltd");
    }

    @Test
    void blankDisplayNameMapsToNull() {
        GmailAlias alias = GmailAliasService.readDefaultAlias("""
                {
                  "sendAs": [
                    {
                      "sendAsEmail": "contact@shviltashvilebi.ge",
                      "displayName": " ",
                      "isDefault": true
                    }
                  ]
                }
                """);

        assertThat(alias.email()).isEqualTo("contact@shviltashvilebi.ge");
        assertThat(alias.displayName()).isNull();
    }

    @Test
    void oauthCodeSyncUpdatesSenderIdentityWithoutPersistingTokens() {
        RecordingProjectService projectService = new RecordingProjectService();
        FakeHttpClient httpClient = new FakeHttpClient(
                response(200, """
                        {
                          "access_token": "access-token",
                          "refresh_token": "must-not-be-stored"
                        }
                        """),
                response(200, """
                        {
                          "sendAs": [
                            {
                              "sendAsEmail": "contact@shviltashvilebi.ge",
                              "displayName": "Shviltashvilebi Ltd",
                              "isDefault": true
                            }
                          ]
                        }
                        """)
        );
        GmailAliasService service = service(projectService, properties(), httpClient);

        service.exchangeCodeAndSyncAlias(Project.DEFAULT_PROJECT_ID, "code-123");

        assertThat(httpClient.requests).hasSize(2);
        assertThat(httpClient.requests.get(0).uri().toString()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(httpClient.requests.get(0).method()).isEqualTo("POST");
        assertThat(httpClient.requests.get(1).uri().toString())
                .isEqualTo("https://gmail.googleapis.com/gmail/v1/users/me/settings/sendAs");
        assertThat(httpClient.requests.get(1).headers().firstValue("Authorization"))
                .contains("Bearer access-token");
        assertThat(projectService.projectId).isEqualTo(Project.DEFAULT_PROJECT_ID);
        assertThat(projectService.mailFrom).isEqualTo("contact@shviltashvilebi.ge");
        assertThat(projectService.mailFromName).isEqualTo("Shviltashvilebi Ltd");
    }

    @Test
    void authorizationUrlIncludesOauthConfigStateAndGmailSettingsScope() {
        GmailAliasService service = service(new RecordingProjectService(), properties(), new FakeHttpClient());

        String url = service.authorizationUrl(Project.DEFAULT_PROJECT_ID);

        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        Map<String, String> params = queryParams(url);
        assertThat(params)
                .containsEntry("client_id", "client-id")
                .containsEntry("redirect_uri", "http://localhost/oauth/gmail/callback")
                .containsEntry("response_type", "code")
                .containsEntry("scope", GmailAliasService.GMAIL_SETTINGS_SCOPE)
                .containsEntry("access_type", "online")
                .containsEntry("prompt", "consent")
                .containsEntry("state", Project.DEFAULT_PROJECT_ID.toString());
    }

    @Test
    void missingGmailOauthConfigFailsWithoutNullPointer() {
        GmailAliasService service = service(new RecordingProjectService(), propertiesWithoutGmail(), new FakeHttpClient());

        Throwable thrown = catchThrowable(() -> service.authorizationUrl(Project.DEFAULT_PROJECT_ID));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Google OAuth client ID, client secret, and redirect URI must be configured");
    }

    @Test
    void tokenFailureIncludesResponseDetail() {
        GmailAliasService service = service(new RecordingProjectService(), properties(), new FakeHttpClient(response(400, """
                {
                  "error": "invalid_grant",
                  "error_description": "Bad authorization code"
                }
                """)));

        Throwable thrown = catchThrowable(() -> service.exchangeCodeAndSyncAlias(Project.DEFAULT_PROJECT_ID, "bad-code"));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Google OAuth token request failed with HTTP 400: Bad authorization code");
    }

    @Test
    void sendAsFailureIncludesResponseDetail() {
        GmailAliasService service = service(
                new RecordingProjectService(),
                properties(),
                new FakeHttpClient(
                        response(200, "{\"access_token\":\"access-token\"}"),
                        response(403, "{\"error\":{\"message\":\"insufficient scope\"}}")
                )
        );

        Throwable thrown = catchThrowable(() -> service.exchangeCodeAndSyncAlias(Project.DEFAULT_PROJECT_ID, "code-123"));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gmail send-as alias request failed with HTTP 403: insufficient scope");
    }

    private static GmailAliasService service(
            ProjectService projectService,
            AppProperties properties,
            HttpClient httpClient
    ) {
        return new GmailAliasService(projectService, properties, httpClient, new ObjectMapper());
    }

    private static AppProperties properties() {
        return new AppProperties(
                new AppProperties.Resources("/tmp"),
                new AppProperties.Mail(
                        0,
                        "0 */5 * * * *",
                        new AppProperties.Gmail(
                                "INBOX",
                                "[Gmail]/Sent Mail",
                                "[Gmail]/Spam",
                                new AppProperties.OAuth(
                                        "client-id",
                                        "client-secret",
                                        "http://localhost/oauth/gmail/callback"
                                )
                        )
                ),
                null
        );
    }

    private static AppProperties propertiesWithoutGmail() {
        return new AppProperties(
                new AppProperties.Resources("/tmp"),
                new AppProperties.Mail(0, "0 */5 * * * *", null),
                null
        );
    }

    private static TestHttpResponse response(int status, String body) {
        return new TestHttpResponse(status, body);
    }

    private static Map<String, String> queryParams(String url) {
        return Arrays.stream(url.substring(url.indexOf('?') + 1).split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        part -> decode(part[0]),
                        part -> part.length > 1 ? decode(part[1]) : ""
                ));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static final class RecordingProjectService extends ProjectService {
        private UUID projectId;
        private String mailFrom;
        private String mailFromName;

        private RecordingProjectService() {
            super(projectRepository(), appProperties(), org.mockito.Mockito.mock(CurrentUserService.class));
        }

        @Override
        public Project updateSenderIdentity(UUID projectId, String mailFrom, String mailFromName) {
            this.projectId = projectId;
            this.mailFrom = mailFrom;
            this.mailFromName = mailFromName;
            Project project = new Project();
            project.setId(projectId);
            project.setMailFrom(mailFrom);
            project.setMailFromName(mailFromName);
            return project;
        }
    }

    private static ProjectRepository projectRepository() {
        return (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class<?>[]{ProjectRepository.class},
                (proxy, method, args) -> null
        );
    }

    private static AppProperties appProperties() {
        return new AppProperties(
                new AppProperties.Resources("/tmp/contactwork-test"),
                new AppProperties.Mail(1000, "0 */5 * * * *", null),
                null
        );
    }

    private static final class StubModelCatalog extends AiModelCatalogService {
        private StubModelCatalog() {
            super(null, null);
        }

        @Override
        public List<String> requiredModelsFor(AiProvider provider) {
            return List.of();
        }
    }

    private static final class FakeHttpClient extends HttpClient {
        private final List<TestHttpResponse> responses;
        private final List<HttpRequest> requests = new ArrayList<>();
        private int index;

        private FakeHttpClient(TestHttpResponse... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.add(request);
            return (HttpResponse<T>) responses.get(index++);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private record TestHttpResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return null;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
