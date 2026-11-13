package com.sortbot.agent;

import com.sortbot.core.CleanupCandidate;
import com.sortbot.domain.Inventory;
import com.sortbot.domain.Plan;
import com.sortbot.domain.UserRule;

import java.util.List;

/** What the agent currently knows: the scanned folder and anything waiting for the user's approval. */
public class AgentSession {
    public Inventory inventory;
    public Plan pendingPlan;
    public List<CleanupCandidate> pendingCleanup;
    public UserRule pendingRule;

    public void clearPending() {
        pendingPlan = null;
        pendingCleanup = null;
        pendingRule = null;
    }
}
