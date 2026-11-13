package com.sortbot.agent;

import com.sortbot.core.FolderNames;
import com.sortbot.core.MemoryManager;
import com.sortbot.core.TraceLogger;
import com.sortbot.domain.CreateFolderAction;
import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;
import com.sortbot.domain.MoveFileAction;
import com.sortbot.domain.Plan;
import com.sortbot.domain.UserRule;
import com.sortbot.infra.FileSystemGateway;
import com.sortbot.llm.LLMClient;
import com.sortbot.llm.LLMException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an inventory and the user's wishes into a Plan (F04). The AI proposes destination folders;
 * saved rules decide first; the PlanValidator checks the result. Nothing here changes any file.
 */
public class Planner {
    static final int PLAN_BATCH = 60;

    private final LLMClient llm;
    private final PromptBuilder prompts;
    private final ResponseParser parser;
    private final PlanValidator validator;
    private final MemoryManager memory;
    private final FileSystemGateway gateway;
    private final TraceLogger trace;

    public Planner(LLMClient llm, PromptBuilder prompts, ResponseParser parser, PlanValidator validator,
                   MemoryManager memory, FileSystemGateway gateway, TraceLogger trace) {
        this.llm = llm;
        this.prompts = prompts;
        this.parser = parser;
        this.validator = validator;
        this.memory = memory;
        this.gateway = gateway;
        this.trace = trace;
    }

    public Plan createPlan(Inventory inventory, String instruction) {
        List<UserRule> rules = memory.getRules();
        Path root = inventory.getRoot();
        Plan plan = new Plan();
        Map<String, List<FileRecord>> byFolder = new LinkedHashMap<>();
        Map<String, FileRecord> byId = new HashMap<>();
        List<FileRecord> forAi = new ArrayList<>();

        for (FileRecord r : inventory.getRecords()) {
            if (anyMatch(rules, UserRule.Kind.PROTECT_PATH, r)) continue;
            String placed = placement(rules, r);
            if (placed != null) addTo(byFolder, placed, r);
            else {
                forAi.add(r);
                byId.put(PromptBuilder.fileId(root, r), r);
            }
        }

        Map<FileRecord, Boolean> assigned = new HashMap<>();
        byFolder.values().forEach(l -> l.forEach(r -> assigned.put(r, true)));
        for (int i = 0; i < forAi.size(); i += PLAN_BATCH) {
            List<FileRecord> batch = forAi.subList(i, Math.min(forAi.size(), i + PLAN_BATCH));
            String prompt = prompts.buildPlanPrompt(batch, root, rules, instruction);
            List<PlannedMove> moves;
            try {
                moves = LlmAsk.ask(llm, prompt, parser::parseMoves);
            } catch (LLMException e) {
                throw new PlanningException("The AI model is unavailable: " + e.getMessage());
            } catch (AgentParseException e) {
                throw new PlanningException("The AI model's reply could not be understood, even after a retry. Please try again.");
            }
            for (PlannedMove m : moves) {
                FileRecord r = byId.get(m.file());
                String folder = FolderNames.sanitize(m.folder());
                if (r == null) plan.addWarning("Ignored a suggestion for an unknown file: " + m.file());
                else if (folder == null) plan.addWarning("Ignored an unsafe folder suggestion for " + r.getName() + ": " + m.folder());
                else if (assigned.putIfAbsent(r, true) != null) plan.addWarning("Ignored a second suggestion for " + r.getName());
                else addTo(byFolder, folder, r);
            }
        }

        for (Map.Entry<String, List<FileRecord>> e : byFolder.entrySet()) {
            Path folder = root.resolve(e.getKey());
            List<FileRecord> moving = new ArrayList<>();
            for (FileRecord r : e.getValue()) if (!folder.equals(r.getPath().getParent())) moving.add(r);
            if (moving.isEmpty()) continue;
            plan.add(new CreateFolderAction(gateway, folder));
            for (FileRecord r : moving) plan.add(new MoveFileAction(gateway, r.getPath(), folder.resolve(r.getName())));
        }

        ValidationResult v = validator.validate(plan, inventory, rules);
        v.problems().forEach(plan::addWarning);
        trace.log("plan_created", Map.of("actions", Integer.toString(plan.size()), "warnings", Integer.toString(plan.getWarnings().size())));
        return plan;
    }

    /** Asks the model what the user wants and which tool to run. Throws if the model is unreachable or unreadable. */
    public Intent interpretIntent(String message, String toolDescriptions, String stateSummary) throws LLMException, AgentParseException {
        String prompt = prompts.buildIntentPrompt(message, toolDescriptions, stateSummary, memory.getRules());
        return LlmAsk.ask(llm, prompt, parser::parseIntent);
    }

    private static void addTo(Map<String, List<FileRecord>> map, String folder, FileRecord r) {
        map.computeIfAbsent(folder, k -> new ArrayList<>()).add(r);
    }

    private static boolean anyMatch(List<UserRule> rules, UserRule.Kind kind, FileRecord r) {
        for (UserRule rule : rules) if (rule.getKind() == kind && rule.matches(r)) return true;
        return false;
    }

    private static String placement(List<UserRule> rules, FileRecord r) {
        for (UserRule rule : rules) {
            if (rule.isPlacement() && rule.matches(r)) {
                String f = FolderNames.sanitize(rule.getFolder());
                if (f != null) return f;
            }
        }
        return null;
    }
}
