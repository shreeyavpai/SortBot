package com.sortbot.domain;

import java.io.IOException;
import java.nio.file.Path;

/** Command pattern: one reversible file operation. */
public interface FileAction {
    String id();

    /** "move" or "createFolder". */
    String type();

    Path source();

    /** The planned target (before execution) . */
    Path target();

    /** The real target after execution (may differ if a name collision was resolved). */
    default Path resultTarget() { return target(); }

    /** True if executing this action created a new folder. */
    default boolean created() { return false; }

    String describe();

    void execute() throws IOException;

    void undo() throws IOException;
}
