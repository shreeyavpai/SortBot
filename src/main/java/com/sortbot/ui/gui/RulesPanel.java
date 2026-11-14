package com.sortbot.ui.gui;

import com.sortbot.domain.UserRule;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/** F10: rules SortBot remembers between sessions (where files go, and what must never move). */
public class RulesPanel extends JPanel {
    private final GuiContext ctx;
    private final DefaultTableModel model = new DefaultTableModel(new Object[] {"Id", "Rule"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JComboBox<UserRule.Kind> kind = new JComboBox<>(UserRule.Kind.values());
    private final JTextField match = new JTextField(14);
    private final JTextField folder = new JTextField(14);

    public RulesPanel(GuiContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add rule");
        JButton remove = new JButton("Remove selected");
        add.addActionListener(e -> addRule((UserRule.Kind) kind.getSelectedItem(), match.getText(), folder.getText()));
        remove.addActionListener(e -> removeSelected());
        form.add(new JLabel("Type:"));
        form.add(kind);
        form.add(new JLabel("Match:"));
        form.add(match);
        form.add(new JLabel("Folder:"));
        form.add(folder);
        form.add(add);
        form.add(remove);
        add(form, BorderLayout.SOUTH);
        add(new JLabel("place_category: a category (e.g. RECEIPTS). place_name: part of a file name. protect_path: files whose path contains this are never moved."), BorderLayout.NORTH);
        refresh();
    }

    public void addRule(UserRule.Kind k, String m, String f) {
        try {
            ctx.facade.saveRule(UserRule.create(k, m, f));
            match.setText("");
            folder.setText("");
            refresh();
        } catch (RuntimeException e) {
            ctx.error(GuiContext.friendly(e));
        }
    }

    public void removeSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            ctx.error("Select a rule first.");
            return;
        }
        ctx.facade.removeRule((String) model.getValueAt(row, 0));
        refresh();
    }

    public void selectRow(int row) { table.setRowSelectionInterval(row, row); }
    public int ruleCount() { return model.getRowCount(); }

    public void refresh() {
        model.setRowCount(0);
        List<UserRule> rules = ctx.facade.getRules();
        for (UserRule r : rules) model.addRow(new Object[] {r.getId(), r.describe()});
    }
}
