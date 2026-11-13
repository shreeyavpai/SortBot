package com.sortbot.core;

import com.sortbot.domain.Category;
import com.sortbot.domain.UserRule;

import java.util.ArrayList;
import java.util.List;

/** Remembers the user's rules between sessions (F10) and rejects bad, conflicting or too-broad rules. */
public class MemoryManager {
    private final RuleRepository repository;
    private final List<UserRule> rules;

    public MemoryManager(RuleRepository repository) {
        this.repository = repository;
        this.rules = new ArrayList<>(repository.load());
    }

    public synchronized List<UserRule> getRules() {
        return new ArrayList<>(rules);
    }

    /** Validates and saves a rule. Throws IllegalArgumentException with a user-readable reason. */
    public synchronized UserRule saveRule(UserRule rule) {
        UserRule clean = validate(rule);
        rules.add(clean);
        repository.save(rules);
        return clean;
    }

    public synchronized boolean removeRule(String id) {
        boolean removed = rules.removeIf(r -> r.getId().equals(id));
        if (removed) repository.save(rules);
        return removed;
    }

    /** True if this rule clashes with a saved one (same match, different destination, or an exact duplicate). */
    public synchronized boolean checkConflict(UserRule rule) {
        return conflictMessage(rule) != null;
    }

    /** Returns the cleaned rule or throws IllegalArgumentException explaining what is wrong. */
    public synchronized UserRule validate(UserRule rule) {
        if (rule.getMatch().isBlank()) throw new IllegalArgumentException("The rule needs something to match.");
        String folder = rule.getFolder();
        if (rule.isPlacement()) {
            folder = FolderNames.sanitize(rule.getFolder());
            if (folder == null) throw new IllegalArgumentException("The folder must be a short relative path such as Finance/Receipts (no drive letters or \"..\").");
        }
        if (rule.getKind() == UserRule.Kind.PLACE_CATEGORY && Category.fromText(rule.getMatch()) == null) {
            throw new IllegalArgumentException("Unknown category \"" + rule.getMatch() + "\".");
        }
        if (rule.getKind() != UserRule.Kind.PLACE_CATEGORY && (rule.getMatch().length() < 2 || rule.getMatch().matches("[*.?/\\\\\\s]+"))) {
            throw new IllegalArgumentException("That rule is too broad; it would match almost every file.");
        }
        UserRule clean = new UserRule(rule.getId(), rule.getKind(), rule.getMatch(), folder);
        String conflict = conflictMessage(clean);
        if (conflict != null) throw new IllegalArgumentException(conflict);
        return clean;
    }

    private String conflictMessage(UserRule rule) {
        String folder = rule.isPlacement() && FolderNames.sanitize(rule.getFolder()) != null ? FolderNames.sanitize(rule.getFolder()) : rule.getFolder();
        for (UserRule existing : rules) {
            if (existing.getKind() != rule.getKind() || !existing.getMatch().equalsIgnoreCase(rule.getMatch())) continue;
            if (existing.getFolder().equalsIgnoreCase(folder)) return "You already have this rule: " + existing.describe();
            return "This conflicts with an existing rule: " + existing.describe() + ". Remove it first if you want to change it.";
        }
        return null;
    }

    public int getDamagedLineCount() { return repository.getDamagedLineCount(); }
}
