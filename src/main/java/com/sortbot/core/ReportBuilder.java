package com.sortbot.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Formats log entries as CSV or Markdown and writes the report file (F11). */
public class ReportBuilder {
    public enum Format { CSV, MARKDOWN }

    public String build(List<ActionEntry> entries, Format format) {
        StringBuilder sb = new StringBuilder();
        if (format == Format.CSV) {
            sb.append("time,run,type,source,target,status,message\n");
            for (ActionEntry e : entries) {
                sb.append(csv(e.getTimestamp().toString())).append(',').append(csv(e.getRunId())).append(',')
                        .append(csv(e.getType())).append(',').append(csv(e.getSource())).append(',')
                        .append(csv(e.getTarget())).append(',').append(e.getStatus()).append(',')
                        .append(csv(e.getMessage())).append('\n');
            }
        } else {
            sb.append("# SortBot action report\n\n| Time | Run | Type | Source | Target | Status | Message |\n|---|---|---|---|---|---|---|\n");
            for (ActionEntry e : entries) {
                sb.append("| ").append(md(e.getTimestamp().toString())).append(" | ").append(md(e.getRunId())).append(" | ")
                        .append(md(e.getType())).append(" | ").append(md(e.getSource())).append(" | ")
                        .append(md(e.getTarget())).append(" | ").append(e.getStatus()).append(" | ")
                        .append(md(e.getMessage())).append(" |\n");
            }
        }
        return sb.toString();
    }

    /** Writes the report. Throws IOException if the location is not writable. */
    public void write(List<ActionEntry> entries, Format format, Path out) throws IOException {
        Path abs = out.toAbsolutePath();
        if (abs.getParent() != null) Files.createDirectories(abs.getParent());
        Files.writeString(abs, build(entries, format));
    }

    private static String csv(String s) {
        if (s == null) return "";
        boolean quote = s.contains(",") || s.contains("\"") || s.contains("\n");
        String t = s.replace("\"", "\"\"");
        return quote ? "\"" + t + "\"" : t;
    }

    private static String md(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }
}
