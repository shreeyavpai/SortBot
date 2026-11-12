package com.sortbot.domain;

import com.sortbot.infra.FileSystemGateway;

import java.io.IOException;
import java.nio.file.Path;

/** Concrete command: move one file. Undo moves it back to where it came from. */
public class MoveFileAction extends BaseFileAction {
    private final FileSystemGateway gateway;
    private final Path source;
    private Path target;
    private Path actualTarget;

    public MoveFileAction(FileSystemGateway gateway, Path source, Path target) {
        this.gateway = gateway;
        this.source = source;
        this.target = target;
    }

    /** Rebuilds an already-executed action from the log so it can be undone. */
    static MoveFileAction restored(FileSystemGateway gateway, Path source, Path actualTarget) {
        MoveFileAction a = new MoveFileAction(gateway, source, actualTarget);
        a.actualTarget = actualTarget;
        a.executed = true;
        return a;
    }

    public void setTarget(Path newTarget) {
        requireNotExecuted();
        this.target = newTarget;
    }

    @Override public String type() { return "move"; }
    @Override public Path source() { return source; }
    @Override public Path target() { return target; }
    @Override public Path resultTarget() { return actualTarget != null ? actualTarget : target; }
    @Override public String describe() { return "Move " + source.getFileName() + " -> " + target; }

    @Override
    public void execute() throws IOException {
        requireNotExecuted();
        actualTarget = gateway.move(source, target);
        executed = true;
    }

    @Override
    public void undo() throws IOException {
        if (!executed) throw new IllegalStateException("Cannot undo an action that was not executed.");
        gateway.restore(actualTarget, source);
        executed = false;
    }
}
