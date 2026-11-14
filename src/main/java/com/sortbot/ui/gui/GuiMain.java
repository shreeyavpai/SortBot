package com.sortbot.ui.gui;

import com.sortbot.facade.FacadeFactory;
import com.sortbot.facade.SortBotFacade;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

/** GUI entry point: java -cp target/classes com.sortbot.ui.gui.GuiMain [--data-dir DIR] [--env FILE] */
public final class GuiMain {
    private GuiMain() {}

    public static void main(String[] args) {
        Path dataDir = FacadeFactory.defaultDataDir();
        Path env = Path.of(".env");
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals("--data-dir")) dataDir = Path.of(args[i + 1]);
            if (args[i].equals("--env")) env = Path.of(args[i + 1]);
        }
        SortBotFacade facade = FacadeFactory.create(dataDir, env, 12);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look and feel is fine
        }
        SwingUtilities.invokeLater(() -> new MainWindow(new GuiContext(facade)).setVisible(true));
    }
}
