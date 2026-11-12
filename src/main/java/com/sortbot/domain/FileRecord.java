package com.sortbot.domain;

import java.nio.file.Path;
import java.time.Instant;

/** One scanned file: facts from the disk plus the category assigned later. */
public class FileRecord {
    private final Path path;
    private final String name;
    private final String extension;
    private final long size;
    private final Instant modified;
    private Category category = Category.UNCLASSIFIED;
    private double confidence;
    private String reason = "";

    public FileRecord(Path path, long size, Instant modified) {
        this.path = path;
        this.name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        this.extension = dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase();
        this.size = size;
        this.modified = modified;
    }

    public Path getPath() { return path; }
    public String getName() { return name; }
    public String getExtension() { return extension; }
    public long getSize() { return size; }
    public Instant getModified() { return modified; }
    public Category getCategory() { return category; }
    public double getConfidence() { return confidence; }
    public String getReason() { return reason; }

    public void classify(Category category, double confidence, String reason) {
        this.category = category == null ? Category.UNCLASSIFIED : category;
        this.confidence = Math.max(0, Math.min(1, confidence));
        this.reason = reason == null ? "" : reason;
    }

    @Override
    public String toString() {
        return name + " [" + category + "]";
    }
}
