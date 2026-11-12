package com.sortbot.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny reader/writer for flat JSON objects with string values (used for the log and trace files). */
public final class FlatJson {
    private FlatJson() {}

    public static String toJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            quote(sb, e.getKey());
            sb.append(':');
            quote(sb, e.getValue() == null ? "" : e.getValue());
        }
        return sb.append('}').toString();
    }

    public static Map<String, String> parse(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        int[] i = {0};
        skipWs(json, i);
        expect(json, i, '{');
        skipWs(json, i);
        if (peek(json, i) == '}') return out;
        while (true) {
            skipWs(json, i);
            String key = readString(json, i);
            skipWs(json, i);
            expect(json, i, ':');
            skipWs(json, i);
            String value = peek(json, i) == '"' ? readString(json, i) : readRaw(json, i);
            out.put(key, value);
            skipWs(json, i);
            char c = next(json, i);
            if (c == '}') return out;
            if (c != ',') throw new IllegalArgumentException("Bad JSON near position " + i[0]);
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    private static String readString(String s, int[] i) {
        expect(s, i, '"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next(s, i);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = next(s, i);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i[0] + 4 > s.length()) throw new IllegalArgumentException("Bad unicode escape");
                    sb.append((char) Integer.parseInt(s.substring(i[0], i[0] + 4), 16));
                    i[0] += 4;
                }
                default -> sb.append(e);
            }
        }
    }

    private static String readRaw(String s, int[] i) {
        int start = i[0];
        while (i[0] < s.length() && ",}".indexOf(s.charAt(i[0])) < 0) i[0]++;
        return s.substring(start, i[0]).trim();
    }

    private static void skipWs(String s, int[] i) {
        while (i[0] < s.length() && Character.isWhitespace(s.charAt(i[0]))) i[0]++;
    }

    private static char peek(String s, int[] i) {
        if (i[0] >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        return s.charAt(i[0]);
    }

    private static char next(String s, int[] i) {
        char c = peek(s, i);
        i[0]++;
        return c;
    }

    private static void expect(String s, int[] i, char want) {
        char c = next(s, i);
        if (c != want) throw new IllegalArgumentException("Expected '" + want + "' but found '" + c + "'");
    }
}
