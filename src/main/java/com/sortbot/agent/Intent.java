package com.sortbot.agent;

/** What the model understood: either a tool to run or a clarifying question for the user. */
public record Intent(ToolCall toolCall, String clarify) {
    public boolean needsClarification() { return toolCall == null; }
}
