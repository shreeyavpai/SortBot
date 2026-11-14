package com.sortbot.ui.gui;

import com.sortbot.domain.Category;
import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.List;

/** F02 and F03: scan a folder, classify its files, and correct categories by hand. */
public class ScanPanel extends JPanel {
    private final GuiContext ctx;
    private final JComboBox<String> folders = new JComboBox<>();
    private final JCheckBox recursive = new JCheckBox("Include subfolders");
    private final RecordsModel model = new RecordsModel();
    private final JTable table = new JTable(model);
    private final JTextArea notes = new JTextArea(3, 40);

    public ScanPanel(GuiContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton scan = new JButton("Scan");
        JButton classify = new JButton("Classify");
        scan.addActionListener(e -> scanNow());
        classify.addActionListener(e -> classifyNow());
        top.add(new JLabel("Folder:"));
        top.add(folders);
        top.add(recursive);
        top.add(scan);
        top.add(classify);
        add(top, BorderLayout.NORTH);
        table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JComboBox<>(Category.values())));
        add(new JScrollPane(table), BorderLayout.CENTER);
        notes.setEditable(false);
        notes.setBorder(BorderFactory.createTitledBorder("Notes"));
        add(new JScrollPane(notes), BorderLayout.SOUTH);
        refreshFolders();
    }

    public void refreshFolders() {
        Object keep = folders.getSelectedItem();
        folders.removeAllItems();
        ctx.facade.getAllowedFolders().forEach(p -> folders.addItem(p.toString()));
        if (keep != null) folders.setSelectedItem(keep);
    }

    public void scanNow() {
        String sel = (String) folders.getSelectedItem();
        if (sel == null) {
            ctx.error("Add a folder on the Folders tab first.");
            return;
        }
        ctx.run("Scanning...", () -> ctx.facade.scanFolder(Path.of(sel), recursive.isSelected()), inv -> {
            ctx.inventory = inv;
            ctx.plan = null;
            show(inv);
            ctx.onInventoryChanged.run();
        });
    }

    public void classifyNow() {
        if (ctx.inventory == null) {
            ctx.error("Scan a folder first.");
            return;
        }
        ctx.run("Classifying...", () -> ctx.facade.classify(ctx.inventory), this::show);
    }

    private void show(Inventory inv) {
        model.set(inv.getRecords());
        notes.setText(String.join("\n", inv.getWarnings()));
    }

    public int rowCount() { return model.getRowCount(); }
    public Object cell(int row, int col) { return model.getValueAt(row, col); }
    public void setCategoryAt(int row, Category c) { model.setValueAt(c, row, 3); }

    private final class RecordsModel extends AbstractTableModel {
        private final String[] cols = {"File", "Type", "Size", "Category", "Confidence", "Why"};
        private List<FileRecord> rows = List.of();

        void set(List<FileRecord> r) {
            rows = r;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return c == 3; }

        @Override
        public Object getValueAt(int r, int c) {
            FileRecord f = rows.get(r);
            return switch (c) {
                case 0 -> f.getName();
                case 1 -> f.getExtension();
                case 2 -> size(f.getSize());
                case 3 -> f.getCategory();
                case 4 -> Math.round(f.getConfidence() * 100) + "%";
                default -> f.getReason();
            };
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            if (c == 3 && v instanceof Category cat) {
                ctx.facade.setCategory(rows.get(r), cat);
                fireTableRowsUpdated(r, r);
            }
        }
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
