package com.sortbot.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.domain.UserRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoryManagerTest {
    @TempDir Path tmp;

    private static UserRule place(String match, String folder) {
        return UserRule.create(UserRule.Kind.PLACE_NAME, match, folder);
    }

    @Test
    void rulesSurviveARestart() {
        Path file = tmp.resolve("rules.jsonl");
        MemoryManager m = new MemoryManager(new RuleRepository(file));
        m.saveRule(place("receipt", "Finance\\Receipts"));
        MemoryManager again = new MemoryManager(new RuleRepository(file));
        assertEquals(1, again.getRules().size());
        assertEquals("Finance/Receipts", again.getRules().get(0).getFolder(), "folder is cleaned before saving");
    }

    @Test
    void conflictingAndDuplicateRulesAreRejected() {
        MemoryManager m = new MemoryManager(new RuleRepository(null));
        m.saveRule(place("tax", "Finance"));
        assertTrue(m.checkConflict(place("TAX", "Other")));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> m.saveRule(place("tax", "Other"))).getMessage().contains("conflicts"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> m.saveRule(place("tax", "Finance"))).getMessage().contains("already"));
        assertEquals(1, m.getRules().size());
    }

    @Test
    void tooBroadUnsafeOrEmptyRulesAreRejected() {
        MemoryManager m = new MemoryManager(new RuleRepository(null));
        assertThrows(IllegalArgumentException.class, () -> m.saveRule(place("a", "Docs")));
        assertThrows(IllegalArgumentException.class, () -> m.saveRule(place("*", "Docs")));
        assertThrows(IllegalArgumentException.class, () -> m.saveRule(place("", "Docs")));
        assertThrows(IllegalArgumentException.class, () -> m.saveRule(place("tax", "../Windows")));
        assertThrows(IllegalArgumentException.class, () -> m.saveRule(place("tax", "C:\\Windows")));
        assertThrows(IllegalArgumentException.class, () -> m.saveRule(UserRule.create(UserRule.Kind.PLACE_CATEGORY, "NOPE", "Docs")));
        assertTrue(m.getRules().isEmpty());
    }

    @Test
    void removeWorksAndUnknownIdsReportFalse() {
        MemoryManager m = new MemoryManager(new RuleRepository(null));
        UserRule saved = m.saveRule(place("tax", "Finance"));
        assertFalse(m.removeRule("nope"));
        assertTrue(m.removeRule(saved.getId()));
        assertTrue(m.getRules().isEmpty());
    }

    @Test
    void aDamagedRulesFileIsBackedUpAndReadableRulesAreKept() throws IOException {
        Path file = tmp.resolve("rules.jsonl");
        new MemoryManager(new RuleRepository(file)).saveRule(place("tax", "Finance"));
        Files.writeString(file, "garbage line\n", StandardOpenOption.APPEND);
        RuleRepository repo = new RuleRepository(file);
        MemoryManager m = new MemoryManager(repo);
        assertEquals(1, m.getRules().size());
        assertEquals(1, m.getDamagedLineCount());
        assertTrue(Files.exists(tmp.resolve("rules.jsonl.bak")));
    }

    @Test
    void ruleMatchingFollowsItsKind() {
        var r = new com.sortbot.domain.FileRecord(Path.of("C:/Users/me/Projects/App/readme.md").toAbsolutePath(), 1, java.time.Instant.now());
        assertTrue(UserRule.create(UserRule.Kind.PROTECT_PATH, "projects/app", "").matches(r));
        assertTrue(UserRule.create(UserRule.Kind.PLACE_NAME, "README", "Docs").matches(r));
        assertFalse(UserRule.create(UserRule.Kind.PLACE_CATEGORY, "RECEIPTS", "Fin").matches(r));
        assertNull(FolderNames.sanitize("a/b/c/d"));
        assertEquals("a/b", FolderNames.sanitize(" a // b/ "));
        assertNull(FolderNames.sanitize("CON"));
    }
}
