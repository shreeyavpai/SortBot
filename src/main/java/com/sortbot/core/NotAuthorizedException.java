package com.sortbot.core;

import java.nio.file.Path;

/** Thrown when code tries to touch a path outside the authorized folders. */
public class NotAuthorizedException extends RuntimeException {
    public NotAuthorizedException(Path path) {
        super("Not authorized: " + path);
    }
}
