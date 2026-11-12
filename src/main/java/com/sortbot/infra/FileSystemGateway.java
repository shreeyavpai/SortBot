package com.sortbot.infra;

import com.sortbot.core.FolderAuthorizer;
import com.sortbot.core.NotAuthorizedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * The ONLY class that reads or changes user files. Every method checks FolderAuthorizer first.
 * There is deliberately no method that deletes a user file.
 */
public class FileSystemGateway {
    public static final String TO_BE_DELETED = "To_Be_Deleted";
    private static final Set<String> TEXT_EXTENSIONS =
            Set.of("txt", "md", "csv", "json", "xml", "html", "htm", "log", "java", "py", "js", "c", "cpp", "h", "sql", "yaml", "yml");

    private final FolderAuthorizer authorizer;

    public FileSystemGateway(FolderAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    /** Files found by list(), plus warnings for anything that could not be read. */
    public record Listing(List<Path> files, List<String> warnings) {}

    public Listing list(Path folder, boolean recursive) throws IOException {
        require(folder);
        List<Path> files = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Path root = folder.toAbsolutePath().normalize();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && (!recursive || dir.getFileName().toString().equals(TO_BE_DELETED))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) files.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                warnings.add("Could not read " + file + ": " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        return new Listing(files, warnings);
    }

    public BasicFileAttributes attributes(Path file) throws IOException {
        require(file);
        return Files.readAttributes(file, BasicFileAttributes.class);
    }

    public boolean exists(Path path) {
        require(path);
        return Files.exists(path);
    }

    /** Moves a file. If the destination exists, a numeric suffix is added. Returns the real destination. */
    public Path move(Path source, Path destination) throws IOException {
        require(source);
        require(destination);
        if (!Files.exists(source)) throw new NoSuchFileException(source.toString());
        Path src = source.toAbsolutePath().normalize();
        Path dst = destination.toAbsolutePath().normalize();
        if (src.equals(dst)) return dst;
        Path parent = dst.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path finalDst = uniqueTarget(dst);
        Files.move(src, finalDst);
        return finalDst;
    }

    /** Moves a file back to an exact location (used by undo). Fails if that location is occupied. */
    public void restore(Path from, Path to) throws IOException {
        require(from);
        require(to);
        if (!Files.exists(from)) throw new NoSuchFileException(from.toString());
        if (Files.exists(to)) throw new FileAlreadyExistsException(to.toString());
        Path parent = to.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.move(from, to);
    }

    /** Creates a folder. Returns true if it was created, false if it already existed. */
    public boolean createFolder(Path folder) throws IOException {
        require(folder);
        boolean existed = Files.isDirectory(folder);
        Files.createDirectories(folder);
        return !existed;
    }

    /** Removes a folder ONLY if it is empty. Returns false if it still has content. Never removes files. */
    public boolean removeEmptyFolder(Path folder) throws IOException {
        require(folder);
        if (!Files.isDirectory(folder)) return false;
        try (var s = Files.list(folder)) {
            if (s.findAny().isPresent()) return false;
        }
        Files.delete(folder);
        return true;
    }

    /** SHA-256 of the file content, as lowercase hex. */
    public String hash(Path file) throws IOException {
        require(file);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** First characters of a text file (empty for non-text files). Used to classify by content. */
    public String readSnippet(Path file, int maxChars) throws IOException {
        require(file);
        String ext = extensionOf(file.getFileName().toString());
        if (!TEXT_EXTENSIONS.contains(ext)) return "";
        StringBuilder sb = new StringBuilder();
        try (Reader r = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            char[] buf = new char[Math.max(1, Math.min(maxChars, 1024))];
            int n;
            while (sb.length() < maxChars && (n = r.read(buf)) > 0) {
                sb.append(buf, 0, Math.min(n, maxChars - sb.length()));
            }
        }
        return sb.toString();
    }

    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private void require(Path path) {
        if (!authorizer.isAuthorized(path)) throw new NotAuthorizedException(path);
    }

    private static Path uniqueTarget(Path dst) {
        if (!Files.exists(dst)) return dst;
        String name = dst.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            Path candidate = dst.resolveSibling(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) return candidate;
        }
    }
}
