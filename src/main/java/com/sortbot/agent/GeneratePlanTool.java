package com.sortbot.agent;

import com.sortbot.domain.Plan;

import java.util.List;
import java.util.Map;

/** Builds a plan from the user's instruction. The plan waits for approval; nothing is moved. */
public class GeneratePlanTool implements Tool {
    private final AgentSession session;
    private final Planner planner;

    public GeneratePlanTool(AgentSession session, Planner planner) {
        this.session = session;
        this.planner = planner;
    }

    @Override public String name() { return "generate_plan"; }

    @Override
    public String description() {
        return "Create a plan that organizes the scanned files. Required arg: instruction (what the user wants, in plain words).";
    }

    @Override public List<String> requiredArgs() { return List.of("instruction"); }

    @Override
    public ToolResult run(Map<String, String> args) {
        if (session.inventory == null) return ToolResult.rejected("Scan a folder first.");
        Plan plan = planner.createPlan(session.inventory, args.get("instruction"));
        session.pendingPlan = plan;
        String msg = plan.size() == 0 ? "I didn't find anything to move for that request."
                : "I prepared a plan with " + plan.size() + " action(s). Review it before anything changes.";
        if (!plan.getWarnings().isEmpty()) msg += " (" + plan.getWarnings().size() + " note(s) about the plan.)";
        return new ToolResult(true, msg, plan, null, null);
    }
}
