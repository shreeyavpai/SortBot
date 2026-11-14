package com.sortbot.ui.gui;

import com.sortbot.core.FolderNames;
import com.sortbot.domain.CreateFolderAction;
import com.sortbot.domain.FileAction;
import com.sortbot.domain.MoveFileAction;
import com.sortbot.domain.Plan;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** F04, F05, F06: generate a plan, review and edit it, then approve it so it runs. */
public class PlanPanel extends JPanel {
    private record ActionNode(String id, String label) {
        @Override public String toString() { return label; }
    }

    private final GuiContext ctx;
    private final JTextField instruction = new JTextField(30);
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode("No plan yet"));
    private final JTree tree = new JTree(treeModel);
    private final JTextArea notes = new JTextArea(4, 40);
    private Plan plan;

    public PlanPanel(GuiContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton generate = new JButton("Generate plan");
        generate.addActionListener(e -> generateNow());
        top.add(new JLabel("What should SortBot do? (optional)"));
        top.add(instruction);
        top.add(generate);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(4, 4));
        notes.setEditable(false);
        notes.setBorder(BorderFactory.createTitledBorder("Notes about this plan"));
        south.add(new JScrollPane(notes), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton reject = new JButton("Reject selected move");
        JButton change = new JButton("Change folder...");
        JButton approve = new JButton("Approve and run");
        JButton cancel = new JButton("Cancel running");
        JButton discard = new JButton("Discard plan");
        reject.addActionListener(e -> rejectSelected());
        change.addActionListener(e -> changeSelectedFolder());
        approve.addActionListener(e -> approveNow());
        cancel.addActionListener(e -> ctx.facade.cancel());
        discard.addActionListener(e -> discard());
        for (JButton b : new JButton[] {reject, change, approve, cancel, discard}) buttons.add(b);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
    }

    public void generateNow() {
        if (ctx.inventory == null) {
            ctx.error("Scan and classify a folder first.");
            return;
        }
        ctx.run("Asking the AI for a plan...", () -> ctx.facade.generatePlan(ctx.inventory, instruction.getText()), p -> {
            ctx.plan = p;
            setPlan(p);
        });
    }

    public void setPlan(Plan p) {
        this.plan = p;
        rebuild();
    }

    public void discard() {
        plan = null;
        ctx.plan = null;
        rebuild();
    }

    public void rejectSelected() {
        String id = selectedActionId();
        if (id == null) {
            ctx.error("Select a move in the tree first.");
            return;
        }
        rejectAction(id);
    }

    public void rejectAction(String id) {
        if (plan == null) return;
        ctx.facade.rejectMove(plan, id);
        rebuild();
    }

    public void changeSelectedFolder() {
        String id = selectedActionId();
        if (id == null) {
            ctx.error("Select a move in the tree first.");
            return;
        }
        String folder = ctx.ask("Move this file to which folder? (relative, such as School/Notes)", "");
        if (folder != null) moveAction(id, folder);
    }

    public void moveAction(String id, String relativeFolder) {
        if (plan == null || ctx.inventory == null) return;
        String clean = FolderNames.sanitize(relativeFolder);
        if (clean == null) {
            ctx.error("The folder must be a short relative path such as School/Notes (no drive letters or \"..\").");
            return;
        }
        FileAction a = plan.find(id);
        if (!(a instanceof MoveFileAction m)) {
            ctx.error("Only moves can be changed.");
            return;
        }
        try {
            ctx.facade.editPlan(plan, id, ctx.inventory.getRoot().resolve(clean).resolve(m.source().getFileName().toString()));
        } catch (RuntimeException e) {
            ctx.error(GuiContext.friendly(e));
            return;
        }
        rebuild();
    }

    public void approveNow() {
        if (plan == null || plan.size() == 0) {
            ctx.error("There is no plan to apply.");
            return;
        }
        if (!ctx.confirm("Apply this plan? Files will be moved. You can undo it afterwards.")) return;
        Plan toRun = plan;
        ctx.run("Applying plan...", () -> ctx.facade.executePlan(toRun), run -> {
            ctx.info("Done (run " + run.runId() + "): " + run.summary() + (run.messages().isEmpty() ? "" : "\n" + String.join("\n", run.messages())));
            plan = null;
            ctx.plan = null;
            ctx.inventory = null;
            rebuild();
            ctx.onHistoryChanged.run();
        });
    }

    private String selectedActionId() {
        TreePath p = tree.getSelectionPath();
        if (p != null && ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject() instanceof ActionNode n) return n.id();
        return null;
    }

    /** Ids of the moves currently shown, for tests. */
    public java.util.List<String> actionIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        collect((DefaultMutableTreeNode) treeModel.getRoot(), ids);
        return ids;
    }

    private void collect(DefaultMutableTreeNode n, java.util.List<String> ids) {
        if (n.getUserObject() instanceof ActionNode a) ids.add(a.id());
        for (int i = 0; i < n.getChildCount(); i++) collect((DefaultMutableTreeNode) n.getChildAt(i), ids);
    }

    public Plan currentPlan() { return plan; }
    public String notesText() { return notes.getText(); }

    private void rebuild() {
        if (plan == null) {
            treeModel.setRoot(new DefaultMutableTreeNode("No plan yet"));
            notes.setText("");
            return;
        }
        Path root = ctx.inventory == null ? null : ctx.inventory.getRoot();
        long moves = plan.getActions().stream().filter(a -> a.source() != null).count();
        DefaultMutableTreeNode top = new DefaultMutableTreeNode("Proposed plan: " + moves + " move(s). Nothing changes until you approve.");
        Map<String, DefaultMutableTreeNode> folders = new LinkedHashMap<>();
        for (FileAction a : plan.getActions()) {
            Path folder = a instanceof CreateFolderAction ? a.target() : a.target().getParent();
            String key = root != null && folder.startsWith(root) ? root.relativize(folder).toString() : folder.toString();
            DefaultMutableTreeNode fn = folders.computeIfAbsent(key.isEmpty() ? "(scanned folder)" : key, k -> {
                DefaultMutableTreeNode n = new DefaultMutableTreeNode(k);
                top.add(n);
                return n;
            });
            if (a.source() != null) {
                String from = a.source().getFileName().toString();
                String to = a.target().getFileName().toString();
                fn.add(new DefaultMutableTreeNode(new ActionNode(a.id(), from.equals(to) ? from : from + " -> " + to)));
            }
        }
        treeModel.setRoot(top);
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        notes.setText(String.join("\n", plan.getWarnings()));
    }
}
