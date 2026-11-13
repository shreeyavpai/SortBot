package com.sortbot.llm;

import com.sortbot.infra.Json;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Adapter for Google's Gemini REST API. Retries rate limits and server errors with backoff. */
public class GeminiClient implements LLMClient {
    static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_ATTEMPTS = 3;

    private final LLMConfig config;
    private final HttpTransport http;
    private final Sleeper sleeper;

    public GeminiClient(LLMConfig config, HttpTransport http) {
        this(config, http, Thread::sleep);
    }

    public GeminiClient(LLMConfig config, HttpTransport http, Sleeper sleeper) {
        this.config = config;
        this.http = http;
        this.sleeper = sleeper;
    }

    @Override
    public String generate(String prompt) throws LLMException {
        if (config.apiKey().isEmpty()) throw new LLMException("No API key is set. Add LLM_API_KEY to your .env file.");
        if (config.model().isEmpty()) throw new LLMException("No model is set. Add LLM_MODEL to your .env file.");
        String url = BASE_URL + config.model() + ":generateContent";
        Map<String, String> headers = Map.of("Content-Type", "application/json", "x-goog-api-key", config.apiKey());
        String body = Json.write(Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.2)));

        LLMException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpTransport.Response r = http.post(url, headers, body);
                int s = r.status();
                if (s == 200) return extractText(r.body());
                if (s == 429 || s >= 500) {
                    last = new LLMException(s == 429 ? "The model is rate limited right now (HTTP 429). Try again in a minute." : "The model service had a problem (HTTP " + s + ").");
                } else if (s == 401 || s == 403 || s == 400) {
                    throw new LLMException("The model rejected the request (HTTP " + s + "). Check LLM_API_KEY and LLM_MODEL.");
                } else if (s == 404) {
                    throw new LLMException("Model not found: " + config.model() + ". Check LLM_MODEL.");
                } else {
                    throw new LLMException("Unexpected response from the model (HTTP " + s + ").");
                }
            } catch (IOException e) {
                last = new LLMException("Could not reach the model service: " + e.getMessage(), e);
            }
            if (attempt < MAX_ATTEMPTS) {
                try {
                    sleeper.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LLMException("Interrupted while waiting to retry");
                }
            }
        }
        throw last;
    }

    private static String extractText(String body) throws LLMException {
        try {
            Object text = Json.path(Json.parse(body), "candidates", 0, "content", "parts", 0, "text");
            if (text instanceof String t && !t.isBlank()) return t;
        } catch (IllegalArgumentException ignored) {
            // fall through to the error below
        }
        throw new LLMException("The model returned no usable text (the reply may have been blocked or malformed).");
    }
}
