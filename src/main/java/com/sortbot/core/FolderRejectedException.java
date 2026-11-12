package com.sortbot.core;

/** Thrown when a folder cannot be added to the allow-list (with a user-readable reason). */
public class FolderRejectedException extends RuntimeException {
    public FolderRejectedException(String message) {
        super(message);
    }
}
