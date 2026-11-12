package com.sortbot.core;

import com.sortbot.domain.CreateFolderAction;
import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;
import com.sortbot.domain.MoveFileAction;
import com.sortbot.domain.Plan;
import com.sortbot.infra.FileSystemGateway;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Applies all cleanup rules to an inventory (F08). It only flags files; moving needs the user's confirmation. */
public class CleanupDetector {
    private final List<CleanupRule> rules;
    private Predicate<FileRecord> protectedFilter = r -> false;

    public CleanupDetector(List<CleanupRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** Files matching this filter (from user rules such as "never touch Projects") are never flagged. */
    public void setProtectionFilter(Predicate<FileRecord> filter) {
        this.protectedFilter = filter == null ? r -> false : filter;
    }

    public List<CleanupCandidate> detect(Inventory inventory) {
        for (CleanupRule rule : rules) rule.prepare(inventory);
        List<CleanupCandidate> out = new ArrayList<>();
        for (FileRecord r : inventory.getRecords()) {
            if (protectedFilter.test(r)) continue;
            List<String> reasons = new ArrayList<>();
            for (CleanupRule rule : rules) if (rule.matches(r)) reasons.add(rule.reason(r));
            if (!reasons.isEmpty()) out.add(new CleanupCandidate(r, reasons));
        }
        return out;
    }

    /** Builds a plan that moves the chosen candidates into root/To_Be_Deleted. Nothing is deleted. */
    public Plan toPlan(List<CleanupCandidate> chosen, Path root, FileSystemGateway gateway) {
        Plan plan = new Plan();
        if (chosen.isEmpty()) return plan;
        Path folder = root.resolve(FileSystemGateway.TO_BE_DELETED);
        plan.add(new CreateFolderAction(gateway, folder));
        for (CleanupCandidate c : chosen) {
            plan.add(new MoveFileAction(gateway, c.record().getPath(), folder.resolve(c.record().getName())));
        }
        return plan;
    }
}
