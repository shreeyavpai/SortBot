package com.sortbot.core;

import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;

/** Strategy pattern: one interchangeable way of deciding that a file is a cleanup candidate. */
public interface CleanupRule {
    String name();

    /** Called once before matching, so rules that need the whole inventory (duplicates) can prepare. */
    default void prepare(Inventory inventory) {}

    boolean matches(FileRecord record);

    /** Human-readable reason shown to the user. */
    String reason(FileRecord record);
}
