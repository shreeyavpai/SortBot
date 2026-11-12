package com.sortbot.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportBuilderTest {
    @TempDir Path tmp;

    private static ActionEntry e(String source, String message) {
        return new ActionEntry("run-1", 0, "move", source, "dst", false, ActionStatus.DONE, message, Instant.parse("2026-11-01T10:00:00Z"));
    }

    @Test
    void csvQuotesCommasAndQuotes() {
        String csv = new ReportBuilder().build(List.of(e("a, b.txt", "said \"hi\"")), ReportBuilder.Format.CSV);
        assertTrue(csv.startsWith("time,run,type"));
        assertTrue(csv.contains("\"a, b.txt\""));
        assertTrue(csv.contains("\"said \"\"hi\"\"\""));
    }

    @Test
    void markdownEscapesPipes() {
        String md = new ReportBuilder().build(List.of(e("a|b.txt", "ok")), ReportBuilder.Format.MARKDOWN);
        assertTrue(md.contains("a\\|b.txt"));
    }

    @Test
    void emptyHistoryStillProducesAHeader() {
        assertEquals(1, new ReportBuilder().build(List.of(), ReportBuilder.Format.CSV).lines().count());
    }

    @Test
    void writesTheFileAndCreatesMissingFolders() throws IOException {
        Path out = tmp.resolve("reports/new/report.csv");
        new ReportBuilder().write(List.of(e("a", "ok")), ReportBuilder.Format.CSV, out);
        assertTrue(Files.readString(out).contains("run-1"));
    }

    @Test
    void unwritableLocationGivesAnIOException() throws IOException {
        Path blocker = Files.writeString(tmp.resolve("blocker"), "i am a file");
        assertThrows(IOException.class, () -> new ReportBuilder().write(List.of(), ReportBuilder.Format.CSV, blocker.resolve("report.csv")));
    }
}
