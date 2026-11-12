package com.sortbot.core;

import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;
import com.sortbot.infra.FileSystemGateway;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;

/** Walks an authorized folder and builds an Inventory (F02). Publishes progress to listeners. */
public class FileScanner extends ProgressPublisher {
    private final FileSystemGateway gateway;

    public FileScanner(FileSystemGateway gateway) {
        this.gateway = gateway;
    }

    public Inventory scan(Path folder, boolean includeSubfolders) throws IOException {
        FileSystemGateway.Listing listing = gateway.list(folder, includeSubfolders);
        Inventory inv = new Inventory(folder.toAbsolutePath().normalize(), Instant.now());
        listing.warnings().forEach(inv::addWarning);
        List<Path> files = listing.files();
        int total = files.size();
        int i = 0;
        for (Path p : files) {
            i++;
            try {
                BasicFileAttributes a = gateway.attributes(p);
                inv.add(new FileRecord(p, a.size(), a.lastModifiedTime().toInstant()));
            } catch (IOException e) {
                inv.addWarning("Skipped " + p.getFileName() + ": " + e.getMessage());
            }
            publish(new ProgressEvent("scan", i, total, p.getFileName().toString()));
        }
        return inv;
    }
}
