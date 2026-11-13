package com.sortbot.agent;

import java.util.Map;

/** A request from the model to run one named tool with string arguments. */
public record ToolCall(String tool, Map<String, String> args) {}
