package com.sortbot.ui.gui;

import com.sortbot.core.CleanupCandidate;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** F08: find old, duplicate and temporary files and move the ones you choose to To_Be_Deleted. Nothing is deleted. */
public class CleanupPanel extends JPanel {
    private final GuiContext ctx;
    private final Model model = new Model();
    private final JTable table = new JTable(model);

    public CleanupPanel(GuiContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton find = new JButton("Find cleanup candidates");
        JButton move = new JButton("Move selected to To_Be_Deleted");
        find.addActionListener(e -> findNow());
        move.addActionListener(e -> moveSelectedNow());
        top.add(find);
        top.add(move);
        top.add(new JLabel("Files are moved, never deleted, and you can undo it."));
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void findNow() {
        if (ctx.inventory == null) {
            ctx.error("Scan a folder first.");
            return;
        }
        ctx.run("Looking for clutter...", () -> ctx.facade.findCleanup(ctx.inventory), this::setCandidates);
    }

    public void setCandidates(List<CleanupCandidate> found) {
        ctx.cleanup = found;
        model.set(found);
    }

    public void moveSelectedNow() {
        List<CleanupCandidate> chosen = model.chosen();
        if (chosen.isEmpty()) {
            ctx.error("Tick at least one file first.");
            return;
        }
        if (!ctx.confirm("Move " + chosen.size() + " file(s) to To_Be_Deleted? Nothing is deleted.")) return;
        ctx.run("Moving files...", () -> ctx.facade.confirmCleanup(chosen), run -> {
            ctx.info("Done (run " + run.runId() + "): " + run.summary());
            model.set(List.of());
            ctx.inventory = null;
            ctx.onHistoryChanged.run();
        });
    }

    public void tickAll(boolean on) { model.tickAll(on); }
    public int rowCount() { return model.getRowCount(); }

    private static final class Model extends AbstractTableModel {
        private List<CleanupCandidate> rows = List.of();
        private boolean[] ticked = new boolean[0];

        void set(List<CleanupCandidate> r) {
            rows = r;
            ticked = new boolean[r.size()];
            java.util.Arrays.fill(ticked, true);
            fireTableDataChanged();
        }

        void tickAll(boolean on) {
            java.util.Arrays.fill(ticked, on);
            fireTableDataChanged();
        }

        List<CleanupCandidate> chosen() {
            List<CleanupCandidate> out = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) if (ticked[i]) out.add(rows.get(i));
            return out;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int c) { return new String[] {"Move?", "File", "Why"}[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
        @Override public boolean isCellEditable(int r, int c) { return c == 0; }

        @Override
        public Object getValueAt(int r, int c) {
            return switch (c) {
                case 0 -> ticked[r];
                case 1 -> rows.get(r).record().getName();
                default -> String.join("; ", rows.get(r).reasons());
            };
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            if (c == 0) ticked[r] = Boolean.TRUE.equals(v);
        }
    }
}
