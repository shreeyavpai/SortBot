package com.sortbot.core;

import com.sortbot.domain.UserRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Saves and loads user rules (JSON Lines). A damaged file is backed up and the readable rules are kept. */
public class RuleRepository {
    private final Path file;
    private int damagedLines;

    public RuleRepository(Path file) {
        this.file = file;
    }

    public List<UserRule> load() {
        List<UserRule> rules = new ArrayList<>();
        damagedLines = 0;
        if (file == null || !Files.isRegularFile(file)) return rules;
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) continue;
                try {
                    rules.add(UserRule.fromMap(FlatJson.parse(line)));
                } catch (RuntimeException e) {
                    damagedLines++;
                }
            }
            if (damagedLines > 0) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                if (!Files.exists(backup)) Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException e) {
            damagedLines++;
        }
        return rules;
    }

    public void save(List<UserRule> rules) {
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>();
            for (UserRule r : rules) lines.add(FlatJson.toJson(r.toMap()));
            Files.write(file, lines);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save rules: " + e.getMessage(), e);
        }
    }

    public int getDamagedLineCount() { return damagedLines; }
}
