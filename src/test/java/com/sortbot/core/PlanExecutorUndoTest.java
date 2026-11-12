package com.sortbot.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.TestFiles;
import com.sortbot.domain.CreateFolderAction;
import com.sortbot.domain.MoveFileAction;
import com.sortbot.domain.Plan;
import com.sortbot.domain.RunResult;
import com.sortbot.domain.UndoResult;
import com.sortbot.infra.FileSystemGateway;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanExecutorUndoTest {
    @TempDir Path tmp;
    Path root;
    Path outside;
    FileSystemGateway gw;
    ActionLog log;
    TraceLogger trace;
    PlanExecutor executor;
    UndoManager undo;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectory(tmp.resolve("Downloads"));
        outside = Files.createDirectory(tmp.resolve("Outside"));
        FolderAuthorizer a = new FolderAuthorizer();
        a.add(root);
        gw = new FileSystemGateway(a);
        log = new ActionLog();
        trace = new TraceLogger();
        executor = new PlanExecutor(log, trace);
        undo = new UndoManager(log, gw, trace);
    }

    private Plan planMoving(Path... files) {
        Plan plan = new Plan();
        Path docs = root.resolve("Docs");
        plan.add(new CreateFolderAction(gw, docs));
        for (Path f : files) plan.add(new MoveFileAction(gw, f, docs.resolve(f.getFileName())));
        plan.approve();
        return plan;
    }

    @Test
    void unapprovedPlanIsRefusedAndNothingChanges() throws IOException {
        Path f = TestFiles.write(root, "a.txt", "x");
        Plan plan = new Plan();
        plan.add(new MoveFileAction(gw, f, root.resolve("Docs/a.txt")));
        assertThrows(IllegalStateException.class, () -> executor.execute(plan));
        assertTrue(Files.exists(f));
        assertTrue(log.query(LogFilter.all()).isEmpty());
    }

    @Test
    void executingMovesFilesAndLogsEveryAction() throws IOException {
        Path f = TestFiles.write(root, "a.txt", "x");
        RunResult r = executor.execute(planMoving(f));
        assertEquals(2, r.done());
        assertEquals(0, r.failed());
        assertFalse(Files.exists(f));
        assertTrue(Files.exists(root.resolve("Docs/a.txt")));
        assertEquals(2, log.query(LogFilter.all().runId(r.runId()).status(ActionStatus.DONE)).size());
        assertEquals(2, trace.events().size());
    }

    @Test
    void existingDestinationIsNeverOverwritten() throws IOException {
        TestFiles.write(root, "Docs/a.txt", "old");
        Path f = TestFiles.write(root, "a.txt", "new");
        executor.execute(planMoving(f));
        assertEquals("old", Files.readString(root.resolve("Docs/a.txt")));
        assertEquals("new", Files.readString(root.resolve("Docs/a (1).txt")));
    }

    @Test
    void unauthorizedTargetFailsButOtherActionsContinue() throws IOException {
        Path bad = TestFiles.write(root, "bad.txt", "x");
        Path good = TestFiles.write(root, "good.txt", "y");
        Plan plan = new Plan();
        plan.add(new MoveFileAction(gw, bad, outside.resolve("bad.txt")));
        plan.add(new MoveFileAction(gw, good, root.resolve("Docs/good.txt")));
        plan.approve();
        RunResult r = executor.execute(plan);
        assertEquals(1, r.failed());
        assertEquals(1, r.done());
        assertTrue(Files.exists(bad));
        assertFalse(Files.exists(outside.resolve("bad.txt")));
        assertEquals(1, log.query(LogFilter.all().status(ActionStatus.FAILED)).size());
    }

    @Test
    void cancelStopsAfterTheCurrentActionAndKeepsCompletedOnesUndoable() throws IOException {
        Path a = TestFiles.write(root, "a.txt", "1");
        Path b = TestFiles.write(root, "b.txt", "2");
        Path c = TestFiles.write(root, "c.txt", "3");
        executor.subscribe(e -> executor.cancel());
        RunResult r = executor.execute(planMoving(a, b, c));
        assertTrue(r.cancelled());
        assertEquals(1, r.done());
        assertEquals(3, r.skipped());
        assertEquals(1, undo.undoRun(r.runId()).undone());
    }

    @Test
    void undoRestoresFilesAndRemovesTheFolderItCreated() throws IOException {
        Path f = TestFiles.write(root, "a.txt", "x");
        RunResult r = executor.execute(planMoving(f));
        UndoResult u = undo.undoRun(r.runId());
        assertEquals(2, u.undone());
        assertEquals(0, u.skipped());
        assertTrue(Files.exists(f));
        assertFalse(Files.exists(root.resolve("Docs")));
        assertEquals(2, log.query(LogFilter.all().status(ActionStatus.UNDONE)).size());
    }

    @Test
    void undoDoesNotRemoveAFolderThatAlreadyExisted() throws IOException {
        Files.createDirectory(root.resolve("Docs"));
        Path f = TestFiles.write(root, "a.txt", "x");
        RunResult r = executor.execute(planMoving(f));
        undo.undoRun(r.runId());
        assertTrue(Files.isDirectory(root.resolve("Docs")));
    }

    @Test
    void undoKeepsACreatedFolderThatNowHasOtherContent() throws IOException {
        Path f = TestFiles.write(root, "a.txt", "x");
        RunResult r = executor.execute(planMoving(f));
        TestFiles.write(root, "Docs/mine.txt", "added later");
        undo.undoRun(r.runId());
        assertTrue(Files.exists(root.resolve("Docs/mine.txt")));
        assertTrue(Files.exists(f));
    }

    @Test
    void undoSkipsAnActionWhenTheOriginalLocationIsNowOccupied() throws IOException {
        Path f = TestFiles.write(root, "a.txt", "moved");
        RunResult r = executor.execute(planMoving(f));
        TestFiles.write(root, "a.txt", "new file in the old spot");
        UndoResult u = undo.undoRun(r.runId());
        assertEquals(1, u.skipped());
        assertEquals("new file in the old spot", Files.readString(f));
        assertEquals("moved", Files.readString(root.resolve("Docs/a.txt")));
        assertFalse(u.messages().isEmpty());
    }

    @Test
    void undoStillWorksAfterARestartBecauseTheLogIsPersisted() throws IOException {
        Path logFile = tmp.resolve("state/actionlog.jsonl");
        ActionLog persisted = new ActionLog(logFile);
        PlanExecutor ex = new PlanExecutor(persisted, trace);
        Path f = TestFiles.write(root, "a.txt", "x");
        RunResult r = ex.execute(planMoving(f));

        ActionLog reloaded = new ActionLog(logFile);
        UndoResult u = new UndoManager(reloaded, gw, trace).undoRun(r.runId());
        assertEquals(2, u.undone());
        assertTrue(Files.exists(f));
        assertEquals(2, new ActionLog(logFile).query(LogFilter.all().status(ActionStatus.UNDONE)).size());
    }

    @Test
    void undoLastOnlyReversesTheNewestAction() throws IOException {
        Path a = TestFiles.write(root, "a.txt", "1");
        Path b = TestFiles.write(root, "b.txt", "2");
        executor.execute(planMoving(a, b));
        UndoResult u = undo.undoLast();
        assertEquals(1, u.undone());
        assertTrue(Files.exists(b));
        assertFalse(Files.exists(a));
    }

    @Test
    void nothingToUndoIsReportedCleanly() {
        assertTrue(undo.undoLast().nothingToUndo());
        assertTrue(undo.undoRun("run-unknown").nothingToUndo());
    }

    @Test
    void undoingTwiceDoesNothingTheSecondTime() throws IOException {
        Path f = TestFiles.write(root, "a.txt", "x");
        RunResult r = executor.execute(planMoving(f));
        undo.undoRun(r.runId());
        assertTrue(undo.undoRun(r.runId()).nothingToUndo());
    }
}
