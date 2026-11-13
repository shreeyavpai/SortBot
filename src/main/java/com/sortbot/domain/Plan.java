package com.sortbot.domain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An ordered list of proposed actions. Nothing runs until approve() has been called. */
public class Plan {
    private final List<FileAction> actions = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private boolean approved;

    public void add(FileAction action) {
        ensureEditable();
        actions.add(action);
    }

    public List<FileAction> getActions() { return Collections.unmodifiableList(actions); }
    public int size() { return actions.size(); }
    public boolean isApproved() { return approved; }
    public void addWarning(String warning) { warnings.add(warning); }
    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }

    public FileAction find(String actionId) {
        for (FileAction a : actions) if (a.id().equals(actionId)) return a;
        return null;
    }

    /** Removes one action (the user rejected that move). Returns false if the id is unknown. */
    public boolean remove(String actionId) {
        ensureEditable();
        FileAction a = find(actionId);
        return a != null && actions.remove(a);
    }

    /** Changes where one move will put its file. */
    public void edit(String actionId, Path newTarget) {
        ensureEditable();
        FileAction a = find(actionId);
        if (a == null) throw new IllegalArgumentException("No such action: " + actionId);
        if (!(a instanceof MoveFileAction move)) throw new IllegalArgumentException("Only moves can be edited.");
        move.setTarget(newTarget);
    }

    /** Renames a target folder everywhere in the plan (for example Receipts -> Finance/Receipts). */
    public void renameFolder(Path oldFolder, Path newFolder) {
        ensureEditable();
        for (FileAction a : actions) {
            if (a instanceof CreateFolderAction c && c.target().startsWith(oldFolder)) {
                c.setFolder(newFolder.resolve(oldFolder.relativize(c.target())));
            } else if (a instanceof MoveFileAction m && m.target().startsWith(oldFolder)) {
                m.setTarget(newFolder.resolve(oldFolder.relativize(m.target())));
            }
        }
    }

    /** Locks the plan. Folders that no move uses any more are dropped first. */
    public void approve() {
        actions.removeIf(a -> a instanceof CreateFolderAction c && !isUsed(c.target()));
        approved = true;
    }

    private boolean isUsed(Path folder) {
        for (FileAction a : actions) {
            if (a instanceof MoveFileAction m && m.target().startsWith(folder)) return true;
        }
        return false;
    }

    private void ensureEditable() {
        if (approved) throw new IllegalStateException("The plan is approved and locked.");
    }
}
