package com.sortbot.agent;

import com.sortbot.core.TraceLogger;
import com.sortbot.infra.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registers tools and validates every tool call from the model before anything runs. */
public class ToolManager {
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final TraceLogger trace;

    public ToolManager(TraceLogger trace) {
        this.trace = trace;
    }

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public String describeTools() {
        StringBuilder sb = new StringBuilder();
        for (Tool t : tools.values()) sb.append("- ").append(t.name()).append(": ").append(t.description()).append("\n");
        return sb.toString();
    }

    public ToolResult execute(ToolCall call) {
        Tool tool = tools.get(call.tool());
        ToolResult result;
        if (tool == null) {
            result = ToolResult.rejected("I can't do that: there is no tool called \"" + call.tool() + "\". I can only use the tools I've been given.");
        } else {
            List<String> missing = new ArrayList<>();
            for (String a : tool.requiredArgs()) {
                String v = call.args().get(a);
                if (v == null || v.isBlank()) missing.add(a);
            }
            if (!missing.isEmpty()) {
                result = ToolResult.rejected("I need more information to do that (missing: " + String.join(", ", missing) + ").");
            } else {
                try {
                    result = tool.run(call.args());
                } catch (Exception e) {
                    result = ToolResult.rejected("That didn't work: " + e.getMessage());
                }
            }
        }
        trace.toolCall(call.tool(), Json.write(call.args()), (result.ok() ? "ok: " : "rejected: ") + result.message());
        return result;
    }
}
