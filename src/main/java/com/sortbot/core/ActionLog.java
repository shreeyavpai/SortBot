package com.sortbot.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Permanent record of every action (F11). Stored as JSON Lines so it survives restarts and supports undo. */
public class ActionLog {
    private final Path file;
    private final List<ActionEntry> entries = new ArrayList<>();
    private int damagedLines;

    /** In-memory log (used by tests). */
    public ActionLog() {
        this(null);
    }

    public ActionLog(Path file) {
        this.file = file;
        load();
    }

    public synchronized String newRunId() {
        return "run-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public synchronized void append(ActionEntry entry) {
        entries.add(entry);
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, FlatJson.toJson(entry.toMap()) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the action log: " + e.getMessage(), e);
        }
    }

    public synchronized List<ActionEntry> query(LogFilter filter) {
        List<ActionEntry> out = new ArrayList<>();
        for (ActionEntry e : entries) if (filter.matches(e)) out.add(e);
        return out;
    }

    /** Marks an entry as undone and rewrites the log file. */
    public synchronized void markUndone(ActionEntry entry) {
        entry.setStatus(ActionStatus.UNDONE);
        rewrite();
    }

    /** Run id of the most recent run that still has undoable actions, or null. */
    public synchronized String lastUndoableRunId() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).getStatus() == ActionStatus.DONE) return entries.get(i).getRunId();
        }
        return null;
    }

    /** Number of unreadable lines skipped when the log was loaded (damaged file). */
    public synchronized int getDamagedLineCount() {
        return damagedLines;
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) continue;
                try {
                    entries.add(ActionEntry.fromMap(FlatJson.parse(line)));
                } catch (RuntimeException e) {
                    damagedLines++;
                }
            }
        } catch (IOException e) {
            damagedLines++;
        }
    }

    private void rewrite() {
        if (file == null) return;
        try {
            List<String> lines = new ArrayList<>();
            for (ActionEntry e : entries) lines.add(FlatJson.toJson(e.toMap()));
            Files.write(file, lines);
        } catch (IOException e) {
            throw new IllegalStateException("Could not update the action log: " + e.getMessage(), e);
        }
    }
}
