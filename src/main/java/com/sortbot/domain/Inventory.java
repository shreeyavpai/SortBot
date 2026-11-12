package com.sortbot.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** The result of scanning one folder: the files found and any warnings. */
public class Inventory {
    private final Path root;
    private final Instant scannedAt;
    private final List<FileRecord> records = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public Inventory(Path root, Instant scannedAt) {
        this.root = root;
        this.scannedAt = scannedAt;
    }

    public void add(FileRecord record) { records.add(record); }
    public void addWarning(String warning) { warnings.add(warning); }

    public Path getRoot() { return root; }
    public Instant getScannedAt() { return scannedAt; }
    public List<FileRecord> getRecords() { return Collections.unmodifiableList(records); }
    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }
    public int size() { return records.size(); }
    public boolean isEmpty() { return records.isEmpty(); }

    public List<FileRecord> filter(Predicate<FileRecord> criteria) {
        List<FileRecord> out = new ArrayList<>();
        for (FileRecord r : records) if (criteria.test(r)) out.add(r);
        return out;
    }
}
