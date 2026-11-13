package com.sortbot.agent;

/** A plan could not be produced (model unavailable or its reply unusable). The message is user-readable. */
public class PlanningException extends RuntimeException {
    public PlanningException(String message) { super(message); }
}
