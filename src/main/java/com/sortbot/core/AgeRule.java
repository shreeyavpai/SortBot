package com.sortbot.core;

import com.sortbot.domain.FileRecord;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;

/** Flags files not modified for a number of months (default 12, configurable). */
public class AgeRule implements CleanupRule {
    private final int months;
    private final Clock clock;

    public AgeRule(int months) {
        this(months, Clock.systemDefaultZone());
    }

    public AgeRule(int months, Clock clock) {
        if (months < 1) throw new IllegalArgumentException("Age threshold must be at least 1 month.");
        this.months = months;
        this.clock = clock;
    }

    @Override public String name() { return "old-file"; }

    @Override
    public boolean matches(FileRecord r) {
        Instant cutoff = ZonedDateTime.now(clock).minusMonths(months).toInstant();
        return r.getModified().isBefore(cutoff);
    }

    @Override
    public String reason(FileRecord r) {
        return "Not modified in over " + months + " months";
    }
}
