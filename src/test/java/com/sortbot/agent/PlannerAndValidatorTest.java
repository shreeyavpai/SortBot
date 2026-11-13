package com.sortbot.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sortbot.domain.Inventory;
import com.sortbot.domain.MoveFileAction;
import com.sortbot.domain.Plan;
import com.sortbot.domain.UserRule;
import com.sortbot.llm.FakeLLMClient;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlannerAndValidatorTest {
    @TempDir Path tmp;

    private static long moves(Plan p) {
        return p.getActions().stream().filter(a -> a instanceof MoveFileAction).count();
    }

    @Test
    void aValidModelReplyBecomesAPlanWithFoldersAndMoves() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("a.pdf", "b.pdf", "c.jpg");
        FakeLLMClient llm = new FakeLLMClient(List.of("```json\n{\"moves\":[{\"file\":\"a.pdf\",\"folder\":\"School\"},{\"file\":\"b.pdf\",\"folder\":\"School\"},{\"file\":\"c.jpg\",\"folder\":\"Pictures/Trips\"}]}\n```"));
        Plan plan = s.planner(llm).createPlan(inv, "tidy up");
        assertEquals(3, moves(plan));
        assertEquals(5, plan.size(), "two folders plus three moves");
        assertFalse(plan.isApproved());
        assertTrue(llm.prompts().get(0).contains("tidy up"));
    }

    @Test
    void unsafeOrInventedSuggestionsAreIgnoredWithWarnings() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("a.pdf", "b.pdf", "c.pdf", "d.pdf");
        String reply = "{\"moves\":[{\"file\":\"a.pdf\",\"folder\":\"../../Windows\"},{\"file\":\"b.pdf\",\"folder\":\"C:/Windows\"},"
                + "{\"file\":\"ghost.pdf\",\"folder\":\"X\"},{\"file\":\"c.pdf\",\"folder\":\"Good\"},{\"file\":\"c.pdf\",\"folder\":\"Other\"}]}";
        Plan plan = s.planner(new FakeLLMClient(List.of(reply))).createPlan(inv, "");
        assertEquals(1, moves(plan));
        assertEquals(4, plan.getWarnings().size());
    }

    @Test
    void malformedReplyIsRetriedOnceThenWorks() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("a.pdf");
        FakeLLMClient llm = new FakeLLMClient(List.of("oops", "{\"moves\":[{\"file\":\"a.pdf\",\"folder\":\"School\"}]}"));
        assertEquals(1, moves(s.planner(llm).createPlan(inv, "")));
        assertEquals(2, llm.calls());
        assertTrue(llm.prompts().get(1).contains("could not be read"));
    }

    @Test
    void twoMalformedRepliesGiveAClearErrorAndNoPlan() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("a.pdf");
        PlanningException e = assertThrows(PlanningException.class, () -> s.planner(new FakeLLMClient(List.of("oops"))).createPlan(inv, ""));
        assertTrue(e.getMessage().contains("could not be understood"));
    }

    @Test
    void anUnavailableModelGivesAClearError() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("a.pdf");
        PlanningException e = assertThrows(PlanningException.class, () -> s.planner(FakeLLMClient.failing("no network")).createPlan(inv, ""));
        assertTrue(e.getMessage().contains("unavailable"));
    }

    @Test
    void savedPlacementRulesDecideWithoutTheModelAndProtectRulesKeepFilesInPlace() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        s.memory.saveRule(UserRule.create(UserRule.Kind.PLACE_NAME, "tax", "Finance/Tax"));
        s.memory.saveRule(UserRule.create(UserRule.Kind.PROTECT_PATH, "Projects", ""));
        Inventory inv = s.scan("tax_2025.pdf", "Projects/keep.txt", "other.pdf");
        FakeLLMClient llm = new FakeLLMClient(List.of("{\"moves\":[{\"file\":\"other.pdf\",\"folder\":\"Misc\"}]}"));
        Plan plan = s.planner(llm).createPlan(inv, "");
        assertEquals(2, moves(plan));
        assertFalse(llm.prompts().get(0).contains("tax_2025.pdf"), "rule-placed files are not sent to the model");
        assertFalse(llm.prompts().get(0).contains("keep.txt"), "protected files are not sent to the model");
        assertTrue(plan.getActions().stream().noneMatch(a -> a.source() != null && a.source().getFileName().toString().equals("keep.txt")));
    }

    @Test
    void filesAlreadyInTheirTargetFolderAreNotMoved() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("School/a.pdf");
        Plan plan = s.planner(new FakeLLMClient(List.of("{\"moves\":[{\"file\":\"School/a.pdf\",\"folder\":\"School\"}]}"))).createPlan(inv, "");
        assertEquals(0, plan.size());
    }

    @Test
    void validatorRemovesUnknownOutsideRepeatedAndProtectedActions() throws IOException {
        AgentTestSupport s = new AgentTestSupport(tmp);
        Inventory inv = s.scan("a.pdf", "Projects/p.txt");
        Path a = inv.filter(r -> r.getName().equals("a.pdf")).get(0).getPath();
        Path p = inv.filter(r -> r.getName().equals("p.txt")).get(0).getPath();
        Plan plan = new Plan();
        plan.add(new MoveFileAction(s.gateway, a, s.root.resolve("X/a.pdf")));
        plan.add(new MoveFileAction(s.gateway, a, s.root.resolve("Y/a.pdf")));
        plan.add(new MoveFileAction(s.gateway, s.root.resolve("ghost.pdf"), s.root.resolve("X/ghost.pdf")));
        plan.add(new MoveFileAction(s.gateway, p, s.root.resolve("X/p.txt")));
        plan.add(new MoveFileAction(s.gateway, a, tmp.resolve("Outside/a.pdf")));
        ValidationResult v = s.validator.validate(plan, inv, List.of(UserRule.create(UserRule.Kind.PROTECT_PATH, "Projects", "")));
        assertEquals(4, v.removed());
        assertEquals(1, plan.size());
        assertFalse(v.isClean());
    }
}
