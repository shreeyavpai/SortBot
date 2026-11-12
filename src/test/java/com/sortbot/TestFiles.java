package com.sortbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.nio.file.attribute.FileTime;

/** Small helpers for creating test files. */
public final class TestFiles {
    private TestFiles() {}

    public static Path write(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        return p;
    }

    public static void setModified(Path file, Instant when) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(when));
    }
}
