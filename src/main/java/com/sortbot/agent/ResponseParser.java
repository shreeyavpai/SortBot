package com.sortbot.agent;

import com.sortbot.domain.Category;
import com.sortbot.infra.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns the model's text into validated Java objects. Anything malformed becomes an AgentParseException. */
public class ResponseParser {

    /** Finds the JSON object in a reply, ignoring markdown fences and any chatter around it. */
    public static String extractJson(String text) throws AgentParseException {
        if (text == null) throw new AgentParseException("The reply was empty.");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) throw new AgentParseException("The reply did not contain a JSON object.");
        return text.substring(start, end + 1);
    }

    private Object parse(String text) throws AgentParseException {
        try {
            return Json.parse(extractJson(text));
        } catch (IllegalArgumentException e) {
            throw new AgentParseException("The reply was not valid JSON: " + e.getMessage());
        }
    }

    public List<PlannedMove> parseMoves(String text) throws AgentParseException {
        Object moves = Json.path(parse(text), "moves");
        if (!(moves instanceof List<?> list)) throw new AgentParseException("The reply had no \"moves\" list.");
        List<PlannedMove> out = new ArrayList<>();
        for (Object o : list) {
            Object file = Json.path(o, "file");
            Object folder = Json.path(o, "folder");
            if (!(file instanceof String f) || !(folder instanceof String d) || f.isBlank()) {
                throw new AgentParseException("A move was missing \"file\" or \"folder\".");
            }
            out.add(new PlannedMove(f, d));
        }
        return out;
    }

    /** Result keyed by the 1-based id used in the prompt. Unknown categories become UNCLASSIFIED. */
    public Map<Integer, Classification> parseClassifications(String text) throws AgentParseException {
        Object files = Json.path(parse(text), "files");
        if (!(files instanceof List<?> list)) throw new AgentParseException("The reply had no \"files\" list.");
        Map<Integer, Classification> out = new HashMap<>();
        for (Object o : list) {
            Object id = Json.path(o, "id");
            if (!(id instanceof Number n)) throw new AgentParseException("A classification was missing its numeric \"id\".");
            Object cat = Json.path(o, "category");
            Category c = cat instanceof String s ? Category.fromText(s) : null;
            Object conf = Json.path(o, "confidence");
            Object reason = Json.path(o, "reason");
            String why = reason instanceof String r ? r : "";
            if (c == null) {
                c = Category.UNCLASSIFIED;
                why = "The AI gave an unknown category.";
            }
            out.put(n.intValue(), new Classification(c, conf instanceof Number cn ? cn.doubleValue() : 0.5, why));
        }
        return out;
    }

    public Intent parseIntent(String text) throws AgentParseException {
        Object root = parse(text);
        Object clarify = Json.path(root, "clarify");
        if (clarify instanceof String q && !q.isBlank()) return new Intent(null, q);
        Object tool = Json.path(root, "tool");
        if (!(tool instanceof String name) || name.isBlank()) throw new AgentParseException("The reply named no tool.");
        Map<String, String> args = new LinkedHashMap<>();
        Object a = Json.path(root, "args");
        if (a instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getValue() != null && !(e.getValue() instanceof Map) && !(e.getValue() instanceof List)) {
                    args.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        }
        return new Intent(new ToolCall(name.trim(), args), null);
    }
}
