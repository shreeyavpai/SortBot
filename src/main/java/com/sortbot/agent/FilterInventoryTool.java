package com.sortbot.agent;

import com.sortbot.domain.Category;
import com.sortbot.domain.FileRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Lists scanned files that match simple filters. Read-only. */
public class FilterInventoryTool implements Tool {
    private final AgentSession session;

    public FilterInventoryTool(AgentSession session) { this.session = session; }

    @Override public String name() { return "filter_files"; }

    @Override
    public String description() {
        return "List scanned files matching filters. Optional args: ext (file extension), name_contains, category, older_than_months.";
    }

    @Override
    public ToolResult run(Map<String, String> args) {
        if (session.inventory == null) return ToolResult.rejected("Scan a folder first.");
        String ext = args.get("ext");
        String contains = args.get("name_contains");
        String cat = args.get("category");
        String months = args.get("older_than_months");
        if (isBlank(ext) && isBlank(contains) && isBlank(cat) && isBlank(months)) {
            return ToolResult.rejected("Tell me what to filter by: an extension, part of a name, a category or an age in months.");
        }
        Category category = isBlank(cat) ? null : Category.fromText(cat);
        if (!isBlank(cat) && category == null) return ToolResult.rejected("I don't know the category \"" + cat + "\".");
        Instant cutoff = null;
        if (!isBlank(months)) {
            try {
                cutoff = ZonedDateTime.now(ZoneId.systemDefault()).minusMonths(Integer.parseInt(months.trim())).toInstant();
            } catch (NumberFormatException e) {
                return ToolResult.rejected("\"" + months + "\" is not a number of months.");
            }
        }
        final Instant cut = cutoff;
        List<FileRecord> found = session.inventory.filter(r ->
                (isBlank(ext) || r.getExtension().equalsIgnoreCase(ext.replace(".", "").trim()))
                        && (isBlank(contains) || r.getName().toLowerCase().contains(contains.toLowerCase()))
                        && (category == null || r.getCategory() == category)
                        && (cut == null || r.getModified().isBefore(cut)));
        if (found.isEmpty()) return ToolResult.ok("No files match.");
        String names = found.stream().limit(15).map(FileRecord::getName).collect(Collectors.joining(", "));
        return ToolResult.ok("Found " + found.size() + " file(s): " + names + (found.size() > 15 ? ", ..." : ""));
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
