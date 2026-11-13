package com.sortbot.agent;

import com.sortbot.core.CleanupCandidate;
import com.sortbot.domain.Plan;
import com.sortbot.domain.UserRule;

import java.util.List;

/** What a tool produced. Tools only ever produce text, plans, lists or proposals; they never change files. */
public record ToolResult(boolean ok, String message, Plan plan, List<CleanupCandidate> cleanup, UserRule proposedRule) {
    public static ToolResult ok(String message) { return new ToolResult(true, message, null, null, null); }
    public static ToolResult rejected(String message) { return new ToolResult(false, message, null, null, null); }
}
