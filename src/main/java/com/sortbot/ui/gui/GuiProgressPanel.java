package com.sortbot.ui.gui;

import com.sortbot.core.ProgressEvent;
import com.sortbot.core.ProgressListener;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

/** Observer for the GUI: shows scan and execution progress at the bottom of the window. */
public class GuiProgressPanel extends JPanel implements ProgressListener {
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JLabel label = new JLabel("Ready");

    public GuiProgressPanel() {
        super(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(label, BorderLayout.WEST);
        add(bar, BorderLayout.CENTER);
    }

    @Override
    public void onProgress(ProgressEvent e) {
        SwingUtilities.invokeLater(() -> {
            bar.setIndeterminate(false);
            bar.setMaximum(Math.max(1, e.total()));
            bar.setValue(e.current());
            label.setText(e.stage() + ": " + e.current() + "/" + e.total());
        });
    }

    public void setBusy(boolean busy, String message) {
        Runnable r = () -> {
            bar.setIndeterminate(busy);
            if (!busy) bar.setValue(0);
            label.setText(busy ? message : "Ready");
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    public String statusText() { return label.getText(); }
}
