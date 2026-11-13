package com.sortbot.agent;

import com.sortbot.core.MemoryManager;
import com.sortbot.domain.UserRule;

import java.util.List;
import java.util.Map;

/** Lists the saved rules. Read-only. */
public class ListRulesTool implements Tool {
    private final MemoryManager memory;

    public ListRulesTool(MemoryManager memory) { this.memory = memory; }

    @Override public String name() { return "list_rules"; }

    @Override public String description() { return "Show the user's saved rules. No args."; }

    @Override
    public ToolResult run(Map<String, String> args) {
        List<UserRule> rules = memory.getRules();
        if (rules.isEmpty()) return ToolResult.ok("You have no saved rules yet.");
        StringBuilder sb = new StringBuilder("Your rules:");
        for (UserRule r : rules) sb.append("\n- ").append(r.describe());
        return ToolResult.ok(sb.toString());
    }
}
