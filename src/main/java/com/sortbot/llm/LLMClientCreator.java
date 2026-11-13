package com.sortbot.llm;

/** Factory Method: subclasses decide which LLMClient to build, so callers never name a provider. */
public abstract class LLMClientCreator {
    protected final LLMConfig config;
    protected final HttpTransport http;

    protected LLMClientCreator(LLMConfig config, HttpTransport http) {
        this.config = config;
        this.http = http;
    }

    public abstract LLMClient createClient();

    /** Picks the creator for config.provider(): "gemini" or "ollama". */
    public static LLMClientCreator forConfig(LLMConfig config, HttpTransport http) {
        return switch (config.provider()) {
            case "gemini" -> new GeminiClientCreator(config, http);
            case "ollama" -> new OllamaClientCreator(config, http);
            default -> throw new IllegalArgumentException("Unknown LLM_PROVIDER '" + config.provider() + "'. Use gemini or ollama.");
        };
    }
}
