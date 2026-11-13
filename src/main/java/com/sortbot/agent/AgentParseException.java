package com.sortbot.agent;

/** The model's reply could not be turned into the structure we asked for. */
public class AgentParseException extends Exception {
    public AgentParseException(String message) { super(message); }
}
