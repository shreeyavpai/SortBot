package com.sortbot.core;

/** Observer interface: GUI and CLI implement this to show progress (Observer pattern). */
public interface ProgressListener {
    void onProgress(ProgressEvent event);
}
