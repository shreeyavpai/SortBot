package com.sortbot.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActionLogAndJsonTest {
    @TempDir Path tmp;

    private static ActionEntry entry(String run, int seq, String type, ActionStatus status, Instant when) {
        return new ActionEntry(run, seq, type, "C:\\src\\a.txt", "C:\\dst\\a.txt", false, status, "msg", when);
    }

    @Test
    void jsonRoundTripKeepsQuotesBackslashesNewlinesAndUnicode() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("path", "C:\\Users\\me\\\"quoted\" file.txt");
        m.put("note", "line1\nline2\ttab, {braces} and : colons");
        m.put("name", "r\u00e9sum\u00e9 \u2013 caf\u00e9.pdf");
        m.put("empty", "");
        assertEquals(m, FlatJson.parse(FlatJson.toJson(m)));
    }

    @Test
    void jsonParserRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> FlatJson.parse("not json"));
        assertThrows(IllegalArgumentException.class, () -> FlatJson.parse("{\"a\":\"unterminated"));
    }

    @Test
    void entriesSurviveARestart() {
        Path file = tmp.resolve("log.jsonl");
        ActionLog log = new ActionLog(file);
        log.append(entry("run-1", 0, "move", ActionStatus.DONE, Instant.parse("2026-11-01T10:00:00Z")));
        ActionLog reloaded = new ActionLog(file);
        assertEquals(1, reloaded.query(LogFilter.all()).size());
        assertEquals("C:\\dst\\a.txt", reloaded.query(LogFilter.all()).get(0).getTarget());
    }

    @Test
    void queryFiltersByRunTypeStatusDateAndPath() {
        ActionLog log = new ActionLog();
        log.append(entry("run-1", 0, "move", ActionStatus.DONE, Instant.parse("2026-11-01T10:00:00Z")));
        log.append(entry("run-1", 1, "createFolder", ActionStatus.FAILED, Instant.parse("2026-11-02T10:00:00Z")));
        log.append(entry("run-2", 0, "move", ActionStatus.DONE, Instant.parse("2026-11-03T10:00:00Z")));
        assertEquals(3, log.query(LogFilter.all()).size());
        assertEquals(2, log.query(LogFilter.all().runId("run-1")).size());
        assertEquals(2, log.query(LogFilter.all().type("move")).size());
        assertEquals(1, log.query(LogFilter.all().status(ActionStatus.FAILED)).size());
        assertEquals(2, log.query(LogFilter.all().from(Instant.parse("2026-11-02T00:00:00Z"))).size());
        assertEquals(1, log.query(LogFilter.all().to(Instant.parse("2026-11-01T23:00:00Z"))).size());
        assertEquals(3, log.query(LogFilter.all().pathContains("DST")).size());
        assertTrue(log.query(LogFilter.all().pathContains("nothing")).isEmpty());
    }

    @Test
    void markUndoneIsPersisted() {
        Path file = tmp.resolve("log.jsonl");
        ActionLog log = new ActionLog(file);
        ActionEntry e = entry("run-1", 0, "move", ActionStatus.DONE, Instant.now());
        log.append(e);
        log.markUndone(e);
        assertEquals(ActionStatus.UNDONE, new ActionLog(file).query(LogFilter.all()).get(0).getStatus());
    }

    @Test
    void lastUndoableRunSkipsUndoneEntries() {
        ActionLog log = new ActionLog();
        assertNull(log.lastUndoableRunId());
        ActionEntry a = entry("run-1", 0, "move", ActionStatus.DONE, Instant.now());
        ActionEntry b = entry("run-2", 0, "move", ActionStatus.DONE, Instant.now());
        log.append(a);
        log.append(b);
        assertEquals("run-2", log.lastUndoableRunId());
        log.markUndone(b);
        assertEquals("run-1", log.lastUndoableRunId());
    }

    @Test
    void damagedLinesAreSkippedAndCountedInsteadOfCrashing() throws IOException {
        Path file = tmp.resolve("log.jsonl");
        ActionLog log = new ActionLog(file);
        log.append(entry("run-1", 0, "move", ActionStatus.DONE, Instant.now()));
        Files.writeString(file, "this line is damaged\n", StandardOpenOption.APPEND);
        ActionLog reloaded = new ActionLog(file);
        assertEquals(1, reloaded.query(LogFilter.all()).size());
        assertEquals(1, reloaded.getDamagedLineCount());
    }

    @Test
    void traceLoggerRecordsToolCallsAndFileChangesToAFile() throws IOException {
        Path file = tmp.resolve("trace.jsonl");
        TraceLogger t = new TraceLogger(file);
        t.toolCall("GeneratePlanTool", "{\"folder\":\"Downloads\"}", "ok");
        t.fileChange("move", "a", "b", "DONE");
        assertEquals(2, t.events().size());
        assertEquals(2, Files.readAllLines(file).size());
        assertEquals("tool_call", FlatJson.parse(Files.readAllLines(file).get(0)).get("event"));
    }
}
