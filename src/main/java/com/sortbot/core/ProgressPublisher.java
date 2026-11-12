package com.sortbot.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Subject in the Observer pattern. FileScanner and PlanExecutor extend it. */
public class ProgressPublisher {
    private final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(ProgressListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void unsubscribe(ProgressListener listener) {
        listeners.remove(listener);
    }

    protected void publish(ProgressEvent event) {
        for (ProgressListener l : listeners) l.onProgress(event);
    }
}
