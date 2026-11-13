package com.sortbot.agent;

import java.util.List;
import java.util.Map;

/** A capability the agent can use. Tools may only produce plans, lists or proposals; they never change files. */
public interface Tool {
    String name();

    /** One line shown to the model, including the arguments it accepts. */
    String description();

    default List<String> requiredArgs() { return List.of(); }

    ToolResult run(Map<String, String> args) throws Exception;
}
