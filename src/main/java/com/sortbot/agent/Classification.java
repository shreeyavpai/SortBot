package com.sortbot.agent;

import com.sortbot.domain.Category;

/** The model's verdict for one file. */
public record Classification(Category category, double confidence, String reason) {}
