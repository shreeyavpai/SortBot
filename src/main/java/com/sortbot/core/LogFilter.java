package com.sortbot.core;

import java.time.Instant;

/** Filter for ActionLog.query(). Leave a field null to ignore it. */
public class LogFilter {
    private String runId;
    private String type;
    private ActionStatus status;
    private Instant from;
    private Instant to;
    private String pathContains;

    public static LogFilter all() { return new LogFilter(); }

    public LogFilter runId(String v) { this.runId = v; return this; }
    public LogFilter type(String v) { this.type = v; return this; }
    public LogFilter status(ActionStatus v) { this.status = v; return this; }
    public LogFilter from(Instant v) { this.from = v; return this; }
    public LogFilter to(Instant v) { this.to = v; return this; }
    public LogFilter pathContains(String v) { this.pathContains = v; return this; }

    boolean matches(ActionEntry e) {
        if (runId != null && !runId.equals(e.getRunId())) return false;
        if (type != null && !type.equals(e.getType())) return false;
        if (status != null && status != e.getStatus()) return false;
        if (from != null && e.getTimestamp().isBefore(from)) return false;
        if (to != null && e.getTimestamp().isAfter(to)) return false;
        if (pathContains != null) {
            String p = pathContains.toLowerCase();
            if (!e.getSource().toLowerCase().contains(p) && !e.getTarget().toLowerCase().contains(p)) return false;
        }
        return true;
    }
}
