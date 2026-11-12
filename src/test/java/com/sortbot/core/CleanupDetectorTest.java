package com.sortbot.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.TestFiles;
import com.sortbot.domain.Inventory;
import com.sortbot.domain.Plan;
import com.sortbot.infra.FileSystemGateway;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CleanupDetectorTest {
    static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir Path tmp;
    Path root;
    FileSystemGateway gw;
    FileScanner scanner;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectory(tmp.resolve("Downloads"));
        FolderAuthorizer a = new FolderAuthorizer();
        a.add(root);
        gw = new FileSystemGateway(a);
        scanner = new FileScanner(gw);
    }

    private CleanupDetector detector() {
        return new CleanupDetector(List.of(new AgeRule(12, CLOCK), new DuplicateRule(gw), new TempFileRule()));
    }

    @Test
    void oldFilesAreFlaggedAndRecentOnesAreNot() throws IOException {
        Path old = TestFiles.write(root, "old.txt", "a");
        Path fresh = TestFiles.write(root, "fresh.txt", "b");
        TestFiles.setModified(old, NOW.minusSeconds(60L * 60 * 24 * 800));
        TestFiles.setModified(fresh, NOW.minusSeconds(60L * 60 * 24 * 10));
        var found = detector().detect(scanner.scan(root, false));
        assertEquals(1, found.size());
        assertEquals("old.txt", found.get(0).record().getName());
        assertTrue(found.get(0).reasons().get(0).contains("12 months"));
    }

    @Test
    void ageRuleRejectsAnInvalidThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new AgeRule(0));
    }

    @Test
    void exactDuplicatesAreFlaggedButTheOldestCopyIsKept() throws IOException {
        Path original = TestFiles.write(root, "lec01.pdf", "same content");
        Path copy = TestFiles.write(root, "lec01 (3).pdf", "same content");
        Path other = TestFiles.write(root, "other.pdf", "diff content!");
        TestFiles.setModified(original, NOW.minusSeconds(3000));
        TestFiles.setModified(copy, NOW.minusSeconds(1000));
        TestFiles.setModified(other, NOW.minusSeconds(1000));
        var found = new CleanupDetector(List.of(new DuplicateRule(gw))).detect(scanner.scan(root, false));
        assertEquals(1, found.size());
        assertEquals("lec01 (3).pdf", found.get(0).record().getName());
        assertTrue(found.get(0).reasons().get(0).contains("lec01.pdf"));
    }

    @Test
    void emptyFilesAreNotTreatedAsDuplicates() throws IOException {
        TestFiles.write(root, "a.txt", "");
        TestFiles.write(root, "b.txt", "");
        assertTrue(new CleanupDetector(List.of(new DuplicateRule(gw))).detect(scanner.scan(root, false)).isEmpty());
    }

    @Test
    void temporaryAndPartialFilesAreFlagged() throws IOException {
        TestFiles.write(root, "x.tmp", "1");
        TestFiles.write(root, "~$report.docx", "2");
        TestFiles.write(root, "video.crdownload", "3");
        TestFiles.write(root, "keep.txt", "4");
        var found = new CleanupDetector(List.of(new TempFileRule())).detect(scanner.scan(root, false));
        assertEquals(3, found.size());
    }

    @Test
    void aFileCanHaveSeveralReasons() throws IOException {
        Path f = TestFiles.write(root, "old.tmp", "1");
        TestFiles.setModified(f, NOW.minusSeconds(60L * 60 * 24 * 900));
        var found = detector().detect(scanner.scan(root, false));
        assertEquals(1, found.size());
        assertEquals(2, found.get(0).reasons().size());
    }

    @Test
    void protectedFilesAreNeverFlagged() throws IOException {
        TestFiles.write(root, "Projects/old.tmp", "1");
        TestFiles.write(root, "other.tmp", "2");
        CleanupDetector d = detector();
        d.setProtectionFilter(r -> r.getPath().toString().contains("Projects"));
        var found = d.detect(scanner.scan(root, true));
        assertEquals(1, found.size());
        assertEquals("other.tmp", found.get(0).record().getName());
    }

    @Test
    void cleanupPlanMovesToToBeDeletedAndNeverDeletesAnything() throws IOException {
        TestFiles.write(root, "a.tmp", "1");
        TestFiles.write(root, "b.tmp", "2");
        Inventory inv = scanner.scan(root, false);
        CleanupDetector d = new CleanupDetector(List.of(new TempFileRule()));
        var found = d.detect(inv);
        Plan plan = d.toPlan(found, root, gw);
        plan.approve();
        new PlanExecutor(new ActionLog(), new TraceLogger()).execute(plan);
        Path bin = root.resolve(FileSystemGateway.TO_BE_DELETED);
        assertTrue(Files.exists(bin.resolve("a.tmp")));
        assertTrue(Files.exists(bin.resolve("b.tmp")));
        assertFalse(Files.exists(root.resolve("a.tmp")));
        try (var listing = Files.list(bin)) {
            assertEquals(2, listing.count(), "files are moved, not deleted");
        }
    }

    @Test
    void choosingNothingGivesAnEmptyPlan() {
        assertEquals(0, new CleanupDetector(List.of()).toPlan(List.of(), root, gw).size());
    }
}
