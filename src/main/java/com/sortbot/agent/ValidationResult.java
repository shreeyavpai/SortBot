package com.sortbot.agent;

import java.util.List;

/** Outcome of checking a plan: what was wrong, and how many actions were removed. */
public record ValidationResult(List<String> problems, int removed) {
    public boolean isClean() { return problems.isEmpty(); }
}
