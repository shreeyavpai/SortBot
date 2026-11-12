package com.sortbot.core;

/** A progress update published while scanning or executing. */
public record ProgressEvent(String stage, int current, int total, String message) {}
