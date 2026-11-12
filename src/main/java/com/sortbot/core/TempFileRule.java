package com.sortbot.core;

import com.sortbot.domain.FileRecord;

import java.util.Set;

/** Flags temporary and partial-download files. */
public class TempFileRule implements CleanupRule {
    private static final Set<String> EXTENSIONS = Set.of("tmp", "temp", "bak", "crdownload", "part", "partial");

    @Override public String name() { return "temp-file"; }

    @Override
    public boolean matches(FileRecord r) {
        return EXTENSIONS.contains(r.getExtension()) || r.getName().startsWith("~$");
    }

    @Override
    public String reason(FileRecord r) {
        return "Temporary or partial-download file";
    }
}
