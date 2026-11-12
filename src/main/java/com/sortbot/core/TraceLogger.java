package com.sortbot.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records every tool call and file change as JSON Lines. Used later as evidence when the agent's
 * behavior is tested. Logging problems never crash the application.
 */
public class TraceLogger {
    private final Path file;
    private final List<Map<String, String>> events = new ArrayList<>();

    public TraceLogger() {
        this(null);
    }

    public TraceLogger(Path file) {
        this.file = file;
    }

    public synchronized void log(String event, Map<String, String> data) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("time", Instant.now().toString());
        m.put("event", event);
        if (data != null) m.putAll(data);
        events.add(m);
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, FlatJson.toJson(m) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Tracing is best-effort.
        }
    }

    public void toolCall(String tool, String args, String result) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("tool", tool);
        d.put("args", args);
        d.put("result", result);
        log("tool_call", d);
    }

    public void fileChange(String type, String source, String target, String status) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("type", type);
        d.put("source", source);
        d.put("target", target);
        d.put("status", status);
        log("file_change", d);
    }

    public synchronized List<Map<String, String>> events() {
        return new ArrayList<>(events);
    }
}
