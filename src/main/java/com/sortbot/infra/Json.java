package com.sortbot.infra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON reader and writer (objects, arrays, strings, numbers, booleans, null). */
public final class Json {
    private Json() {}

    /** Parses JSON text into Map, List, String, Long/Double, Boolean or null. */
    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("No JSON text");
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.i != text.length()) throw new IllegalArgumentException("Unexpected text after JSON at position " + p.i);
        return v;
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    /** Follows keys (String for objects, Integer for arrays); returns null if anything is missing. */
    public static Object path(Object root, Object... keys) {
        Object cur = root;
        for (Object k : keys) {
            if (k instanceof String s && cur instanceof Map<?, ?> m) cur = m.get(s);
            else if (k instanceof Integer idx && cur instanceof List<?> l && idx >= 0 && idx < l.size()) cur = l.get(idx);
            else return null;
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object v) {
        if (v == null) sb.append("null");
        else if (v instanceof String s) quote(sb, s);
        else if (v instanceof Number || v instanceof Boolean) sb.append(v);
        else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) m).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else quote(sb, String.valueOf(v));
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

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) { this.s = s; }

        void skipWs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return s.charAt(i);
        }

        void expect(char c) {
            if (peek() != c) throw new IllegalArgumentException("Expected '" + c + "' at position " + i);
            i++;
        }

        Object value() {
            skipWs();
            char c = peek();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') { i++; return m; }
            while (true) {
                skipWs();
                String k = string();
                skipWs();
                expect(':');
                m.put(k, value());
                skipWs();
                char c = peek();
                i++;
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("Expected ',' or '}' at position " + (i - 1));
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') { i++; return l; }
            while (true) {
                l.add(value());
                skipWs();
                char c = peek();
                i++;
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("Expected ',' or ']' at position " + (i - 1));
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                i++;
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = peek();
                i++;
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw new IllegalArgumentException("Bad unicode escape");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> sb.append(e);
                }
            }
        }

        Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new IllegalArgumentException("Unexpected character '" + s.charAt(i) + "' at position " + i);
            String t = s.substring(start, i);
            try {
                return t.contains(".") || t.contains("e") || t.contains("E") ? (Object) Double.parseDouble(t) : (Object) Long.parseLong(t);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Bad number '" + t + "'");
            }
        }
    }
}
