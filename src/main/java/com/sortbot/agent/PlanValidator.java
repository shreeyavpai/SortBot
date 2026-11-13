package com.sortbot.agent;

import com.sortbot.core.FolderAuthorizer;
import com.sortbot.domain.CreateFolderAction;
import com.sortbot.domain.FileAction;
import com.sortbot.domain.Inventory;
import com.sortbot.domain.MoveFileAction;
import com.sortbot.domain.Plan;
import com.sortbot.domain.UserRule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The deterministic safety net between the AI and the file system. It removes any action that
 * uses an unknown file, leaves the authorized folders, moves a protected file, or repeats a file.
 */
public class PlanValidator {
    private final FolderAuthorizer authorizer;

    public PlanValidator(FolderAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    public ValidationResult validate(Plan plan, Inventory inventory, List<UserRule> rules) {
        Set<Path> known = new HashSet<>();
        inventory.getRecords().forEach(r -> known.add(r.getPath()));
        Set<Path> seenSources = new HashSet<>();
        Set<Path> seenTargets = new HashSet<>();
        List<String> problems = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        for (FileAction a : plan.getActions()) {
            if (a instanceof MoveFileAction m) {
                String name = m.source().getFileName().toString();
                if (!known.contains(m.source())) {
                    problems.add("Removed a move of an unknown file: " + name);
                    toRemove.add(a.id());
                } else if (!authorizer.isAuthorized(m.target())) {
                    problems.add("Removed a move of " + name + " because its destination is outside the authorized folders.");
                    toRemove.add(a.id());
                } else if (isProtected(m.source(), inventory, rules)) {
                    problems.add("Kept " + name + " in place because a rule protects it.");
                    toRemove.add(a.id());
                } else if (!seenSources.add(m.source())) {
                    problems.add("Removed a repeated move of " + name + ".");
                    toRemove.add(a.id());
                } else if (!seenTargets.add(m.target())) {
                    problems.add(name + " shares a destination name with another file; it will be renamed with a number.");
                }
            } else if (a instanceof CreateFolderAction c && !authorizer.isAuthorized(c.target())) {
                problems.add("Removed a new folder outside the authorized folders: " + c.target());
                toRemove.add(a.id());
            }
        }
        for (String id : toRemove) plan.remove(id);
        return new ValidationResult(problems, toRemove.size());
    }

    private static boolean isProtected(Path source, Inventory inv, List<UserRule> rules) {
        for (var r : inv.getRecords()) {
            if (!r.getPath().equals(source)) continue;
            for (UserRule rule : rules) if (rule.getKind() == UserRule.Kind.PROTECT_PATH && rule.matches(r)) return true;
        }
        return false;
    }
}
