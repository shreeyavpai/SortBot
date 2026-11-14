package com.sortbot.ui.gui;

import javax.swing.JFrame;
import java.awt.Dimension;

/** The application window. */
public class MainWindow extends JFrame {
    public MainWindow(GuiContext ctx) {
        super("SortBot: safe, AI-assisted folder organizing");
        MainPanel panel = new MainPanel(ctx);
        ctx.owner = this;
        setContentPane(panel);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1000, 650));
        pack();
        setLocationRelativeTo(null);
    }
}
