package com.sortbot.llm;

import com.sortbot.infra.DotEnv;

/** Model settings read from the local .env file or environment variables. The key is never printed. */
public class LLMConfig {
    private final String provider;
    private final String model;
    private final String apiKey;
    private final String ollamaUrl;
    private final String ollamaModel;

    public LLMConfig(String provider, String model, String apiKey, String ollamaUrl, String ollamaModel) {
        this.provider = provider == null ? "gemini" : provider.trim().toLowerCase();
        this.model = model == null ? "" : model.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.ollamaUrl = ollamaUrl == null || ollamaUrl.isBlank() ? "http://localhost:11434" : ollamaUrl.trim().replaceAll("/+$", "");
        this.ollamaModel = ollamaModel == null ? "" : ollamaModel.trim();
    }

    public static LLMConfig fromEnv(DotEnv env) {
        return new LLMConfig(env.get("LLM_PROVIDER", "gemini"), env.get("LLM_MODEL", ""), env.get("LLM_API_KEY", ""),
                env.get("OLLAMA_URL", "http://localhost:11434"), env.get("OLLAMA_MODEL", ""));
    }

    public String provider() { return provider; }
    public String model() { return model; }
    public String apiKey() { return apiKey; }
    public String ollamaUrl() { return ollamaUrl; }
    public String ollamaModel() { return ollamaModel; }

    @Override
    public String toString() {
        return "LLMConfig[provider=" + provider + ", model=" + (provider.equals("ollama") ? ollamaModel : model) + ", apiKey=" + (apiKey.isEmpty() ? "(not set)" : "(hidden)") + "]";
    }
}
