package com.sortbot.llm;

/** Creates a GeminiClient. */
public class GeminiClientCreator extends LLMClientCreator {
    public GeminiClientCreator(LLMConfig config, HttpTransport http) { super(config, http); }

    @Override
    public LLMClient createClient() { return new GeminiClient(config, http); }
}
