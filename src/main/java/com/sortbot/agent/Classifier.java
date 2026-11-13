package com.sortbot.agent;

import com.sortbot.domain.Category;
import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Runs the classification strategies in order: deterministic rules first, the AI only for what is left (F03). */
public class Classifier {
    private final List<ClassificationStrategy> strategies;

    public Classifier(List<ClassificationStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public Inventory classify(Inventory inventory) {
        List<FileRecord> pending = new ArrayList<>(inventory.filter(r -> r.getCategory() == Category.UNCLASSIFIED));
        for (ClassificationStrategy s : strategies) {
            if (pending.isEmpty()) break;
            pending = s.classify(pending, inventory);
        }
        return inventory;
    }
}
