package com.sortbot.agent;

/** One move proposed by the model: a file id (path relative to the scanned folder) and a relative target folder. */
public record PlannedMove(String file, String folder) {}
