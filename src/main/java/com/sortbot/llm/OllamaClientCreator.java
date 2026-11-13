package com.sortbot.llm;

/** Creates an OllamaClient. */
public class OllamaClientCreator extends LLMClientCreator {
    public OllamaClientCreator(LLMConfig config, HttpTransport http) { super(config, http); }

    @Override
    public LLMClient createClient() { return new OllamaClient(config, http); }
}
