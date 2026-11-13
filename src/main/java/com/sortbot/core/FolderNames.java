package com.sortbot.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Checks that a folder name proposed by the AI or the user is a short, safe, relative path. */
public final class FolderNames {
    private static final Pattern BAD_CHARS = Pattern.compile("[<>:\"|?*\\x00-\\x1F]");
    private static final Set<String> RESERVED = Set.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "LPT1", "LPT2", "LPT3");
    public static final int MAX_DEPTH = 3;

    private FolderNames() {}

    /** Returns a cleaned relative path such as "Finance/Receipts", or null if it is not acceptable. */
    public static String sanitize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace('\\', '/');
        if (s.isEmpty() || s.startsWith("/") || s.matches("^[A-Za-z]:.*")) return null;
        List<String> parts = new ArrayList<>();
        for (String seg : s.split("/")) {
            String t = seg.trim();
            if (t.isEmpty()) continue;
            if (t.equals(".") || t.equals("..") || BAD_CHARS.matcher(t).find()) return null;
            while (t.endsWith(".") || t.endsWith(" ")) t = t.substring(0, t.length() - 1);
            if (t.isEmpty() || t.length() > 60 || RESERVED.contains(t.toUpperCase())) return null;
            parts.add(t);
        }
        if (parts.isEmpty() || parts.size() > MAX_DEPTH) return null;
        return String.join("/", parts);
    }
}
