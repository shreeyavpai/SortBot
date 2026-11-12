package com.sortbot.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** One line of the action log: enough information to undo the action later, even after a restart. */
public class ActionEntry {
    private final String runId;
    private final int seq;
    private final String type;
    private final String source;
    private final String target;
    private final boolean created;
    private ActionStatus status;
    private final String message;
    private final Instant timestamp;

    public ActionEntry(String runId, int seq, String type, String source, String target,
                       boolean created, ActionStatus status, String message, Instant timestamp) {
        this.runId = runId;
        this.seq = seq;
        this.type = type;
        this.source = source == null ? "" : source;
        this.target = target == null ? "" : target;
        this.created = created;
        this.status = status;
        this.message = message == null ? "" : message;
        this.timestamp = timestamp;
    }

    public String getRunId() { return runId; }
    public int getSeq() { return seq; }
    public String getType() { return type; }
    public String getSource() { return source; }
    public String getTarget() { return target; }
    public boolean isCreated() { return created; }
    public ActionStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }

    void setStatus(ActionStatus status) { this.status = status; }

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("runId", runId);
        m.put("seq", Integer.toString(seq));
        m.put("type", type);
        m.put("source", source);
        m.put("target", target);
        m.put("created", Boolean.toString(created));
        m.put("status", status.name());
        m.put("message", message);
        m.put("timestamp", timestamp.toString());
        return m;
    }

    public static ActionEntry fromMap(Map<String, String> m) {
        return new ActionEntry(
                m.get("runId"), Integer.parseInt(m.get("seq")), m.get("type"), m.get("source"), m.get("target"),
                Boolean.parseBoolean(m.get("created")), ActionStatus.valueOf(m.get("status")),
                m.get("message"), Instant.parse(m.get("timestamp")));
    }
}
