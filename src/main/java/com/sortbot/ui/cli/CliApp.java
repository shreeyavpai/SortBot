package com.sortbot.ui.cli;

import com.sortbot.agent.ChatReply;
import com.sortbot.agent.PlanningException;
import com.sortbot.core.ActionEntry;
import com.sortbot.core.CleanupCandidate;
import com.sortbot.core.FolderRejectedException;
import com.sortbot.core.LogFilter;
import com.sortbot.core.NotAuthorizedException;
import com.sortbot.core.ReportBuilder;
import com.sortbot.domain.FileAction;
import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;
import com.sortbot.domain.Plan;
import com.sortbot.domain.RunResult;
import com.sortbot.domain.UndoResult;
import com.sortbot.domain.UserRule;
import com.sortbot.facade.SortBotFacade;
import com.sortbot.infra.DotEnv;
import com.sortbot.infra.Json;
import com.sortbot.llm.JdkHttpTransport;
import com.sortbot.llm.LLMClient;
import com.sortbot.llm.LLMClientCreator;
import com.sortbot.llm.LLMConfig;
import com.sortbot.llm.LLMException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Command-line interface. Every command works in plain text or, with --json, prints one JSON
 * object (used by scripts and by the behavioral test harness). All work goes through SortBotFacade.
 */
public class CliApp {
    static final String USAGE = String.join("\n",
            "SortBot: an AI agent that safely organizes folders.",
            "",
            "Usage: sortbot <command> [options]",
            "  folders list | add <path> | remove <path>   Manage the folders SortBot may touch",
            "  scan <folder> [--recursive]                  List the files in a folder",
            "  classify <folder> [--recursive]              Categorize files (rules first, then the AI)",
            "  plan <folder> [--instruction \"...\"]          Propose an organization plan (changes nothing)",
            "  organize <folder> [--instruction \"...\"] [--yes]   Plan, then apply after your approval",
            "  cleanup <folder> [--months N] [--yes]        Find old/duplicate/temp files; move them to To_Be_Deleted",
            "  chat <folder> [--message \"...\"] [--yes]      Talk to the agent in plain language",
            "  undo [runId]                                 Undo the last run (or a given run)",
            "  history [--run <id>]                         Show what SortBot has done",
            "  export <file> [--format csv|md]              Save the history as a report",
            "  rules list | add <kind> <match> [folder] | remove <id>",
            "        kinds: place_category, place_name, protect_path",
            "",
            "Options: --json (machine-readable output), --data-dir <dir>, --env <file>, --recursive, --yes, --help",
            "SortBot never deletes files. The AI proposes, you approve, and every action can be undone.");

    private static final Set<String> BOOLEAN_FLAGS = Set.of("recursive", "yes", "json", "help");

    private record Result(boolean ok, String text, Map<String, Object> data, int code) {}

    private static final class Args {
        String command;
        final List<String> positional = new ArrayList<>();
        final Map<String, String> flags = new LinkedHashMap<>();

        static Args parse(String[] argv) {
            Args a = new Args();
            for (int i = 0; i < argv.length; i++) {
                String t = argv[i];
                if (t.startsWith("--")) {
                    String name = t.substring(2);
                    int eq = name.indexOf('=');
                    if (eq > 0) a.flags.put(name.substring(0, eq), name.substring(eq + 1));
                    else if (BOOLEAN_FLAGS.contains(name) || i + 1 >= argv.length) a.flags.put(name, "true");
                    else a.flags.put(name, argv[++i]);
                } else if (a.command == null) a.command = t;
                else a.positional.add(t);
            }
            return a;
        }

        boolean has(String f) { return flags.containsKey(f); }
        String flag(String f, String def) { return flags.getOrDefault(f, def); }
        String pos(int i) { return i < positional.size() ? positional.get(i) : null; }
    }

    private final BufferedReader in;
    private final PrintStream out;
    private final LLMClient llmOverride;
    private boolean json;

    public CliApp(InputStream in, PrintStream out, LLMClient llmOverride) {
        this.in = new BufferedReader(new InputStreamReader(in));
        this.out = out;
        this.llmOverride = llmOverride;
    }

    public int run(String[] argv) {
        Args a = Args.parse(argv);
        json = a.has("json");
        if (a.command == null || a.command.equals("help") || a.has("help")) {
            out.println(USAGE);
            return 0;
        }
        int months;
        try {
            months = Integer.parseInt(a.flag("months", "12"));
        } catch (NumberFormatException e) {
            return print(a, fail("--months must be a whole number.", 2));
        }
        Path dataDir = Path.of(a.flag("data-dir", System.getProperty("user.home") + java.io.File.separator + ".sortbot"));
        SortBotFacade facade;
        try {
            facade = SortBotFacade.create(dataDir, llm(a), months);
        } catch (IllegalArgumentException e) {
            return print(a, fail(e.getMessage(), 2));
        }
        if (!json) facade.subscribe(new CliProgressPrinter(out));

        Result r;
        try {
            r = dispatch(a, facade);
        } catch (NotAuthorizedException e) {
            r = fail("That folder is not authorized. Run: folders add <path>", 1);
        } catch (FolderRejectedException | PlanningException | IllegalArgumentException | IllegalStateException e) {
            r = fail(e.getMessage(), 1);
        } catch (IOException e) {
            r = fail("File problem: " + e.getMessage(), 1);
        } catch (RuntimeException e) {
            r = fail("Unexpected error: " + e, 1);
        }
        return print(a, r);
    }

    private LLMClient llm(Args a) {
        if (llmOverride != null) return llmOverride;
        LLMConfig cfg = LLMConfig.fromEnv(new DotEnv(Path.of(a.flag("env", ".env"))));
        try {
            return LLMClientCreator.forConfig(cfg, new JdkHttpTransport()).createClient();
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            return prompt -> { throw new LLMException(msg); };
        }
    }

    private int print(Args a, Result r) {
        if (json) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("command", a.command);
            o.put("ok", r.ok);
            o.put("message", r.text);
            o.put("data", r.data);
            out.println(Json.write(o));
        } else {
            out.println(r.text);
        }
        return r.ok ? 0 : r.code;
    }

    private static Result ok(String text, Map<String, Object> data) { return new Result(true, text, data, 0); }
    private static Result fail(String text, int code) { return new Result(false, text, new LinkedHashMap<>(), code); }

    private Result dispatch(Args a, SortBotFacade f) throws IOException {
        return switch (a.command) {
            case "folders" -> folders(a, f);
            case "scan" -> scan(a, f);
            case "classify" -> classify(a, f);
            case "plan" -> plan(a, f, false);
            case "organize" -> plan(a, f, true);
            case "cleanup" -> cleanup(a, f);
            case "chat" -> chat(a, f);
            case "undo" -> undo(a, f);
            case "history" -> history(a, f);
            case "export" -> export(a, f);
            case "rules" -> rules(a, f);
            default -> fail("Unknown command \"" + a.command + "\". Run with --help to see the commands.", 2);
        };
    }

    private Result folders(Args a, SortBotFacade f) {
        String sub = a.pos(0) == null ? "list" : a.pos(0);
        Map<String, Object> data = new LinkedHashMap<>();
        switch (sub) {
            case "add" -> {
                if (a.pos(1) == null) return fail("Usage: folders add <path>", 2);
                f.authorizeFolder(Path.of(a.pos(1)));
                data.put("folders", paths(f));
                return ok("Authorized: " + a.pos(1), data);
            }
            case "remove" -> {
                if (a.pos(1) == null) return fail("Usage: folders remove <path>", 2);
                boolean removed = f.removeFolder(Path.of(a.pos(1)));
                data.put("folders", paths(f));
                return removed ? ok("Removed: " + a.pos(1), data) : fail("That folder was not on the list.", 1);
            }
            case "list" -> {
                List<String> p = paths(f);
                data.put("folders", p);
                return ok(p.isEmpty() ? "No folders are authorized yet. Add one with: folders add <path>" : "Authorized folders:\n  " + String.join("\n  ", p), data);
            }
            default -> {
                return fail("Usage: folders list | add <path> | remove <path>", 2);
            }
        }
    }

    private static List<String> paths(SortBotFacade f) {
        List<String> p = new ArrayList<>();
        f.getAllowedFolders().forEach(x -> p.add(x.toString()));
        return p;
    }

    private Inventory scanOnly(Args a, SortBotFacade f) throws IOException {
        if (a.pos(0) == null) throw new IllegalArgumentException("Please give a folder, for example: " + a.command + " C:\\Users\\me\\Downloads");
        return f.scanFolder(Path.of(a.pos(0)), a.has("recursive"));
    }

    private Result scan(Args a, SortBotFacade f) throws IOException {
        Inventory inv = scanOnly(a, f);
        return ok("Scanned " + inv.size() + " file(s) in " + inv.getRoot() + warnings(inv), inventoryData(inv));
    }

    private Result classify(Args a, SortBotFacade f) throws IOException {
        Inventory inv = f.classify(scanOnly(a, f));
        StringBuilder sb = new StringBuilder("Classified " + inv.size() + " file(s):");
        for (FileRecord r : inv.getRecords()) sb.append("\n  ").append(r.getName()).append("  ->  ").append(r.getCategory()).append(" (").append(Math.round(r.getConfidence() * 100)).append("%)");
        return ok(sb + warnings(inv), inventoryData(inv));
    }

    private Result plan(Args a, SortBotFacade f, boolean apply) throws IOException {
        Inventory inv = f.classify(scanOnly(a, f));
        Plan plan = f.generatePlan(inv, a.flag("instruction", ""));
        Map<String, Object> data = planData(plan);
        String shown = describePlan(plan) + warnings(inv);
        if (!apply || plan.size() == 0) {
            data.put("applied", false);
            return ok(shown + (apply ? "" : "\n\nNothing was changed. Use 'organize' to apply a plan."), data);
        }
        if (!a.has("yes") && (json || !confirm(shown + "\n\nApply this plan?"))) {
            data.put("applied", false);
            return ok(shown + "\n\nNot applied. Nothing was changed.", data);
        }
        RunResult run = f.executePlan(plan);
        data.put("applied", true);
        data.put("runId", run.runId());
        data.put("done", run.done());
        data.put("failed", run.failed());
        data.put("skipped", run.skipped());
        return ok("Applied (run " + run.runId() + "): " + run.summary() + failures(run) + "\nUndo with: undo " + run.runId(), data);
    }

    private Result cleanup(Args a, SortBotFacade f) throws IOException {
        Inventory inv = scanOnly(a, f);
        List<CleanupCandidate> found = f.findCleanup(inv);
        Map<String, Object> data = candidateData(found);
        if (found.isEmpty()) return ok("Nothing looks like clutter.", data);
        String shown = describeCandidates(found);
        if (!a.has("yes") && (json || !confirm(shown + "\n\nMove these files to To_Be_Deleted? (nothing is deleted)"))) {
            data.put("applied", false);
            return ok(shown + "\n\nNot applied. Nothing was changed.", data);
        }
        RunResult run = f.confirmCleanup(found);
        data.put("applied", true);
        data.put("runId", run.runId());
        return ok("Moved to To_Be_Deleted (run " + run.runId() + "): " + run.summary() + failures(run) + "\nUndo with: undo " + run.runId(), data);
    }

    private Result chat(Args a, SortBotFacade f) throws IOException {
        Inventory inv = f.classify(scanOnly(a, f));
        String message = a.flag("message", null);
        if (message != null) {
            ChatReply reply = f.handleChat(message);
            Map<String, Object> data = replyData(reply);
            StringBuilder sb = new StringBuilder(describeReply(reply));
            if (a.has("yes") && reply.plan() != null && reply.plan().size() > 0) {
                RunResult run = f.executePlan(reply.plan());
                data.put("applied", true);
                data.put("runId", run.runId());
                sb.append("\nApplied (run ").append(run.runId()).append("): ").append(run.summary());
            }
            return ok(sb.toString(), data);
        }
        out.println("SortBot chat for " + inv.getRoot() + " (" + inv.size() + " files). Type a request, or /help.");
        try {
            String line;
            while (true) {
                out.print("> ");
                out.flush();
                line = in.readLine();
                if (line == null || line.trim().equals("/quit")) break;
                out.println(replLine(line.trim(), f));
            }
        } catch (IOException e) {
            return fail("Could not read input.", 1);
        }
        return ok("Goodbye.", new LinkedHashMap<>());
    }

    private String replLine(String line, SortBotFacade f) {
        if (line.isEmpty()) return "";
        try {
            switch (line) {
                case "/help": return "Commands: /show (pending plan), /approve (apply the plan), /cleanup (move the cleanup list), /rule (save the proposed rule), /undo, /quit. Anything else is sent to the agent.";
                case "/show": return f.session().pendingPlan == null ? "There is no pending plan." : describePlan(f.session().pendingPlan);
                case "/approve": {
                    if (f.session().pendingPlan == null) return "There is no pending plan.";
                    RunResult run = f.executePlan(f.session().pendingPlan);
                    return "Applied (run " + run.runId() + "): " + run.summary() + failures(run);
                }
                case "/cleanup": {
                    if (f.session().pendingCleanup == null || f.session().pendingCleanup.isEmpty()) return "There is no pending cleanup list.";
                    RunResult run = f.confirmCleanup(f.session().pendingCleanup);
                    return "Moved to To_Be_Deleted (run " + run.runId() + "): " + run.summary() + failures(run);
                }
                case "/rule": return "Saved: " + f.confirmPendingRule().describe();
                case "/undo": {
                    UndoResult u = f.undoLast();
                    return u.nothingToUndo() ? "Nothing to undo." : "Undone: " + u.summary();
                }
                default: return describeReply(f.handleChat(line));
            }
        } catch (RuntimeException e) {
            return "Problem: " + e.getMessage();
        }
    }

    private Result undo(Args a, SortBotFacade f) {
        String runId = a.pos(0) != null ? a.pos(0) : f.lastUndoableRunId();
        if (runId == null) return ok("Nothing to undo.", new LinkedHashMap<>());
        UndoResult u = f.undo(runId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", runId);
        data.put("undone", u.undone());
        data.put("skipped", u.skipped());
        data.put("messages", u.messages());
        if (u.nothingToUndo()) return ok("Run " + runId + " has nothing left to undo.", data);
        return ok("Undo of " + runId + ": " + u.summary() + (u.messages().isEmpty() ? "" : "\n  " + String.join("\n  ", u.messages())), data);
    }

    private Result history(Args a, SortBotFacade f) {
        LogFilter filter = LogFilter.all();
        if (a.has("run")) filter.runId(a.flag("run", null));
        List<ActionEntry> entries = f.getHistory(filter);
        List<Map<String, Object>> rows = new ArrayList<>();
        StringBuilder sb = new StringBuilder(entries.isEmpty() ? "No history yet." : entries.size() + " action(s):");
        for (ActionEntry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>(e.toMap());
            rows.add(m);
            sb.append("\n  ").append(e.getTimestamp()).append("  ").append(e.getRunId()).append("  ").append(e.getType()).append("  ").append(e.getStatus()).append("  ").append(e.getSource().isEmpty() ? e.getTarget() : e.getSource() + " -> " + e.getTarget());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entries", rows);
        return ok(sb.toString(), data);
    }

    private Result export(Args a, SortBotFacade f) throws IOException {
        if (a.pos(0) == null) return fail("Usage: export <file> [--format csv|md]", 2);
        String fmt = a.flag("format", a.pos(0).toLowerCase().endsWith(".md") ? "md" : "csv").toLowerCase();
        ReportBuilder.Format format = fmt.startsWith("md") || fmt.startsWith("mark") ? ReportBuilder.Format.MARKDOWN : ReportBuilder.Format.CSV;
        LogFilter filter = LogFilter.all();
        if (a.has("run")) filter.runId(a.flag("run", null));
        f.exportReport(format, Path.of(a.pos(0)), filter);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("file", a.pos(0));
        return ok("Report saved to " + a.pos(0), data);
    }

    private Result rules(Args a, SortBotFacade f) {
        String sub = a.pos(0) == null ? "list" : a.pos(0);
        Map<String, Object> data = new LinkedHashMap<>();
        switch (sub) {
            case "list" -> {
                List<Map<String, String>> rows = new ArrayList<>();
                StringBuilder sb = new StringBuilder();
                for (UserRule r : f.getRules()) {
                    rows.add(r.toMap());
                    sb.append("\n  ").append(r.getId()).append("  ").append(r.describe());
                }
                data.put("rules", rows);
                return ok(rows.isEmpty() ? "No saved rules." : "Saved rules:" + sb, data);
            }
            case "add" -> {
                if (a.pos(1) == null || a.pos(2) == null) return fail("Usage: rules add <place_category|place_name|protect_path> <match> [folder]", 2);
                UserRule.Kind kind;
                try {
                    kind = UserRule.Kind.valueOf(a.pos(1).toUpperCase());
                } catch (IllegalArgumentException e) {
                    return fail("Kind must be place_category, place_name or protect_path.", 2);
                }
                UserRule saved = f.saveRule(UserRule.create(kind, a.pos(2), a.pos(3)));
                data.put("rule", saved.toMap());
                return ok("Saved: " + saved.describe(), data);
            }
            case "remove" -> {
                if (a.pos(1) == null) return fail("Usage: rules remove <id>", 2);
                return f.removeRule(a.pos(1)) ? ok("Removed rule " + a.pos(1), data) : fail("No rule with that id.", 1);
            }
            default -> {
                return fail("Usage: rules list | add ... | remove <id>", 2);
            }
        }
    }

    private boolean confirm(String question) throws IOException {
        out.println(question + " [y/N]");
        String line = in.readLine();
        return line != null && line.trim().toLowerCase().startsWith("y");
    }

    private static String warnings(Inventory inv) {
        if (inv.getWarnings().isEmpty()) return "";
        return "\nNotes:\n  " + String.join("\n  ", inv.getWarnings());
    }

    private static String failures(RunResult run) {
        return run.messages().isEmpty() ? "" : "\n  " + String.join("\n  ", run.messages());
    }

    private static String describePlan(Plan plan) {
        if (plan.size() == 0) return "The plan is empty: nothing needs to move." + planNotes(plan);
        Map<String, List<String>> byFolder = new LinkedHashMap<>();
        for (FileAction a : plan.getActions()) {
            if (a.source() != null) byFolder.computeIfAbsent(a.target().getParent().toString(), k -> new ArrayList<>()).add(a.source().getFileName() + (a.target().getFileName().equals(a.source().getFileName()) ? "" : " -> " + a.target().getFileName()));
        }
        StringBuilder sb = new StringBuilder("Proposed plan (" + plan.getActions().stream().filter(x -> x.source() != null).count() + " move(s)):");
        byFolder.forEach((folder, files) -> {
            sb.append("\n  ").append(folder);
            files.forEach(x -> sb.append("\n      ").append(x));
        });
        return sb + planNotes(plan);
    }

    private static String planNotes(Plan plan) {
        return plan.getWarnings().isEmpty() ? "" : "\nNotes:\n  " + String.join("\n  ", plan.getWarnings());
    }

    private static String describeCandidates(List<CleanupCandidate> found) {
        StringBuilder sb = new StringBuilder("Cleanup candidates (" + found.size() + "):");
        for (CleanupCandidate c : found) sb.append("\n  ").append(c.record().getName()).append("  (").append(String.join("; ", c.reasons())).append(")");
        return sb.toString();
    }

    private static String describeReply(ChatReply r) {
        StringBuilder sb = new StringBuilder(r.text());
        if (r.plan() != null && r.plan().size() > 0) sb.append("\n").append(describePlan(r.plan())).append("\nType /approve to apply it.");
        if (r.cleanup() != null && !r.cleanup().isEmpty()) sb.append("\n").append(describeCandidates(r.cleanup())).append("\nType /cleanup to move them to To_Be_Deleted.");
        if (r.proposedRule() != null) sb.append("\nType /rule to save it.");
        return sb.toString();
    }

    private static Map<String, Object> inventoryData(Inventory inv) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        for (FileRecord r : inv.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getName());
            m.put("extension", r.getExtension());
            m.put("size", r.getSize());
            m.put("category", r.getCategory().name());
            m.put("confidence", r.getConfidence());
            m.put("reason", r.getReason());
            files.add(m);
        }
        data.put("root", inv.getRoot().toString());
        data.put("count", inv.size());
        data.put("files", files);
        data.put("warnings", new ArrayList<>(inv.getWarnings()));
        return data;
    }

    private static Map<String, Object> planData(Plan plan) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> actions = new ArrayList<>();
        for (FileAction a : plan.getActions()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("type", a.type());
            m.put("source", a.source() == null ? "" : a.source().toString());
            m.put("target", a.target().toString());
            actions.add(m);
        }
        data.put("actions", actions);
        data.put("warnings", new ArrayList<>(plan.getWarnings()));
        return data;
    }

    private static Map<String, Object> candidateData(List<CleanupCandidate> found) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CleanupCandidate c : found) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("file", c.record().getName());
            m.put("reasons", new ArrayList<>(c.reasons()));
            rows.add(m);
        }
        data.put("candidates", rows);
        return data;
    }

    private static Map<String, Object> replyData(ChatReply r) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reply", r.text());
        if (r.plan() != null) data.putAll(planData(r.plan()));
        if (r.cleanup() != null) data.putAll(candidateData(r.cleanup()));
        if (r.proposedRule() != null) data.put("proposedRule", r.proposedRule().toMap());
        return data;
    }
}
