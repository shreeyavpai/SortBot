package com.sortbot.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.TestFiles;
import com.sortbot.agent.ChatReply;
import com.sortbot.core.CleanupCandidate;
import com.sortbot.core.LogFilter;
import com.sortbot.core.NotAuthorizedException;
import com.sortbot.core.ReportBuilder;
import com.sortbot.domain.Category;
import com.sortbot.domain.Inventory;
import com.sortbot.domain.Plan;
import com.sortbot.domain.RunResult;
import com.sortbot.domain.UndoResult;
import com.sortbot.domain.UserRule;
import com.sortbot.llm.FakeLLMClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FacadeEndToEndTest {
    @TempDir Path tmp;
    Path root;
    Path data;
    FakeLLMClient llm;
    SortBotFacade facade;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectory(tmp.resolve("Downloads"));
        data = tmp.resolve("data");
        llm = new FakeLLMClient(p -> {
            if (p.contains("file classification assistant")) {
                for (String l : p.split("\\n")) {
                    if (l.contains("name: scan0032.pdf")) return "{\"files\":[{\"id\":" + l.substring(0, l.indexOf('.')) + ",\"category\":\"RECEIPTS\",\"confidence\":0.8,\"reason\":\"looks like a bill\"}]}";
                }
                return "{\"files\":[]}";
            }
            if (p.contains("You organize files into folders")) return "{\"moves\":[{\"file\":\"scan0032.pdf\",\"folder\":\"Finance\"},{\"file\":\"lec01.pdf\",\"folder\":\"School\"}]}";
            if (p.contains("Decide which tool")) return "{\"tool\":\"generate_plan\",\"args\":{\"instruction\":\"tidy\"}}";
            return "{\"clarify\":\"What do you mean?\"}";
        });
        facade = SortBotFacade.create(data, llm, 12);
        facade.authorizeFolder(root);
        TestFiles.write(root, "scan0032.pdf", "an unclear scan");
        TestFiles.write(root, "lec01.pdf", "slides");
        TestFiles.write(root, "download.tmp", "partial");
    }

    @Test
    void fullFlowScanClassifyPlanExecuteUndo() throws IOException {
        Inventory inv = facade.classify(facade.scanFolder(root, false));
        assertEquals(3, inv.size());
        assertEquals(Category.RECEIPTS, inv.filter(r -> r.getName().equals("scan0032.pdf")).get(0).getCategory());
        assertEquals(Category.LECTURE_NOTES, inv.filter(r -> r.getName().equals("lec01.pdf")).get(0).getCategory());

        Plan plan = facade.generatePlan(inv, "tidy up");
        assertEquals(4, plan.size());
        assertTrue(Files.exists(root.resolve("scan0032.pdf")), "planning must not move anything");

        RunResult run = facade.executePlan(plan);
        assertEquals(4, run.done());
        assertTrue(Files.exists(root.resolve("Finance/scan0032.pdf")));
        assertTrue(Files.exists(root.resolve("School/lec01.pdf")));
        assertEquals(4, facade.getHistory(LogFilter.all().runId(run.runId())).size());

        UndoResult undo = facade.undo(run.runId());
        assertEquals(4, undo.undone());
        assertTrue(Files.exists(root.resolve("scan0032.pdf")));
        assertFalse(Files.exists(root.resolve("Finance")));
    }

    @Test
    void editingAndRejectingMovesChangesWhatRuns() throws IOException {
        Inventory inv = facade.classify(facade.scanFolder(root, false));
        Plan plan = facade.generatePlan(inv, "");
        var move = plan.getActions().stream().filter(a -> a.source() != null && a.source().getFileName().toString().equals("lec01.pdf")).findFirst().get();
        assertThrows(IllegalArgumentException.class, () -> facade.editPlan(plan, move.id(), tmp.resolve("elsewhere/lec01.pdf")));
        facade.editPlan(plan, move.id(), root.resolve("Course/lec01.pdf"));
        var other = plan.getActions().stream().filter(a -> a.source() != null && a.source().getFileName().toString().equals("scan0032.pdf")).findFirst().get();
        assertTrue(facade.rejectMove(plan, other.id()));
        facade.executePlan(plan);
        assertTrue(Files.exists(root.resolve("Course/lec01.pdf")));
        assertTrue(Files.exists(root.resolve("scan0032.pdf")));
    }

    @Test
    void unauthorizedFoldersCannotBeScanned() throws IOException {
        Path other = Files.createDirectory(tmp.resolve("Private"));
        assertThrows(NotAuthorizedException.class, () -> facade.scanFolder(other, false));
    }

    @Test
    void cleanupMovesCandidatesToToBeDeletedAndCanBeUndone() throws IOException {
        Inventory inv = facade.scanFolder(root, false);
        List<CleanupCandidate> found = facade.findCleanup(inv);
        assertEquals(1, found.size());
        RunResult run = facade.confirmCleanup(found);
        assertTrue(Files.exists(root.resolve("To_Be_Deleted/download.tmp")));
        assertFalse(Files.exists(root.resolve("download.tmp")));
        facade.undo(run.runId());
        assertTrue(Files.exists(root.resolve("download.tmp")));
    }

    @Test
    void protectRulesKeepFilesOutOfPlansAndCleanup() throws IOException {
        facade.saveRule(UserRule.create(UserRule.Kind.PROTECT_PATH, "download.tmp", ""));
        Inventory inv = facade.scanFolder(root, false);
        assertTrue(facade.findCleanup(inv).isEmpty());
    }

    @Test
    void chatProducesAPlanThatWaitsForApproval() throws IOException {
        facade.classify(facade.scanFolder(root, false));
        ChatReply reply = facade.handleChat("tidy my downloads");
        assertNotNull(reply.plan());
        assertFalse(reply.plan().isApproved());
        assertTrue(Files.exists(root.resolve("scan0032.pdf")));
        RunResult run = facade.executePlan(reply.plan());
        assertTrue(run.done() > 0);
    }

    @Test
    void rulesFoldersHistoryAndReportsSurviveARestart() throws IOException {
        facade.saveRule(UserRule.create(UserRule.Kind.PLACE_NAME, "tax", "Finance/Tax"));
        Inventory inv = facade.classify(facade.scanFolder(root, false));
        RunResult run = facade.executePlan(facade.generatePlan(inv, ""));

        SortBotFacade again = SortBotFacade.create(data, llm, 12);
        assertEquals(1, again.getRules().size());
        assertEquals(1, again.getAllowedFolders().size());
        assertEquals(run.runId(), again.lastUndoableRunId());
        Path report = tmp.resolve("out/report.md");
        again.exportReport(ReportBuilder.Format.MARKDOWN, report, LogFilter.all());
        assertTrue(Files.readString(report).contains(run.runId()));
        assertEquals(4, again.undo(run.runId()).undone());
    }

    @Test
    void confirmingARuleWithNothingProposedIsAnError() {
        assertThrows(IllegalStateException.class, () -> facade.confirmPendingRule());
    }
}
