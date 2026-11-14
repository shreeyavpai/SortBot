package com.sortbot.ui.gui;

import com.sortbot.agent.ChatReply;
import com.sortbot.domain.UserRule;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;

/** F09: talk to the agent in plain language. File changes only ever appear as a plan to review. */
public class ChatPanel extends JPanel {
    private final GuiContext ctx;
    private final JTextArea transcript = new JTextArea();
    private final JTextField input = new JTextField();
    private final JButton saveRule = new JButton("Save proposed rule");
    private UserRule proposed;

    public ChatPanel(GuiContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setText("SortBot: Scan a folder first, then tell me what you want, for example \"put my tax documents in one folder\".\n");
        add(new JScrollPane(transcript), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        JButton send = new JButton("Send");
        send.addActionListener(e -> send(input.getText()));
        input.addActionListener(e -> send(input.getText()));
        saveRule.setEnabled(false);
        saveRule.addActionListener(e -> saveProposedRule());
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        bottom.add(saveRule, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    public void send(String message) {
        if (message == null || message.isBlank()) return;
        input.setText("");
        transcript.append("You: " + message + "\n");
        ctx.run("Thinking...", () -> ctx.facade.handleChat(message), this::show);
    }

    private void show(ChatReply r) {
        transcript.append("SortBot: " + r.text() + "\n");
        if (r.plan() != null && r.plan().size() > 0) {
            ctx.plan = r.plan();
            ctx.onPlanReady.accept(r.plan());
            transcript.append("SortBot: The plan is on the Plan tab. Review it there; nothing has changed yet.\n");
        }
        if (r.cleanup() != null && !r.cleanup().isEmpty()) {
            ctx.onCleanupReady.accept(r.cleanup());
            transcript.append("SortBot: The cleanup list is on the Cleanup tab. Nothing has been moved.\n");
        }
        proposed = r.proposedRule();
        saveRule.setEnabled(proposed != null);
    }

    public void saveProposedRule() {
        if (proposed == null) return;
        try {
            UserRule saved = ctx.facade.confirmPendingRule();
            transcript.append("SortBot: Saved the rule: " + saved.describe() + "\n");
            proposed = null;
            saveRule.setEnabled(false);
            ctx.onRulesChanged.run();
        } catch (RuntimeException e) {
            ctx.error(GuiContext.friendly(e));
        }
    }

    public String transcriptText() { return transcript.getText(); }
    public boolean canSaveRule() { return saveRule.isEnabled(); }
}
