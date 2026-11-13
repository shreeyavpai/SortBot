package com.sortbot.agent;

import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;

import java.util.List;

/** Strategy pattern: one interchangeable way to classify files. Returns the files it could not settle. */
public interface ClassificationStrategy {
    String name();

    List<FileRecord> classify(List<FileRecord> pending, Inventory inventory);
}
