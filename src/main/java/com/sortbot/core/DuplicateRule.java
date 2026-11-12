package com.sortbot.core;

import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;
import com.sortbot.infra.FileSystemGateway;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Flags exact duplicates (same SHA-256). The oldest copy in each group is kept as the original. */
public class DuplicateRule implements CleanupRule {
    private final FileSystemGateway gateway;
    private final Map<Path, Path> duplicateToOriginal = new HashMap<>();

    public DuplicateRule(FileSystemGateway gateway) {
        this.gateway = gateway;
    }

    @Override public String name() { return "duplicate"; }

    @Override
    public void prepare(Inventory inventory) {
        duplicateToOriginal.clear();
        Map<Long, List<FileRecord>> bySize = new HashMap<>();
        for (FileRecord r : inventory.getRecords()) {
            if (r.getSize() > 0) bySize.computeIfAbsent(r.getSize(), k -> new ArrayList<>()).add(r);
        }
        for (List<FileRecord> sameSize : bySize.values()) {
            if (sameSize.size() < 2) continue;
            Map<String, List<FileRecord>> byHash = new HashMap<>();
            for (FileRecord r : sameSize) {
                try {
                    byHash.computeIfAbsent(gateway.hash(r.getPath()), k -> new ArrayList<>()).add(r);
                } catch (IOException | RuntimeException e) {
                    inventory.addWarning("Could not compare " + r.getName() + ": " + e.getMessage());
                }
            }
            for (List<FileRecord> group : byHash.values()) {
                if (group.size() < 2) continue;
                group.sort(Comparator.comparing(FileRecord::getModified).thenComparing(r -> r.getPath().toString()));
                Path original = group.get(0).getPath();
                for (int i = 1; i < group.size(); i++) duplicateToOriginal.put(group.get(i).getPath(), original);
            }
        }
    }

    @Override
    public boolean matches(FileRecord r) {
        return duplicateToOriginal.containsKey(r.getPath());
    }

    @Override
    public String reason(FileRecord r) {
        Path original = duplicateToOriginal.get(r.getPath());
        return "Exact duplicate of " + (original == null ? "another file" : original.getFileName());
    }
}
