package com.sortbot.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.TestFiles;
import com.sortbot.domain.Inventory;
import com.sortbot.infra.FileSystemGateway;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileScannerTest {
    @TempDir Path tmp;
    Path root;
    FileScanner scanner;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectory(tmp.resolve("Downloads"));
        FolderAuthorizer a = new FolderAuthorizer();
        a.add(root);
        scanner = new FileScanner(new FileSystemGateway(a));
    }

    @Test
    void buildsInventoryWithNamesExtensionsAndSizes() throws IOException {
        TestFiles.write(root, "Lecture 01.PDF", "12345");
        TestFiles.write(root, "notes", "x");
        Inventory inv = scanner.scan(root, false);
        assertEquals(2, inv.size());
        var pdf = inv.filter(r -> r.getName().equals("Lecture 01.PDF")).get(0);
        assertEquals("pdf", pdf.getExtension());
        assertEquals(5, pdf.getSize());
        assertEquals("", inv.filter(r -> r.getName().equals("notes")).get(0).getExtension());
    }

    @Test
    void subfoldersOnlyWhenRequestedAndToBeDeletedIsSkipped() throws IOException {
        TestFiles.write(root, "a.txt", "1");
        TestFiles.write(root, "sub/b.txt", "2");
        TestFiles.write(root, "To_Be_Deleted/c.txt", "3");
        assertEquals(1, scanner.scan(root, false).size());
        assertEquals(2, scanner.scan(root, true).size());
    }

    @Test
    void emptyFolderGivesEmptyInventory() throws IOException {
        Inventory inv = scanner.scan(root, true);
        assertTrue(inv.isEmpty());
    }

    @Test
    void publishesOneProgressEventPerFile() throws IOException {
        TestFiles.write(root, "a.txt", "1");
        TestFiles.write(root, "b.txt", "2");
        List<ProgressEvent> events = new ArrayList<>();
        scanner.subscribe(events::add);
        scanner.scan(root, false);
        assertEquals(2, events.size());
        assertEquals(2, events.get(1).current());
        assertEquals(2, events.get(1).total());
    }

    @Test
    void unauthorizedFolderIsRejected() throws IOException {
        Path other = Files.createDirectory(tmp.resolve("Other"));
        assertThrows(NotAuthorizedException.class, () -> scanner.scan(other, false));
    }

    @Test
    void filesModifiedDuringScanningDoNotCrashIt() throws IOException {
        TestFiles.write(root, "a.txt", "1");
        scanner.subscribe(e -> {
            try {
                Files.deleteIfExists(root.resolve("a.txt"));
            } catch (IOException ignored) {
                // the test only needs the file gone mid-scan
            }
        });
        Inventory inv = scanner.scan(root, false);
        assertTrue(inv.size() <= 1);
    }
}
