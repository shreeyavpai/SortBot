package com.sortbot.llm;

/** Target interface of the Adapter pattern: one way to talk to any language model. */
public interface LLMClient {
    String generate(String prompt) throws LLMException;
}
