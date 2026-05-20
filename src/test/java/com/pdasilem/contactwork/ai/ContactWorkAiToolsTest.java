package com.pdasilem.contactwork.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdasilem.contactwork.config.AppProperties;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class ContactWorkAiToolsTest {

    @Test
    void missingBraveApiKeyReturnsConfigurationMessage() {
        ContactWorkAiTools tools = tools("", new CapturingHttpClient("{}"));

        String result = tools.braveWebSearch("current pharma partnering news");

        assertThat(result).isEqualTo("Brave Search is not configured. Set BRAVE_SEARCH_API_KEY.");
    }

    @Test
    void configuredBraveApiKeyIsSentAsSubscriptionToken() throws Exception {
        CapturingHttpClient httpClient = new CapturingHttpClient("""
                {"web":{"results":[{"title":"One","url":"https://example.com","description":"Snippet text"}]}}
                """);
        ContactWorkAiTools tools = tools("test-key", httpClient);

        String result = tools.braveWebSearch("public company news");

        assertThat(httpClient.lastRequest.headers().firstValue("X-Subscription-Token")).contains("test-key");
        assertThat(result).contains("One", "https://example.com", "Snippet text");
    }

    @Test
    void privateLookingBraveQueryIsRejected() {
        ContactWorkAiTools tools = tools("test-key", new CapturingHttpClient("{}"));

        String result = tools.braveWebSearch("What about jane@example.com?");

        assertThat(result).contains("refused");
    }

    private ContactWorkAiTools tools(String apiKey, HttpClient httpClient) {
        AppProperties properties = new AppProperties(
                new AppProperties.Resources("/tmp/contactwork-test"),
                new AppProperties.Mail(1000, "0 */5 * * * *", null, null),
                new AppProperties.Ai(new AppProperties.Brave(apiKey, "https://api.search.brave.com/res/v1/web/search", 5))
        );
        return new ContactWorkAiTools(
                null,
                null,
                properties,
                httpClient,
                new ObjectMapper(),
                null
        );
    }

    private static class CapturingHttpClient extends HttpClient {
        private final String responseBody;
        private HttpRequest lastRequest;

        private CapturingHttpClient(String responseBody) {
            this.responseBody = responseBody;
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
            return null;
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
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            this.lastRequest = request;
            HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(new HttpResponse.ResponseInfo() {
                @Override
                public int statusCode() {
                    return 200;
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(java.util.Map.of(), (left, right) -> true);
                }

                @Override
                public Version version() {
                    return Version.HTTP_1_1;
                }
            });
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
            subscriber.onNext(List.of(ByteBuffer.wrap(responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            subscriber.onComplete();
            return new FixedHttpResponse<>(request, subscriber.getBody().toCompletableFuture().join());
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
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

    private record FixedHttpResponse<T>(HttpRequest request, T body) implements HttpResponse<T> {
        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (left, right) -> true);
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

    }
}
