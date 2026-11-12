package com.sortbot.core;

import com.sortbot.domain.FileRecord;

import java.util.List;

/** A file flagged for cleanup, with every reason that applies. */
public record CleanupCandidate(FileRecord record, List<String> reasons) {}
