package com.sortbot.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds the allow-list of folders SortBot may touch (F01). Paths are resolved to their real
 * location first, so "..", relative paths and symlinks cannot escape an authorized folder.
 */
public class FolderAuthorizer {
    private static final List<String> BLOCKED = List.of(
            "c:/windows", "c:/program files", "c:/program files (x86)", "c:/programdata",
            "/etc", "/usr", "/bin", "/sbin", "/lib", "/var", "/sys", "/proc", "/boot", "/dev",
            "/system", "/library", "/applications");

    private final Set<Path> allowed = new LinkedHashSet<>();
    private final Path storeFile;

    /** In-memory allow-list (used by tests). */
    public FolderAuthorizer() {
        this(null);
    }

    /** Allow-list persisted to {@code storeFile} (one path per line). */
    public FolderAuthorizer(Path storeFile) {
        this.storeFile = storeFile;
        load();
    }

    /** Adds a folder after checking it exists, is a real folder, is not a system location, and is read/writable. */
    public synchronized void add(Path path) {
        if (path == null) throw new FolderRejectedException("No folder was given.");
        Path abs = path.toAbsolutePath().normalize();
        if (!Files.exists(abs)) throw new FolderRejectedException("Folder does not exist: " + abs);
        if (!Files.isDirectory(abs)) throw new FolderRejectedException("That path is a file, not a folder: " + abs);
        Path real;
        try {
            real = abs.toRealPath();
        } catch (IOException e) {
            throw new FolderRejectedException("Cannot read folder: " + abs);
        }
        if (real.getParent() == null) throw new FolderRejectedException("A drive or filesystem root cannot be authorized.");
        if (isProtectedLocation(real)) throw new FolderRejectedException("System locations cannot be authorized: " + real);
        if (real.equals(homeDir())) throw new FolderRejectedException("Choose a folder inside your home folder (such as Downloads), not the home folder itself.");
        if (!Files.isReadable(real) || !Files.isWritable(real)) throw new FolderRejectedException("No read/write permission for: " + real);
        if (allowed.contains(real)) throw new FolderRejectedException("Folder is already on the list: " + real);
        allowed.add(real);
        save();
    }

    /** Removes a folder from the allow-list. Returns false if it was not on the list. */
    public synchronized boolean remove(Path path) {
        Path key = realOrAbsolute(path);
        boolean removed = allowed.remove(key);
        if (removed) save();
        return removed;
    }

    /** True if the path is inside (or equal to) an authorized folder. Works for paths that do not exist yet. */
    public synchronized boolean isAuthorized(Path path) {
        if (path == null) return false;
        Path resolved = resolveForCheck(path);
        for (Path root : allowed) {
            if (resolved.startsWith(root)) return true;
        }
        return false;
    }

    public synchronized List<Path> getAllowedFolders() {
        return new ArrayList<>(allowed);
    }

    private static Path homeDir() {
        try {
            return Path.of(System.getProperty("user.home")).toRealPath();
        } catch (IOException | RuntimeException e) {
            return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        }
    }

    private static boolean isProtectedLocation(Path real) {
        String s = real.toString().replace('\\', '/').toLowerCase();
        List<String> blocked = new ArrayList<>(BLOCKED);
        for (String env : new String[] {"SystemRoot", "ProgramFiles", "ProgramFiles(x86)", "ProgramData"}) {
            String v = System.getenv(env);
            if (v != null) blocked.add(v.replace('\\', '/').toLowerCase());
        }
        for (String b : blocked) {
            if (s.equals(b) || s.startsWith(b + "/")) return true;
        }
        return false;
    }

    private static Path realOrAbsolute(Path p) {
        try {
            return p.toAbsolutePath().normalize().toRealPath();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }

    /** Resolves the existing part of the path to its real location and re-attaches the rest. */
    private static Path resolveForCheck(Path path) {
        Path abs = path.toAbsolutePath().normalize();
        Deque<Path> rest = new ArrayDeque<>();
        Path existing = abs;
        while (existing != null && !Files.exists(existing)) {
            rest.push(existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) return abs;
        Path real;
        try {
            real = existing.toRealPath();
        } catch (IOException e) {
            return abs;
        }
        for (Path part : rest) real = real.resolve(part);
        return real.normalize();
    }

    private void load() {
        if (storeFile == null || !Files.isRegularFile(storeFile)) return;
        try {
            for (String line : Files.readAllLines(storeFile)) {
                if (line.isBlank()) continue;
                Path p = Path.of(line.trim());
                if (Files.isDirectory(p)) allowed.add(realOrAbsolute(p));
            }
        } catch (IOException | RuntimeException e) {
            // A damaged store starts empty; the user simply re-authorizes folders.
            allowed.clear();
        }
    }

    private void save() {
        if (storeFile == null) return;
        try {
            if (storeFile.getParent() != null) Files.createDirectories(storeFile.getParent());
            List<String> lines = new ArrayList<>();
            for (Path p : allowed) lines.add(p.toString());
            Files.write(storeFile, lines);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the allowed-folders list: " + e.getMessage(), e);
        }
    }
}
