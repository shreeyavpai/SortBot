package com.sortbot.ui.gui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;

/** F01: choose which folders SortBot may read and change. */
public class FoldersPanel extends JPanel {
    private final GuiContext ctx;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);

    public FoldersPanel(GuiContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(new JLabel("SortBot only works inside the folders listed here. Everything else is blocked."), BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add folder...");
        JButton remove = new JButton("Remove selected");
        add.addActionListener(e -> chooseAndAdd());
        remove.addActionListener(e -> removeSelected());
        buttons.add(add);
        buttons.add(remove);
        add(buttons, BorderLayout.SOUTH);
        refresh();
    }

    private void chooseAndAdd() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) addFolder(fc.getSelectedFile().toPath());
    }

    public void addFolder(Path folder) {
        try {
            ctx.facade.authorizeFolder(folder);
            refresh();
            ctx.onFoldersChanged.run();
        } catch (RuntimeException e) {
            ctx.error(GuiContext.friendly(e));
        }
    }

    public void removeSelected() {
        String sel = list.getSelectedValue();
        if (sel == null) {
            ctx.error("Select a folder in the list first.");
            return;
        }
        ctx.facade.removeFolder(Path.of(sel));
        refresh();
        ctx.onFoldersChanged.run();
    }

    public void selectIndex(int i) { list.setSelectedIndex(i); }

    public void refresh() {
        model.clear();
        ctx.facade.getAllowedFolders().forEach(p -> model.addElement(p.toString()));
    }

    public int folderCount() { return model.size(); }
}
