package com.sortbot.domain;

import com.sortbot.infra.FileSystemGateway;

import java.io.IOException;
import java.nio.file.Path;

/** Concrete command: create a folder. Undo removes it only if SortBot created it and it is still empty. */
public class CreateFolderAction extends BaseFileAction {
    private final FileSystemGateway gateway;
    private Path folder;
    private boolean created;

    public CreateFolderAction(FileSystemGateway gateway, Path folder) {
        this.gateway = gateway;
        this.folder = folder;
    }

    static CreateFolderAction restored(FileSystemGateway gateway, Path folder, boolean created) {
        CreateFolderAction a = new CreateFolderAction(gateway, folder);
        a.created = created;
        a.executed = true;
        return a;
    }

    public void setFolder(Path newFolder) {
        requireNotExecuted();
        this.folder = newFolder;
    }

    @Override public String type() { return "createFolder"; }
    @Override public Path source() { return null; }
    @Override public Path target() { return folder; }
    @Override public boolean created() { return created; }
    @Override public String describe() { return "Create folder " + folder; }

    @Override
    public void execute() throws IOException {
        requireNotExecuted();
        created = gateway.createFolder(folder);
        executed = true;
    }

    @Override
    public void undo() throws IOException {
        if (!executed) throw new IllegalStateException("Cannot undo an action that was not executed.");
        if (created) gateway.removeEmptyFolder(folder);
        executed = false;
    }
}
