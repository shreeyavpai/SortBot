package com.sortbot.ui.cli;

import com.sortbot.core.ProgressEvent;
import com.sortbot.core.ProgressListener;

import java.io.PrintStream;

/** Observer for the command line: prints progress at roughly every 25 percent. */
public class CliProgressPrinter implements ProgressListener {
    private final PrintStream out;
    private int lastBucket = -1;

    public CliProgressPrinter(PrintStream out) { this.out = out; }

    @Override
    public void onProgress(ProgressEvent e) {
        if (e.total() <= 0) return;
        int bucket = e.current() * 4 / e.total();
        if (e.current() == e.total() || bucket != lastBucket) {
            lastBucket = bucket;
            out.println("  " + e.stage() + ": " + e.current() + "/" + e.total());
        }
        if (e.current() == e.total()) lastBucket = -1;
    }
}
