package com.sortbot.agent;

import com.sortbot.core.CleanupCandidate;
import com.sortbot.core.CleanupDetector;

import java.util.List;
import java.util.Map;

/** Finds old, duplicate and temporary files. They are only listed; moving them needs the user's confirmation. */
public class FindCleanupTool implements Tool {
    private final AgentSession session;
    private final CleanupDetector detector;

    public FindCleanupTool(AgentSession session, CleanupDetector detector) {
        this.session = session;
        this.detector = detector;
    }

    @Override public String name() { return "find_cleanup"; }

    @Override
    public String description() {
        return "Find old, duplicate or temporary files that could be moved to the To_Be_Deleted folder. No args.";
    }

    @Override
    public ToolResult run(Map<String, String> args) {
        if (session.inventory == null) return ToolResult.rejected("Scan a folder first.");
        List<CleanupCandidate> found = detector.detect(session.inventory);
        session.pendingCleanup = found;
        String msg = found.isEmpty() ? "Nothing looks like clutter." : "I found " + found.size() + " cleanup candidate(s). Nothing has been moved; review and confirm first.";
        return new ToolResult(true, msg, null, found, null);
    }
}
