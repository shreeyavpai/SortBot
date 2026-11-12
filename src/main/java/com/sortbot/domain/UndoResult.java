package com.sortbot.domain;

import java.util.List;

/** Summary of an undo. Skipped actions are listed in messages with the reason. */
public record UndoResult(int undone, int skipped, List<String> messages) {
    public boolean nothingToUndo() { return undone == 0 && skipped == 0; }
    public String summary() { return undone + " undone, " + skipped + " skipped"; }
}
