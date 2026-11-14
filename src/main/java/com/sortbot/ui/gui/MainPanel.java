package com.sortbot.ui.gui;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/** All the SortBot tabs, wired together. Kept separate from the window so it can be tested without a screen. */
public class MainPanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    public final GuiContext ctx;
    public final FoldersPanel folders;
    public final ScanPanel scan;
    public final PlanPanel plan;
    public final CleanupPanel cleanup;
    public final ChatPanel chat;
    public final RulesPanel rules;
    public final HistoryPanel history;

    public MainPanel(GuiContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        folders = new FoldersPanel(ctx);
        scan = new ScanPanel(ctx);
        plan = new PlanPanel(ctx);
        cleanup = new CleanupPanel(ctx);
        chat = new ChatPanel(ctx);
        rules = new RulesPanel(ctx);
        history = new HistoryPanel(ctx);
        tabs.addTab("Folders", folders);
        tabs.addTab("Scan", scan);
        tabs.addTab("Plan", plan);
        tabs.addTab("Cleanup", cleanup);
        tabs.addTab("Chat", chat);
        tabs.addTab("Rules", rules);
        tabs.addTab("History", history);
        add(tabs, BorderLayout.CENTER);
        add(ctx.progress, BorderLayout.SOUTH);

        ctx.onFoldersChanged = scan::refreshFolders;
        ctx.onRulesChanged = rules::refresh;
        ctx.onHistoryChanged = history::refresh;
        ctx.onPlanReady = p -> {
            plan.setPlan(p);
            tabs.setSelectedComponent(plan);
        };
        ctx.onCleanupReady = c -> {
            cleanup.setCandidates(c);
            tabs.setSelectedComponent(cleanup);
        };
    }

    public String selectedTabTitle() { return tabs.getTitleAt(tabs.getSelectedIndex()); }
    public int tabCount() { return tabs.getTabCount(); }
}
