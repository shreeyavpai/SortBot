package com.sortbot.agent;

import com.sortbot.core.MemoryManager;
import com.sortbot.domain.UserRule;

import java.util.List;
import java.util.Map;

/** Turns a sentence like "always put lecture slides under School" into a proposed rule. Nothing is saved until the user confirms. */
public class ProposeRuleTool implements Tool {
    private final AgentSession session;
    private final MemoryManager memory;

    public ProposeRuleTool(AgentSession session, MemoryManager memory) {
        this.session = session;
        this.memory = memory;
    }

    @Override public String name() { return "propose_rule"; }

    @Override
    public String description() {
        return "Propose a saved rule. Required args: kind (place_category, place_name or protect_path) and match (a category, part of a file name, or part of a path). "
                + "For place_* kinds also give folder (a relative folder such as School/Lectures).";
    }

    @Override public List<String> requiredArgs() { return List.of("kind", "match"); }

    @Override
    public ToolResult run(Map<String, String> args) {
        UserRule.Kind kind;
        try {
            kind = UserRule.Kind.valueOf(args.get("kind").trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ToolResult.rejected("I can propose rules of type place_category, place_name or protect_path.");
        }
        UserRule candidate = UserRule.create(kind, args.get("match"), args.getOrDefault("folder", ""));
        UserRule clean;
        try {
            clean = memory.validate(candidate);
        } catch (IllegalArgumentException e) {
            return ToolResult.rejected(e.getMessage());
        }
        session.pendingRule = clean;
        return new ToolResult(true, "Proposed rule: " + clean.describe() + ". Confirm to save it.", null, null, clean);
    }
}
