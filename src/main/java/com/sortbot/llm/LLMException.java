package com.sortbot.llm;

/** The language model could not be reached or returned something unusable. Messages never contain the API key. */
public class LLMException extends Exception {
    public LLMException(String message) { super(message); }
    public LLMException(String message, Throwable cause) { super(message, cause); }
}
