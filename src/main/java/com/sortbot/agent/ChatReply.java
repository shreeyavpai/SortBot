package com.sortbot.agent;

import com.sortbot.core.CleanupCandidate;
import com.sortbot.domain.Plan;
import com.sortbot.domain.UserRule;

import java.util.List;

/** The agent's answer to one chat message, plus anything the user still has to review or confirm. */
public record ChatReply(String text, Plan plan, List<CleanupCandidate> cleanup, UserRule proposedRule) {
    public static ChatReply text(String text) { return new ChatReply(text, null, null, null); }
}
