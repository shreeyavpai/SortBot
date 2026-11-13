package com.sortbot.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A saved preference: where certain files go, or which files must never be moved (F10). */
public class UserRule {
    public enum Kind { PLACE_CATEGORY, PLACE_NAME, PROTECT_PATH }

    private final String id;
    private final Kind kind;
    private final String match;
    private final String folder;

    public UserRule(String id, Kind kind, String match, String folder) {
        this.id = id;
        this.kind = kind;
        this.match = match == null ? "" : match.trim();
        this.folder = folder == null ? "" : folder.trim();
    }

    public static UserRule create(Kind kind, String match, String folder) {
        return new UserRule("rule-" + UUID.randomUUID().toString().substring(0, 6), kind, match, folder);
    }

    public String getId() { return id; }
    public Kind getKind() { return kind; }
    public String getMatch() { return match; }
    public String getFolder() { return folder; }
    public boolean isPlacement() { return kind != Kind.PROTECT_PATH; }

    public boolean matches(FileRecord r) {
        String m = match.toLowerCase();
        return switch (kind) {
            case PLACE_CATEGORY -> r.getCategory().name().equalsIgnoreCase(match) || r.getCategory().folderName().equalsIgnoreCase(match);
            case PLACE_NAME -> r.getName().toLowerCase().contains(m);
            case PROTECT_PATH -> r.getPath().toString().replace('\\', '/').toLowerCase().contains(m.replace('\\', '/'));
        };
    }

    public String describe() {
        return switch (kind) {
            case PLACE_CATEGORY -> "Files categorized as " + match + " go in " + folder;
            case PLACE_NAME -> "Files with \"" + match + "\" in the name go in " + folder;
            case PROTECT_PATH -> "Never move files whose path contains \"" + match + "\"";
        };
    }

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("kind", kind.name());
        m.put("match", match);
        m.put("folder", folder);
        return m;
    }

    public static UserRule fromMap(Map<String, String> m) {
        return new UserRule(m.get("id"), Kind.valueOf(m.get("kind")), m.get("match"), m.get("folder"));
    }
}
