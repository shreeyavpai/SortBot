package com.sortbot.ui.gui;

import com.sortbot.core.CleanupCandidate;
import com.sortbot.core.NotAuthorizedException;
import com.sortbot.domain.Inventory;
import com.sortbot.domain.Plan;
import com.sortbot.facade.SortBotFacade;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Shared state and helpers for the GUI panels. All real work goes through SortBotFacade, and long
 * work runs in a SwingWorker so the window never freezes.
 */
public class GuiContext {
    /** Tests set this to run work inline and skip dialogs. */
    public static boolean synchronous = false;

    public final SortBotFacade facade;
    public final GuiProgressPanel progress = new GuiProgressPanel();
    public Component owner;

    public Inventory inventory;
    public Plan plan;
    public List<CleanupCandidate> cleanup;

    public String lastError;
    public String lastInfo;

    public Consumer<Plan> onPlanReady = p -> {};
    public Consumer<List<CleanupCandidate>> onCleanupReady = c -> {};
    public Runnable onFoldersChanged = () -> {};
    public Runnable onRulesChanged = () -> {};
    public Runnable onHistoryChanged = () -> {};
    public Runnable onInventoryChanged = () -> {};

    public GuiContext(SortBotFacade facade) {
        this.facade = facade;
        facade.subscribe(progress);
    }

    private boolean quiet() {
        return synchronous || GraphicsEnvironment.isHeadless();
    }

    public <T> void run(String busyMessage, Callable<T> work, Consumer<T> onSuccess) {
        if (synchronous) {
            try {
                onSuccess.accept(work.call());
            } catch (Exception e) {
                error(friendly(e));
            }
            return;
        }
        progress.setBusy(true, busyMessage);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                progress.setBusy(false, "");
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    error(friendly(e.getCause()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    public static String friendly(Throwable e) {
        if (e instanceof NotAuthorizedException) return "That folder is not authorized. Add it on the Folders tab first.";
        return e.getMessage() == null || e.getMessage().isBlank() ? e.toString() : e.getMessage();
    }

    public void error(String message) {
        lastError = message;
        if (quiet()) return;
        JOptionPane.showMessageDialog(owner, message, "SortBot", JOptionPane.WARNING_MESSAGE);
    }

    public void info(String message) {
        lastInfo = message;
        if (quiet()) return;
        JOptionPane.showMessageDialog(owner, message, "SortBot", JOptionPane.INFORMATION_MESSAGE);
    }

    public boolean confirm(String question) {
        if (quiet()) return true;
        return JOptionPane.showConfirmDialog(owner, question, "SortBot", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    public String ask(String question, String initial) {
        if (quiet()) return initial;
        return JOptionPane.showInputDialog(owner, question, initial);
    }
}
