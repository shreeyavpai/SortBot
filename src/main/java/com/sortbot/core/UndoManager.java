package com.sortbot.core;

import com.sortbot.domain.FileAction;
import com.sortbot.domain.FileActions;
import com.sortbot.domain.UndoResult;
import com.sortbot.infra.FileSystemGateway;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reverses logged actions in last-in first-out order (F07). */
public class UndoManager {
    private final ActionLog log;
    private final FileSystemGateway gateway;
    private final TraceLogger trace;

    public UndoManager(ActionLog log, FileSystemGateway gateway, TraceLogger trace) {
        this.log = log;
        this.gateway = gateway;
        this.trace = trace;
    }

    public UndoResult undoRun(String runId) {
        List<ActionEntry> entries = new ArrayList<>(log.query(LogFilter.all().runId(runId).status(ActionStatus.DONE)));
        entries.sort(Comparator.comparingInt(ActionEntry::getSeq).reversed());
        return undoEntries(entries);
    }

    /** Undoes only the most recent action that is still undoable. */
    public UndoResult undoLast() {
        List<ActionEntry> done = log.query(LogFilter.all().status(ActionStatus.DONE));
        if (done.isEmpty()) return new UndoResult(0, 0, List.of());
        return undoEntries(List.of(done.get(done.size() - 1)));
    }

    private UndoResult undoEntries(List<ActionEntry> entries) {
        int undone = 0, skipped = 0;
        List<String> messages = new ArrayList<>();
        for (ActionEntry e : entries) {
            try {
                FileAction action = FileActions.fromEntry(e, gateway);
                action.undo();
                log.markUndone(e);
                undone++;
                trace.fileChange("undo_" + e.getType(), e.getTarget(), e.getSource(), "UNDONE");
            } catch (IOException | RuntimeException ex) {
                skipped++;
                messages.add("Could not undo " + e.getType() + " " + e.getTarget() + ": " + ex.getMessage());
            }
        }
        return new UndoResult(undone, skipped, messages);
    }
}
