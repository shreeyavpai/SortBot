package com.sortbot.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.core.CleanupDetector;
import com.sortbot.core.TempFileRule;
import com.sortbot.llm.FakeLLMClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentControllerTest {
    @TempDir Path tmp;
    AgentTestSupport s;
    AgentSession session;

    @BeforeEach
    void setUp() throws IOException {
        s = new AgentTestSupport(tmp);
        session = new AgentSession();
    }

    private AgentController controller(FakeLLMClient llm) {
        Planner planner = s.planner(llm);
        ToolManager tm = new ToolManager(s.trace);
        tm.register(new FilterInventoryTool(session));
        tm.register(new GeneratePlanTool(session, planner));
        tm.register(new FindCleanupTool(session, new CleanupDetector(List.of(new TempFileRule()))));
        tm.register(new ListRulesTool(s.memory));
        tm.register(new ProposeRuleTool(session, s.memory));
        return new AgentController(planner, tm, s.trace);
    }

    @Test
    void deleteRequestsAreRefusedWithoutAskingTheModel() throws IOException {
        session.inventory = s.scan("a.pdf");
        FakeLLMClient llm = new FakeLLMClient(List.of("{\"tool\":\"generate_plan\",\"args\":{\"instruction\":\"x\"}}"));
        ChatReply r = controller(llm).handleChat("please delete all my old files", session);
        assertTrue(r.text().contains("never deletes"));
        assertEquals(0, llm.calls());
        assertTrue(Files.exists(s.root.resolve("a.pdf")));
    }

    @Test
    void withoutAScannedFolderTheAgentAsksForOne() {
        ChatReply r = controller(new FakeLLMClient(List.of("{}"))).handleChat("organize things", session);
        assertTrue(r.text().contains("Scan a folder first"));
    }

    @Test
    void aValidToolCallProducesAPlanButChangesNoFiles() throws IOException {
        session.inventory = s.scan("tax.pdf");
        FakeLLMClient llm = new FakeLLMClient(List.of(
                "{\"tool\":\"generate_plan\",\"args\":{\"instruction\":\"put tax documents in one folder\"}}",
                "{\"moves\":[{\"file\":\"tax.pdf\",\"folder\":\"Tax\"}]}"));
        ChatReply r = controller(llm).handleChat("put my tax documents in one folder", session);
        assertNotNull(r.plan());
        assertEquals(2, r.plan().size());
        assertFalse(r.plan().isApproved());
        assertTrue(Files.exists(s.root.resolve("tax.pdf")), "chat alone must never move files");
        assertEquals(r.plan(), session.pendingPlan);
    }

    @Test
    void unknownToolsAreRejected() throws IOException {
        session.inventory = s.scan("a.pdf");
        ChatReply r = controller(new FakeLLMClient(List.of("{\"tool\":\"format_disk\",\"args\":{}}"))).handleChat("do it", session);
        assertTrue(r.text().contains("no tool called"));
        assertNull(r.plan());
    }

    @Test
    void missingArgumentsAreRejected() throws IOException {
        session.inventory = s.scan("a.pdf");
        ChatReply r = controller(new FakeLLMClient(List.of("{\"tool\":\"generate_plan\",\"args\":{}}"))).handleChat("organize", session);
        assertTrue(r.text().contains("missing: instruction"));
    }

    @Test
    void ambiguousRequestsGetAClarifyingQuestion() throws IOException {
        session.inventory = s.scan("a.pdf");
        ChatReply r = controller(new FakeLLMClient(List.of("{\"clarify\":\"Which files do you mean?\"}"))).handleChat("fix it", session);
        assertEquals("Which files do you mean?", r.text());
    }

    @Test
    void unreadableOrUnavailableModelsGiveFriendlyMessages() throws IOException {
        session.inventory = s.scan("a.pdf");
        assertTrue(controller(new FakeLLMClient(List.of("blah"))).handleChat("organize", session).text().contains("rephrase"));
        assertTrue(controller(FakeLLMClient.failing("offline")).handleChat("organize", session).text().contains("unavailable"));
    }

    @Test
    void filterToolListsMatchingFilesAndValidatesItsArguments() throws IOException {
        session.inventory = s.scan("a.pdf", "b.pdf", "c.txt");
        FilterInventoryTool t = new FilterInventoryTool(session);
        assertTrue(t.run(java.util.Map.of("ext", ".pdf")).message().startsWith("Found 2"));
        assertFalse(t.run(java.util.Map.of()).ok());
        assertFalse(t.run(java.util.Map.of("older_than_months", "abc")).ok());
        assertFalse(t.run(java.util.Map.of("category", "nonsense")).ok());
        assertTrue(t.run(java.util.Map.of("name_contains", "zzz")).message().contains("No files"));
    }

    @Test
    void proposedRulesWaitForConfirmationAndBadOnesAreRejected() throws IOException {
        session.inventory = s.scan("a.pdf");
        ChatReply r = controller(new FakeLLMClient(List.of("{\"tool\":\"propose_rule\",\"args\":{\"kind\":\"place_name\",\"match\":\"lecture\",\"folder\":\"School\"}}")))
                .handleChat("always put lecture slides under School", session);
        assertNotNull(r.proposedRule());
        assertTrue(s.memory.getRules().isEmpty(), "nothing is saved until the user confirms");
        ChatReply bad = controller(new FakeLLMClient(List.of("{\"tool\":\"propose_rule\",\"args\":{\"kind\":\"place_name\",\"match\":\"a\",\"folder\":\"School\"}}")))
                .handleChat("rule please", session);
        assertNull(bad.proposedRule());
        assertTrue(bad.text().contains("too broad"));
    }

    @Test
    void cleanupToolReportsCandidatesWithoutMovingAnything() throws IOException {
        session.inventory = s.scan("x.tmp", "keep.txt");
        ChatReply r = controller(new FakeLLMClient(List.of("{\"tool\":\"find_cleanup\",\"args\":{}}"))).handleChat("what can I clean up", session);
        assertEquals(1, r.cleanup().size());
        assertTrue(Files.exists(s.root.resolve("x.tmp")));
    }

    @Test
    void everyToolCallIsTraced() throws IOException {
        session.inventory = s.scan("a.pdf");
        controller(new FakeLLMClient(List.of("{\"tool\":\"list_rules\",\"args\":{}}"))).handleChat("show rules", session);
        assertTrue(s.trace.events().stream().anyMatch(e -> "tool_call".equals(e.get("event")) && "list_rules".equals(e.get("tool"))));
    }
}
