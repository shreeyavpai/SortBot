package com.sortbot.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.TestFiles;
import com.sortbot.infra.Json;
import com.sortbot.llm.FakeLLMClient;
import com.sortbot.ui.cli.CliApp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliAppTest {
    @TempDir Path tmp;
    Path root;
    String data;
    FakeLLMClient llm;
    String output;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectory(tmp.resolve("Downloads"));
        data = tmp.resolve("data").toString();
        llm = new FakeLLMClient(p -> {
            if (p.contains("file classification assistant")) {
                for (String l : p.split("\\n")) {
                    if (l.contains("name: scan0032.pdf")) return "{\"files\":[{\"id\":" + l.substring(0, l.indexOf('.')) + ",\"category\":\"RECEIPTS\",\"confidence\":0.8,\"reason\":\"looks like a bill\"}]}";
                }
                return "{\"files\":[]}";
            }
            if (p.contains("You organize files into folders")) return "{\"moves\":[{\"file\":\"scan0032.pdf\",\"folder\":\"Finance\"}]}";
            if (p.contains("Decide which tool")) return "{\"tool\":\"generate_plan\",\"args\":{\"instruction\":\"tidy\"}}";
            return "{\"clarify\":\"?\"}";
        });
        TestFiles.write(root, "scan0032.pdf", "unclear");
        TestFiles.write(root, "junk.tmp", "x");
    }

    private int run(String stdin, String... args) {
        String[] full = new String[args.length + 2];
        System.arraycopy(args, 0, full, 0, args.length);
        full[args.length] = "--data-dir";
        full[args.length + 1] = data;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int code = new CliApp(new ByteArrayInputStream(stdin.getBytes()), new PrintStream(buf, true), llm).run(full);
        output = buf.toString();
        return code;
    }

    private Object json(Object... path) {
        return Json.path(Json.parse(output.trim()), path);
    }

    @Test
    void helpListsTheCommandsAndUnknownCommandsFail() {
        assertEquals(0, run("", "--help"));
        assertTrue(output.contains("organize"));
        assertEquals(2, run("", "dance"));
        assertTrue(output.contains("Unknown command"));
    }

    @Test
    void scanningAnUnauthorizedFolderIsRefusedWithAHint() {
        assertEquals(1, run("", "scan", root.toString()));
        assertTrue(output.contains("folders add"));
    }

    @Test
    void foldersCanBeAddedListedAndRemovedAndBadOnesAreRejected() {
        assertEquals(0, run("", "folders", "add", root.toString(), "--json"));
        assertEquals(Boolean.TRUE, json("ok"));
        assertEquals(0, run("", "folders", "list"));
        assertTrue(output.contains("Downloads"));
        assertEquals(1, run("", "folders", "add", tmp.resolve("nope").toString()));
        assertTrue(output.contains("does not exist"));
        assertEquals(0, run("", "folders", "remove", root.toString()));
        assertEquals(1, run("", "scan", root.toString()));
    }

    @Test
    void scanAndClassifyReportFilesAsJson() {
        run("", "folders", "add", root.toString());
        assertEquals(0, run("", "scan", root.toString(), "--json"));
        assertEquals(2L, json("data", "count"));
        assertEquals(0, run("", "classify", root.toString(), "--json"));
        var files = (java.util.List<?>) json("data", "files");
        String cat = null;
        for (Object f : files) if ("scan0032.pdf".equals(Json.path(f, "name"))) cat = (String) Json.path(f, "category");
        assertEquals("RECEIPTS", cat);
    }

    @Test
    void planChangesNothingAndOrganizeAsksFirst() {
        run("", "folders", "add", root.toString());
        assertEquals(0, run("", "plan", root.toString(), "--json"));
        assertEquals(Boolean.FALSE, json("data", "applied"));
        assertTrue(Files.exists(root.resolve("scan0032.pdf")));
        assertEquals(0, run("n\n", "organize", root.toString()));
        assertTrue(output.contains("Not applied"));
        assertTrue(Files.exists(root.resolve("scan0032.pdf")));
        assertEquals(0, run("", "organize", root.toString(), "--json"));
        assertEquals(Boolean.FALSE, json("data", "applied"));
    }

    @Test
    void organizeAppliesAfterApprovalAndUndoRestores() {
        run("", "folders", "add", root.toString());
        assertEquals(0, run("y\n", "organize", root.toString()));
        assertTrue(output.contains("Applied"));
        assertTrue(Files.exists(root.resolve("Finance/scan0032.pdf")));
        assertEquals(0, run("", "history", "--json"));
        assertEquals(2, ((java.util.List<?>) json("data", "entries")).size());
        assertEquals(0, run("", "undo", "--json"));
        assertEquals(2L, json("data", "undone"));
        assertTrue(Files.exists(root.resolve("scan0032.pdf")));
        assertEquals(0, run("", "undo"));
        assertTrue(output.contains("Nothing to undo"));
    }

    @Test
    void yesFlagAppliesWithoutPromptingAndExportWritesAReport() throws IOException {
        run("", "folders", "add", root.toString());
        assertEquals(0, run("", "organize", root.toString(), "--yes", "--json"));
        assertEquals(Boolean.TRUE, json("data", "applied"));
        Path report = tmp.resolve("report.csv");
        assertEquals(0, run("", "export", report.toString()));
        assertTrue(Files.readString(report).contains("move"));
        Path md = tmp.resolve("report.md");
        assertEquals(0, run("", "export", md.toString()));
        assertTrue(Files.readString(md).startsWith("# SortBot action report"));
    }

    @Test
    void cleanupListsThenMovesToToBeDeleted() {
        run("", "folders", "add", root.toString());
        assertEquals(0, run("", "cleanup", root.toString(), "--json"));
        assertEquals(1, ((java.util.List<?>) json("data", "candidates")).size());
        assertTrue(Files.exists(root.resolve("junk.tmp")));
        assertEquals(0, run("", "cleanup", root.toString(), "--yes"));
        assertTrue(Files.exists(root.resolve("To_Be_Deleted/junk.tmp")));
    }

    @Test
    void chatOneShotRefusesDeletionWithoutCallingTheModelAndPlansWithoutApplying() {
        run("", "folders", "add", root.toString());
        int callsBefore = llm.calls();
        assertEquals(0, run("", "chat", root.toString(), "--message", "delete everything", "--json"));
        assertTrue(((String) json("data", "reply")).contains("never deletes"));
        assertEquals(callsBefore + 1, llm.calls(), "only the classification call, not the chat");
        assertEquals(0, run("", "chat", root.toString(), "--message", "tidy up", "--json"));
        assertFalse(((java.util.List<?>) json("data", "actions")).isEmpty());
        assertTrue(Files.exists(root.resolve("scan0032.pdf")));
    }

    @Test
    void interactiveChatCanApproveAPlanAndQuit() {
        run("", "folders", "add", root.toString());
        assertEquals(0, run("tidy up\n/approve\n/quit\n", "chat", root.toString()));
        assertTrue(output.contains("Applied"));
        assertTrue(Files.exists(root.resolve("Finance/scan0032.pdf")));
    }

    @Test
    void rulesCanBeAddedListedAndRemoved() {
        assertEquals(0, run("", "rules", "add", "place_name", "tax", "Finance/Tax", "--json"));
        String id = (String) json("data", "rule", "id");
        assertEquals(0, run("", "rules", "list"));
        assertTrue(output.contains("tax"));
        assertEquals(1, run("", "rules", "add", "place_name", "tax", "Other"));
        assertTrue(output.contains("conflicts"));
        assertEquals(0, run("", "rules", "remove", id));
        assertEquals(0, run("", "rules", "list"));
        assertTrue(output.contains("No saved rules"));
    }

    @Test
    void aBrokenModelConfigurationGivesAClearMessageNotACrash() throws IOException {
        llm = FakeLLMClient.failing("The model could not be reached");
        run("", "folders", "add", root.toString());
        assertEquals(1, run("", "plan", root.toString()));
        assertTrue(output.contains("unavailable"));
    }
}
