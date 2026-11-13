package com.sortbot.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.domain.Category;
import com.sortbot.domain.Inventory;
import com.sortbot.llm.FakeLLMClient;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassifierTest {
    @TempDir Path tmp;

    private static Category of(Inventory inv, String name) {
        return inv.filter(r -> r.getName().equals(name)).get(0).getCategory();
    }

    @Test
    void obviousFilesAreClassifiedByRulesWithoutCallingTheModel() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("holiday.jpg", "Screenshot 1.png", "setup.exe", "bundle.zip", "Main.java", "receipt_oct.pdf", "Lecture 03.pdf", "hw2.docx");
        FakeLLMClient llm = new FakeLLMClient(List.of("{}"));
        s.classifier(llm).classify(inv);
        assertEquals(0, llm.calls());
        assertEquals(Category.PHOTOS, of(inv, "holiday.jpg"));
        assertEquals(Category.SCREENSHOTS, of(inv, "Screenshot 1.png"));
        assertEquals(Category.INSTALLERS, of(inv, "setup.exe"));
        assertEquals(Category.ARCHIVES, of(inv, "bundle.zip"));
        assertEquals(Category.CODE, of(inv, "Main.java"));
        assertEquals(Category.RECEIPTS, of(inv, "receipt_oct.pdf"));
        assertEquals(Category.LECTURE_NOTES, of(inv, "Lecture 03.pdf"));
        assertEquals(Category.ASSIGNMENTS, of(inv, "hw2.docx"));
    }

    @Test
    void ambiguousFilesGoToTheModelAndUseItsAnswer() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("scan0032.pdf");
        FakeLLMClient llm = new FakeLLMClient(List.of("{\"files\":[{\"id\":1,\"category\":\"RECEIPTS\",\"confidence\":0.8,\"reason\":\"looks like a bill\"}]}"));
        s.classifier(llm).classify(inv);
        assertEquals(Category.RECEIPTS, of(inv, "scan0032.pdf"));
        assertEquals(1, llm.calls());
    }

    @Test
    void ifTheModelFailsFilesStayUnclassifiedAndTheUserGetsAWarning() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("scan0032.pdf");
        s.classifier(FakeLLMClient.failing("offline")).classify(inv);
        assertEquals(Category.UNCLASSIFIED, of(inv, "scan0032.pdf"));
        assertFalse(inv.getWarnings().isEmpty());
        assertTrue(inv.getWarnings().get(0).contains("offline"));
    }

    @Test
    void garbageRepliesAreRetriedOnceThenGivenUp() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("scan0032.pdf");
        FakeLLMClient llm = new FakeLLMClient(List.of("sorry, no json"));
        s.classifier(llm).classify(inv);
        assertEquals(2, llm.calls());
        assertEquals(Category.UNCLASSIFIED, of(inv, "scan0032.pdf"));
    }

    @Test
    void invalidCategoriesFromTheModelAreNotAccepted() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("scan0032.pdf");
        FakeLLMClient llm = new FakeLLMClient(List.of("{\"files\":[{\"id\":1,\"category\":\"SECRETS\"}]}"));
        s.classifier(llm).classify(inv);
        assertEquals(Category.UNCLASSIFIED, of(inv, "scan0032.pdf"));
    }

    @Test
    void manyAmbiguousFilesAreSentInBatchesOfTwenty() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 45; i++) names.add("scan" + i + ".pdf");
        Inventory inv = s.scan(names.toArray(new String[0]));
        FakeLLMClient llm = new FakeLLMClient(List.of("{\"files\":[]}"));
        s.classifier(llm).classify(inv);
        assertEquals(3, llm.calls());
    }

    @Test
    void snippetsSentToTheModelAreLimitedTo300Characters() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        com.sortbot.TestFiles.write(s.root, "mystery.txt", "y".repeat(5000));
        Inventory inv = new com.sortbot.core.FileScanner(s.gateway).scan(s.root, false);
        FakeLLMClient llm = new FakeLLMClient(List.of("{\"files\":[]}"));
        s.classifier(llm).classify(inv);
        assertFalse(llm.prompts().get(0).contains("y".repeat(301)));
        assertTrue(llm.prompts().get(0).contains("y".repeat(300)));
    }
}
