package com.sortbot.domain;

import java.util.UUID;

/** Shared id and "already executed" state for file actions. */
abstract class BaseFileAction implements FileAction {
    private final String id = UUID.randomUUID().toString().substring(0, 8);
    protected boolean executed;

    @Override
    public String id() { return id; }

    protected void requireNotExecuted() {
        if (executed) throw new IllegalStateException("Action already executed; it can no longer be edited.");
    }
}
