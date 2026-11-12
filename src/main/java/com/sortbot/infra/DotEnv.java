package com.sortbot.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Reads KEY=VALUE pairs from a local .env file (never committed). Real environment variables win. */
public class DotEnv {
    private final Map<String, String> values = new HashMap<>();

    public DotEnv(Path envFile) {
        if (envFile == null || !Files.isRegularFile(envFile)) return;
        try {
            for (String raw : Files.readAllLines(envFile)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                if (val.length() >= 2 && (val.startsWith("\"") && val.endsWith("\"") || val.startsWith("'") && val.endsWith("'"))) {
                    val = val.substring(1, val.length() - 1);
                }
                values.put(key, val);
            }
        } catch (IOException e) {
            // Missing or unreadable file: fall back to environment variables only.
        }
    }

    /** Environment variable first, then the .env file, then null. */
    public String get(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        return values.get(key);
    }

    public String get(String key, String defaultValue) {
        String v = get(key);
        return v == null || v.isBlank() ? defaultValue : v;
    }
}
