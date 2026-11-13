package com.sortbot.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Real HTTP transport built on java.net.http. */
public class JdkHttpTransport implements HttpTransport {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final Duration timeout;

    public JdkHttpTransport() { this(Duration.ofSeconds(120)); }

    public JdkHttpTransport(Duration timeout) { this.timeout = timeout; }

    @Override
    public Response post(String url, Map<String, String> headers, String body) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        try {
            HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(r.statusCode(), r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }
}
