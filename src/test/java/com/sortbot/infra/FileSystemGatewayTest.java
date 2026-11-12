package com.sortbot.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.TestFiles;
import com.sortbot.core.FolderAuthorizer;
import com.sortbot.core.NotAuthorizedException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemGatewayTest {
    @TempDir Path tmp;
    Path allowed;
    Path outside;
    FileSystemGateway gw;

    @BeforeEach
    void setUp() throws IOException {
        allowed = Files.createDirectory(tmp.resolve("allowed"));
        outside = Files.createDirectory(tmp.resolve("outside"));
        FolderAuthorizer a = new FolderAuthorizer();
        a.add(allowed);
        gw = new FileSystemGateway(a);
    }

    @Test
    void everyOperationRejectsUnauthorizedPaths() throws IOException {
        Path f = TestFiles.write(outside, "a.txt", "x");
        assertThrows(NotAuthorizedException.class, () -> gw.list(outside, false));
        assertThrows(NotAuthorizedException.class, () -> gw.attributes(f));
        assertThrows(NotAuthorizedException.class, () -> gw.hash(f));
        assertThrows(NotAuthorizedException.class, () -> gw.readSnippet(f, 10));
        assertThrows(NotAuthorizedException.class, () -> gw.createFolder(outside.resolve("new")));
        assertThrows(NotAuthorizedException.class, () -> gw.move(f, allowed.resolve("a.txt")));
        assertThrows(NotAuthorizedException.class, () -> gw.move(TestFiles.write(allowed, "b.txt", "x"), outside.resolve("b.txt")));
        assertTrue(Files.exists(f), "the unauthorized file must be untouched");
    }

    @Test
    void thereIsNoMethodThatDeletesUserFiles() {
        for (Method m : FileSystemGateway.class.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            assertFalse(n.contains("delete") || n.equals("remove"), "unexpected method: " + m.getName());
        }
    }

    @Test
    void listSkipsSubfoldersUnlessRecursiveAndSkipsToBeDeleted() throws IOException {
        TestFiles.write(allowed, "a.txt", "1");
        TestFiles.write(allowed, "sub/b.txt", "2");
        TestFiles.write(allowed, FileSystemGateway.TO_BE_DELETED + "/c.txt", "3");
        assertEquals(1, gw.list(allowed, false).files().size());
        assertEquals(2, gw.list(allowed, true).files().size());
    }

    @Test
    void moveNeverOverwritesAndAddsASuffix() throws IOException {
        Path src = TestFiles.write(allowed, "a.txt", "new");
        Path existing = TestFiles.write(allowed, "dest/a.txt", "old");
        Path result = gw.move(src, existing);
        assertEquals("a (1).txt", result.getFileName().toString());
        assertEquals("old", Files.readString(existing));
        assertEquals("new", Files.readString(result));
        assertFalse(Files.exists(src));
    }

    @Test
    void moveCreatesMissingParentFolderInsideAuthorizedArea() throws IOException {
        Path src = TestFiles.write(allowed, "a.txt", "x");
        Path result = gw.move(src, allowed.resolve("New/Deep/a.txt"));
        assertTrue(Files.exists(result));
    }

    @Test
    void moveOfMissingSourceFails() {
        assertThrows(NoSuchFileException.class, () -> gw.move(allowed.resolve("ghost.txt"), allowed.resolve("x.txt")));
    }

    @Test
    void moveToSamePlaceIsANoOp() throws IOException {
        Path src = TestFiles.write(allowed, "a.txt", "x");
        assertEquals(src.toAbsolutePath().normalize(), gw.move(src, src));
        assertTrue(Files.exists(src));
    }

    @Test
    void restoreRefusesToOverwrite() throws IOException {
        Path moved = TestFiles.write(allowed, "dest/a.txt", "x");
        Path original = TestFiles.write(allowed, "a.txt", "other");
        assertThrows(FileAlreadyExistsException.class, () -> gw.restore(moved, original));
        assertEquals("other", Files.readString(original));
    }

    @Test
    void createFolderReportsWhetherItWasCreated() throws IOException {
        Path f = allowed.resolve("Receipts");
        assertTrue(gw.createFolder(f));
        assertFalse(gw.createFolder(f));
    }

    @Test
    void removeEmptyFolderKeepsFolderWithContent() throws IOException {
        Path empty = Files.createDirectory(allowed.resolve("empty"));
        Path full = Files.createDirectory(allowed.resolve("full"));
        TestFiles.write(full, "keep.txt", "x");
        assertTrue(gw.removeEmptyFolder(empty));
        assertFalse(Files.exists(empty));
        assertFalse(gw.removeEmptyFolder(full));
        assertTrue(Files.exists(full.resolve("keep.txt")));
    }

    @Test
    void hashIsEqualForSameContentAndDifferentOtherwise() throws IOException {
        Path a = TestFiles.write(allowed, "a.txt", "same");
        Path b = TestFiles.write(allowed, "b.txt", "same");
        Path c = TestFiles.write(allowed, "c.txt", "different");
        assertEquals(gw.hash(a), gw.hash(b));
        assertNotEquals(gw.hash(a), gw.hash(c));
        assertEquals(64, gw.hash(a).length());
    }

    @Test
    void snippetIsLimitedAndEmptyForNonTextFiles() throws IOException {
        Path text = TestFiles.write(allowed, "notes.txt", "x".repeat(2000));
        Path bin = TestFiles.write(allowed, "photo.jpg", "binarydata");
        assertEquals(300, gw.readSnippet(text, 300).length());
        assertEquals("", gw.readSnippet(bin, 300));
    }

    @Test
    void extensionOfHandlesEdgeCases() {
        assertEquals("pdf", FileSystemGateway.extensionOf("Report.PDF"));
        assertEquals("", FileSystemGateway.extensionOf("README"));
        assertEquals("", FileSystemGateway.extensionOf(".gitignore"));
        assertEquals("", FileSystemGateway.extensionOf("trailingdot."));
    }
}
