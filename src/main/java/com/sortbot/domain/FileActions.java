package com.sortbot.domain;

import com.sortbot.core.ActionEntry;
import com.sortbot.infra.FileSystemGateway;

import java.nio.file.Path;

/** Rebuilds executed FileActions from log entries so undo works even after a restart. */
public final class FileActions {
    private FileActions() {}

    public static FileAction fromEntry(ActionEntry e, FileSystemGateway gateway) {
        return switch (e.getType()) {
            case "move" -> MoveFileAction.restored(gateway, Path.of(e.getSource()), Path.of(e.getTarget()));
            case "createFolder" -> CreateFolderAction.restored(gateway, Path.of(e.getTarget()), e.isCreated());
            default -> throw new IllegalArgumentException("Unknown action type: " + e.getType());
        };
    }
}
