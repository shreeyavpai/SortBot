package com.sortbot.llm;

import com.sortbot.infra.Json;

import java.io.IOException;
import java.util.Map;

/** Adapter for a local Ollama server (no key, nothing leaves the computer). */
public class OllamaClient implements LLMClient {
    private final LLMConfig config;
    private final HttpTransport http;

    public OllamaClient(LLMConfig config, HttpTransport http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public String generate(String prompt) throws LLMException {
        if (config.ollamaModel().isEmpty()) throw new LLMException("No local model is set. Add OLLAMA_MODEL to your .env file.");
        String body = Json.write(Map.of("model", config.ollamaModel(), "prompt", prompt, "stream", false));
        try {
            HttpTransport.Response r = http.post(config.ollamaUrl() + "/api/generate", Map.of("Content-Type", "application/json"), body);
            if (r.status() != 200) {
                String snippet = r.body() == null ? "" : r.body().substring(0, Math.min(200, r.body().length()));
                throw new LLMException("Ollama returned HTTP " + r.status() + ". " + snippet);
            }
            Object text = Json.path(Json.parse(r.body()), "response");
            if (text instanceof String t && !t.isBlank()) return t;
            throw new LLMException("Ollama returned no text.");
        } catch (IOException e) {
            throw new LLMException("Could not reach Ollama at " + config.ollamaUrl() + ". Is it running? " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new LLMException("Ollama returned a reply that could not be read.");
        }
    }
}
