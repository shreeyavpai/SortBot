package com.sortbot.agent;

import com.sortbot.core.TraceLogger;
import com.sortbot.llm.LLMException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handles one chat message (F09): understand the request with the model, validate the tool call,
 * run the tool, and report back. Anything that would change files only becomes a plan for the user to approve.
 */
public class AgentController {
    private static final Pattern DELETE_WORDS = Pattern.compile("\\b(delete|erase|trash|wipe|shred|destroy|get rid of)\\b", Pattern.CASE_INSENSITIVE);

    private final Planner planner;
    private final ToolManager tools;
    private final TraceLogger trace;

    public AgentController(Planner planner, ToolManager tools, TraceLogger trace) {
        this.planner = planner;
        this.tools = tools;
        this.trace = trace;
    }

    public ChatReply handleChat(String message, AgentSession session) {
        if (message == null || message.isBlank()) return ChatReply.text("Type a request, for example: put my tax documents in one folder.");
        trace.log("chat_message", Map.of("length", Integer.toString(message.length())));
        if (DELETE_WORDS.matcher(message).find()) {
            return ChatReply.text("SortBot never deletes files. I can find old, duplicate and temporary files and move them to a To_Be_Deleted folder "
                    + "so you can review them yourself. Ask me to find cleanup candidates.");
        }
        if (session.inventory == null) return ChatReply.text("Scan a folder first so I know which files to work with.");

        Intent intent;
        try {
            intent = planner.interpretIntent(message, tools.describeTools(), stateSummary(session));
        } catch (LLMException e) {
            return ChatReply.text("The AI model is unavailable right now (" + e.getMessage() + "). The other SortBot features still work.");
        } catch (AgentParseException e) {
            return ChatReply.text("I didn't understand that well enough to act on it. Could you rephrase it?");
        }
        if (intent.needsClarification()) return ChatReply.text(intent.clarify());

        ToolResult result;
        try {
            result = tools.execute(intent.toolCall());
        } catch (PlanningException e) {
            return ChatReply.text(e.getMessage());
        }
        return new ChatReply(result.message(), result.plan(), result.cleanup(), result.proposedRule());
    }

    private static String stateSummary(AgentSession s) {
        return s.inventory.size() + " files scanned in " + s.inventory.getRoot();
    }
}
