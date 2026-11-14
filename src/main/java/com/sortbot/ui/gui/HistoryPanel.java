package com.sortbot.ui.gui;

import com.sortbot.core.ActionEntry;
import com.sortbot.core.LogFilter;
import com.sortbot.core.ReportBuilder;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;

/** F07 and F11: see everything SortBot did, undo a run, and export a report. */
public class HistoryPanel extends JPanel {
    private final GuiContext ctx;
    private final DefaultTableModel model = new DefaultTableModel(new Object[] {"Time", "Run", "Type", "Status", "From", "To"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextField runFilter = new JTextField(10);
    private final JComboBox<String> format = new JComboBox<>(new String[] {"CSV", "Markdown"});

    public HistoryPanel(GuiContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refresh = new JButton("Refresh");
        JButton undoRun = new JButton("Undo selected run");
        JButton undoLast = new JButton("Undo last action");
        JButton export = new JButton("Export report...");
        refresh.addActionListener(e -> refresh());
        undoRun.addActionListener(e -> undoSelectedRun());
        undoLast.addActionListener(e -> undoLastNow());
        export.addActionListener(e -> chooseAndExport());
        top.add(new JLabel("Run id filter:"));
        top.add(runFilter);
        top.add(refresh);
        top.add(undoRun);
        top.add(undoLast);
        top.add(format);
        top.add(export);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        model.setRowCount(0);
        LogFilter f = LogFilter.all();
        if (!runFilter.getText().isBlank()) f.runId(runFilter.getText().trim());
        for (ActionEntry e : ctx.facade.getHistory(f)) {
            model.addRow(new Object[] {e.getTimestamp(), e.getRunId(), e.getType(), e.getStatus(), e.getSource(), e.getTarget()});
        }
    }

    public void undoSelectedRun() {
        int row = table.getSelectedRow();
        String runId = row >= 0 ? (String) model.getValueAt(row, 1) : ctx.facade.lastUndoableRunId();
        if (runId == null) {
            ctx.info("Nothing to undo.");
            return;
        }
        undoRun(runId);
    }

    public void undoRun(String runId) {
        if (!ctx.confirm("Undo run " + runId + "? Moved files go back where they were.")) return;
        ctx.run("Undoing...", () -> ctx.facade.undo(runId), u -> {
            ctx.info(u.nothingToUndo() ? "Run " + runId + " has nothing left to undo." : "Undo: " + u.summary() + (u.messages().isEmpty() ? "" : "\n" + String.join("\n", u.messages())));
            ctx.inventory = null;
            refresh();
        });
    }

    public void undoLastNow() {
        ctx.run("Undoing...", ctx.facade::undoLast, u -> {
            ctx.info(u.nothingToUndo() ? "Nothing to undo." : "Undo: " + u.summary());
            ctx.inventory = null;
            refresh();
        });
    }

    private void chooseAndExport() {
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            exportTo(fc.getSelectedFile().toPath(), "Markdown".equals(format.getSelectedItem()) ? ReportBuilder.Format.MARKDOWN : ReportBuilder.Format.CSV);
        }
    }

    public void exportTo(Path file, ReportBuilder.Format fmt) {
        try {
            LogFilter f = LogFilter.all();
            if (!runFilter.getText().isBlank()) f.runId(runFilter.getText().trim());
            ctx.facade.exportReport(fmt, file, f);
            ctx.info("Report saved to " + file);
        } catch (IOException e) {
            ctx.error("Could not save the report: " + e.getMessage());
        }
    }

    public int rowCount() { return model.getRowCount(); }
    public void selectRow(int row) { table.setRowSelectionInterval(row, row); }
}
