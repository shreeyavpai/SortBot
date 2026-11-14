package com.sortbot.ui.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.TestFiles;
import com.sortbot.core.ReportBuilder;
import com.sortbot.domain.Category;
import com.sortbot.domain.UserRule;
import com.sortbot.facade.SortBotFacade;
import com.sortbot.llm.FakeLLMClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives the real panels without a screen (work runs inline and dialogs are skipped). */
class GuiPanelsTest {
    @TempDir Path tmp;
    Path root;
    MainPanel ui;
    GuiContext ctx;

    @BeforeEach
    void setUp() throws IOException {
        GuiContext.synchronous = true;
        root = Files.createDirectory(tmp.resolve("Downloads"));
        TestFiles.write(root, "scan0032.pdf", "an unclear scan");
        TestFiles.write(root, "lec01.pdf", "slides");
        TestFiles.write(root, "junk.tmp", "partial");
        FakeLLMClient llm = new FakeLLMClient(p -> {
            if (p.contains("file classification assistant")) {
                for (String l : p.split("\\n")) {
                    if (l.contains("name: scan0032.pdf")) return "{\"files\":[{\"id\":" + l.substring(0, l.indexOf('.')) + ",\"category\":\"RECEIPTS\",\"confidence\":0.8,\"reason\":\"bill\"}]}";
                }
                return "{\"files\":[]}";
            }
            if (p.contains("You organize files into folders")) return "{\"moves\":[{\"file\":\"scan0032.pdf\",\"folder\":\"Finance\"},{\"file\":\"lec01.pdf\",\"folder\":\"School\"}]}";
            if (p.contains("Decide which tool")) {
                if (p.contains("User message: always")) return "{\"tool\":\"propose_rule\",\"args\":{\"kind\":\"place_name\",\"match\":\"lecture\",\"folder\":\"School\"}}";
                return "{\"tool\":\"generate_plan\",\"args\":{\"instruction\":\"tidy\"}}";
            }
            return "{\"clarify\":\"?\"}";
        });
        ctx = new GuiContext(SortBotFacade.create(tmp.resolve("data"), llm, 12));
        ui = new MainPanel(ctx);
        ui.folders.addFolder(root);
    }

    private void scanAndClassify() {
        ui.scan.scanNow();
        ui.scan.classifyNow();
    }

    @Test
    void everyFeatureHasATab() {
        assertEquals(7, ui.tabCount());
    }

    @Test
    void foldersPanelAddsRemovesAndRejectsBadFolders() {
        assertEquals(1, ui.folders.folderCount());
        ui.folders.addFolder(tmp.resolve("nope"));
        assertTrue(ctx.lastError.contains("does not exist"));
        ui.folders.selectIndex(0);
        ui.folders.removeSelected();
        assertEquals(0, ui.folders.folderCount());
        ui.folders.removeSelected();
        assertTrue(ctx.lastError.contains("Select a folder"));
    }

    @Test
    void scanNeedsAFolderThenShowsFilesAndClassifyFillsCategories() throws IOException {
        ui.folders.selectIndex(0);
        ui.folders.removeSelected();
        ui.scan.scanNow();
        assertTrue(ctx.lastError.contains("Add a folder"));
        ui.folders.addFolder(root);
        ui.scan.classifyNow();
        assertTrue(ctx.lastError.contains("Scan a folder first"));
        scanAndClassify();
        assertEquals(3, ui.scan.rowCount());
        boolean sawReceipt = false;
        for (int i = 0; i < ui.scan.rowCount(); i++) if ("scan0032.pdf".equals(ui.scan.cell(i, 0))) sawReceipt = ui.scan.cell(i, 3) == Category.RECEIPTS;
        assertTrue(sawReceipt);
    }

    @Test
    void categoriesCanBeCorrectedByHand() {
        scanAndClassify();
        ui.scan.setCategoryAt(0, Category.PHOTOS);
        assertEquals(Category.PHOTOS, ui.scan.cell(0, 3));
        assertEquals("100%", ui.scan.cell(0, 4));
    }

    @Test
    void planReviewEditApproveAndUndo() throws IOException {
        scanAndClassify();
        ui.plan.generateNow();
        assertEquals(2, ui.plan.actionIds().size());
        assertFalse(ui.plan.currentPlan().isApproved());
        assertTrue(Files.exists(root.resolve("scan0032.pdf")), "generating a plan changes nothing");

        String first = ui.plan.actionIds().get(0);
        ui.plan.moveAction(first, "../Windows");
        assertTrue(ctx.lastError.contains("short relative path"));
        String second = ui.plan.actionIds().get(1);
        ui.plan.moveAction(second, "Course/Week1");
        ui.plan.rejectAction(first);
        assertEquals(1, ui.plan.actionIds().size());

        ui.plan.approveNow();
        assertNull(ui.plan.currentPlan());
        assertTrue(ctx.lastInfo.contains("Done"));
        assertTrue(Files.exists(root.resolve("Course/Week1/lec01.pdf")));
        assertTrue(Files.exists(root.resolve("scan0032.pdf")), "the rejected move did not happen");
        assertTrue(ui.history.rowCount() > 0);

        ui.history.undoSelectedRun();
        assertTrue(Files.exists(root.resolve("lec01.pdf")));
        assertFalse(Files.exists(root.resolve("Course")));
    }

    @Test
    void planNeedsAScan() {
        ui.plan.generateNow();
        assertTrue(ctx.lastError.contains("Scan and classify"));
        ui.plan.approveNow();
        assertTrue(ctx.lastError.contains("no plan"));
    }

    @Test
    void cleanupFindsTicksAndMovesWithoutDeleting() throws IOException {
        scanAndClassify();
        ui.cleanup.findNow();
        assertEquals(1, ui.cleanup.rowCount());
        ui.cleanup.tickAll(false);
        ui.cleanup.moveSelectedNow();
        assertTrue(ctx.lastError.contains("Tick at least one"));
        ui.cleanup.tickAll(true);
        ui.cleanup.moveSelectedNow();
        assertTrue(Files.exists(root.resolve("To_Be_Deleted/junk.tmp")));
        assertEquals(0, ui.cleanup.rowCount());
    }

    @Test
    void chatShowsAPlanOnThePlanTabAndNeverMovesFilesItself() throws IOException {
        scanAndClassify();
        ui.chat.send("tidy my downloads");
        assertEquals("Plan", ui.selectedTabTitle());
        assertEquals(2, ui.plan.actionIds().size());
        assertTrue(Files.exists(root.resolve("scan0032.pdf")));
        assertTrue(ui.chat.transcriptText().contains("nothing has changed yet"));
    }

    @Test
    void chatRefusesDeletionAndCanSaveAProposedRule() {
        scanAndClassify();
        ui.chat.send("delete all my old files");
        assertTrue(ui.chat.transcriptText().contains("never deletes"));
        ui.chat.send("always put lecture slides under School");
        assertTrue(ui.chat.canSaveRule());
        assertEquals(0, ui.rules.ruleCount());
        ui.chat.saveProposedRule();
        assertEquals(1, ui.rules.ruleCount());
        assertFalse(ui.chat.canSaveRule());
    }

    @Test
    void rulesPanelAddsRejectsAndRemoves() {
        ui.rules.addRule(UserRule.Kind.PLACE_NAME, "tax", "Finance/Tax");
        assertEquals(1, ui.rules.ruleCount());
        ui.rules.addRule(UserRule.Kind.PLACE_NAME, "tax", "Other");
        assertTrue(ctx.lastError.contains("conflicts"));
        ui.rules.addRule(UserRule.Kind.PLACE_NAME, "x", "Docs");
        assertTrue(ctx.lastError.contains("too broad"));
        ui.rules.selectRow(0);
        ui.rules.removeSelected();
        assertEquals(0, ui.rules.ruleCount());
    }

    @Test
    void historyExportsReportsAndReportsProblems() throws IOException {
        scanAndClassify();
        ui.plan.generateNow();
        ui.plan.approveNow();
        Path report = tmp.resolve("out/report.csv");
        ui.history.exportTo(report, ReportBuilder.Format.CSV);
        assertTrue(Files.readString(report).contains("move"));
        Path blocker = Files.writeString(tmp.resolve("blocker"), "file");
        ui.history.exportTo(blocker.resolve("x.csv"), ReportBuilder.Format.CSV);
        assertTrue(ctx.lastError.contains("Could not save"));
        ui.history.undoLastNow();
        assertNotNull(ctx.lastInfo);
    }

    @Test
    void unauthorizedFolderErrorsAreFriendly() {
        assertTrue(GuiContext.friendly(new com.sortbot.core.NotAuthorizedException(root)).contains("Folders tab"));
    }
}
