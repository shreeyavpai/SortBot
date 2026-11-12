package com.sortbot.domain;

import java.util.List;

/** Summary of one execution run. */
public record RunResult(String runId, int done, int failed, int skipped, boolean cancelled, List<String> messages) {
    public String summary() {
        return done + " done, " + failed + " failed, " + skipped + " skipped" + (cancelled ? " (cancelled)" : "");
    }
}
