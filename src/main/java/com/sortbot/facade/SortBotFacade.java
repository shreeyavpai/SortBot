package com.sortbot.facade;

import com.sortbot.agent.AgentController;
import com.sortbot.agent.AgentSession;
import com.sortbot.agent.ChatReply;
import com.sortbot.agent.Classifier;
import com.sortbot.agent.FilterInventoryTool;
import com.sortbot.agent.FindCleanupTool;
import com.sortbot.agent.GeneratePlanTool;
import com.sortbot.agent.LLMClassificationStrategy;
import com.sortbot.agent.ListRulesTool;
import com.sortbot.agent.NameRuleStrategy;
import com.sortbot.agent.Planner;
import com.sortbot.agent.PlanValidator;
import com.sortbot.agent.PromptBuilder;
import com.sortbot.agent.ProposeRuleTool;
import com.sortbot.agent.ResponseParser;
import com.sortbot.agent.ToolManager;
import com.sortbot.core.ActionEntry;
import com.sortbot.core.ActionLog;
import com.sortbot.core.AgeRule;
import com.sortbot.core.CleanupCandidate;
import com.sortbot.core.CleanupDetector;
import com.sortbot.core.DuplicateRule;
import com.sortbot.core.FileScanner;
import com.sortbot.core.FolderAuthorizer;
import com.sortbot.core.LogFilter;
import com.sortbot.core.MemoryManager;
import com.sortbot.core.PlanExecutor;
import com.sortbot.core.ProgressListener;
import com.sortbot.core.ReportBuilder;
import com.sortbot.core.RuleRepository;
import com.sortbot.core.TempFileRule;
import com.sortbot.core.TraceLogger;
import com.sortbot.core.UndoManager;
import com.sortbot.domain.Inventory;
import com.sortbot.domain.Plan;
import com.sortbot.domain.RunResult;
import com.sortbot.domain.UndoResult;
import com.sortbot.domain.UserRule;
import com.sortbot.infra.FileSystemGateway;
import com.sortbot.llm.LLMClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Facade pattern: the single entry point used by both the GUI and the CLI. It wires every subsystem
 * together so the user interfaces never need to know about the classes behind it.
 */
public class SortBotFacade {
    private final FolderAuthorizer authorizer;
    private final FileSystemGateway gateway;
    private final FileScanner scanner;
    private final Classifier classifier;
    private final Planner planner;
    private final PlanExecutor executor;
    private final UndoManager undoManager;
    private final ActionLog log;
    private final TraceLogger trace;
    private final MemoryManager memory;
    private final CleanupDetector cleanup;
    private final ReportBuilder reports = new ReportBuilder();
    private final AgentController controller;
    private final AgentSession session = new AgentSession();

    private SortBotFacade(Path dataDir, LLMClient llm, int cleanupMonths) {
        authorizer = new FolderAuthorizer(dataDir.resolve("folders.txt"));
        gateway = new FileSystemGateway(authorizer);
        log = new ActionLog(dataDir.resolve("actionlog.jsonl"));
        trace = new TraceLogger(dataDir.resolve("trace.jsonl"));
        memory = new MemoryManager(new RuleRepository(dataDir.resolve("rules.jsonl")));
        scanner = new FileScanner(gateway);
        executor = new PlanExecutor(log, trace);
        undoManager = new UndoManager(log, gateway, trace);

        PromptBuilder prompts = new PromptBuilder();
        ResponseParser parser = new ResponseParser();
        classifier = new Classifier(List.of(new NameRuleStrategy(), new LLMClassificationStrategy(llm, prompts, parser, gateway)));
        planner = new Planner(llm, prompts, parser, new PlanValidator(authorizer), memory, gateway, trace);
        cleanup = new CleanupDetector(List.of(new AgeRule(cleanupMonths), new DuplicateRule(gateway), new TempFileRule()));
        cleanup.setProtectionFilter(r -> memory.getRules().stream().anyMatch(rule -> rule.getKind() == UserRule.Kind.PROTECT_PATH && rule.matches(r)));

        ToolManager tools = new ToolManager(trace);
        tools.register(new FilterInventoryTool(session));
        tools.register(new GeneratePlanTool(session, planner));
        tools.register(new FindCleanupTool(session, cleanup));
        tools.register(new ListRulesTool(memory));
        tools.register(new ProposeRuleTool(session, memory));
        controller = new AgentController(planner, tools, trace);
    }

    public static SortBotFacade create(Path dataDir, LLMClient llm, int cleanupMonths) {
        return new SortBotFacade(dataDir, llm, cleanupMonths);
    }

    // F01 folders
    public void authorizeFolder(Path path) { authorizer.add(path); }
    public boolean removeFolder(Path path) { return authorizer.remove(path); }
    public List<Path> getAllowedFolders() { return authorizer.getAllowedFolders(); }

    // F02, F03 scanning and classification
    public Inventory scanFolder(Path folder, boolean includeSubfolders) throws IOException {
        Inventory inv = scanner.scan(folder, includeSubfolders);
        session.inventory = inv;
        session.clearPending();
        return inv;
    }

    /** Lets the user correct a file's category by hand. */
    public void setCategory(com.sortbot.domain.FileRecord record, com.sortbot.domain.Category category) {
        record.classify(category, 1.0, "Set by you");
    }

    public Inventory classify(Inventory inventory) {
        session.inventory = inventory;
        return classifier.classify(inventory);
    }

    // F04, F05 planning and review
    public Plan generatePlan(Inventory inventory, String instruction) {
        session.inventory = inventory;
        Plan plan = planner.createPlan(inventory, instruction);
        session.pendingPlan = plan;
        return plan;
    }

    public void editPlan(Plan plan, String actionId, Path newTarget) {
        if (!authorizer.isAuthorized(newTarget)) throw new IllegalArgumentException("That destination is outside the authorized folders.");
        plan.edit(actionId, newTarget);
        ensureFolderActions(plan, newTarget.toAbsolutePath().normalize().getParent());
    }

    /** Makes sure every folder level of an edited destination has its own CreateFolderAction, placed before the moves. */
    private void ensureFolderActions(Plan plan, Path folder) {
        if (session.inventory == null || folder == null || !folder.startsWith(session.inventory.getRoot())) return;
        java.util.Set<Path> existing = new java.util.HashSet<>();
        for (var a : plan.getActions()) if (a instanceof com.sortbot.domain.CreateFolderAction c) existing.add(c.target().toAbsolutePath().normalize());
        Path current = session.inventory.getRoot();
        int at = 0;
        for (Path part : current.relativize(folder)) {
            current = current.resolve(part);
            if (!existing.contains(current)) plan.insert(at, new com.sortbot.domain.CreateFolderAction(gateway, current));
            at++;
        }
    }

    public boolean rejectMove(Plan plan, String actionId) { return plan.remove(actionId); }

    // F06 execution
    public RunResult executePlan(Plan plan) {
        if (!plan.isApproved()) plan.approve();
        RunResult r = executor.execute(plan);
        if (session.pendingPlan == plan) session.pendingPlan = null;
        return r;
    }

    public void cancel() { executor.cancel(); }

    // F07 undo
    public UndoResult undo(String runId) { return undoManager.undoRun(runId); }
    public UndoResult undoLast() { return undoManager.undoLast(); }
    public String lastUndoableRunId() { return log.lastUndoableRunId(); }

    // F08 cleanup
    public List<CleanupCandidate> findCleanup(Inventory inventory) {
        session.inventory = inventory;
        List<CleanupCandidate> found = cleanup.detect(inventory);
        session.pendingCleanup = found;
        return found;
    }

    public RunResult confirmCleanup(List<CleanupCandidate> chosen) {
        if (session.inventory == null) throw new IllegalStateException("Scan a folder first.");
        Plan plan = cleanup.toPlan(chosen, session.inventory.getRoot(), gateway);
        plan.approve();
        session.pendingCleanup = null;
        return executor.execute(plan);
    }

    // F09 chat
    public ChatReply handleChat(String message) { return controller.handleChat(message, session); }

    // F10 rules
    public UserRule saveRule(UserRule rule) { return memory.saveRule(rule); }
    public UserRule confirmPendingRule() {
        if (session.pendingRule == null) throw new IllegalStateException("There is no proposed rule to confirm.");
        UserRule saved = memory.saveRule(session.pendingRule);
        session.pendingRule = null;
        return saved;
    }
    public List<UserRule> getRules() { return memory.getRules(); }
    public boolean removeRule(String id) { return memory.removeRule(id); }

    // F11 history and reports
    public List<ActionEntry> getHistory(LogFilter filter) { return log.query(filter); }

    public void exportReport(ReportBuilder.Format format, Path out, LogFilter filter) throws IOException {
        reports.write(log.query(filter), format, out);
    }

    public void subscribe(ProgressListener listener) {
        scanner.subscribe(listener);
        executor.subscribe(listener);
    }

    public AgentSession session() { return session; }
    public TraceLogger trace() { return trace; }
}
