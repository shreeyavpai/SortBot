package com.sortbot.agent;

import com.sortbot.domain.Category;
import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;
import com.sortbot.infra.FileSystemGateway;
import com.sortbot.llm.LLMClient;
import com.sortbot.llm.LLMException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Classifies ambiguous files with the language model, in small batches, using names and short snippets only. */
public class LLMClassificationStrategy implements ClassificationStrategy {
    static final int BATCH_SIZE = 20;

    private final LLMClient llm;
    private final PromptBuilder prompts;
    private final ResponseParser parser;
    private final FileSystemGateway gateway;

    public LLMClassificationStrategy(LLMClient llm, PromptBuilder prompts, ResponseParser parser, FileSystemGateway gateway) {
        this.llm = llm;
        this.prompts = prompts;
        this.parser = parser;
        this.gateway = gateway;
    }

    @Override public String name() { return "llm"; }

    @Override
    public List<FileRecord> classify(List<FileRecord> pending, Inventory inventory) {
        List<FileRecord> unresolved = new ArrayList<>();
        for (int i = 0; i < pending.size(); i += BATCH_SIZE) {
            List<FileRecord> batch = pending.subList(i, Math.min(pending.size(), i + BATCH_SIZE));
            List<String> snippets = new ArrayList<>();
            for (FileRecord r : batch) snippets.add(snippet(r));
            String prompt = prompts.buildClassifyPrompt(batch, snippets);
            try {
                Map<Integer, Classification> result = LlmAsk.ask(llm, prompt, parser::parseClassifications);
                for (int k = 0; k < batch.size(); k++) {
                    Classification c = result.get(k + 1);
                    if (c == null || c.category() == Category.UNCLASSIFIED) {
                        unresolved.add(batch.get(k));
                    } else {
                        batch.get(k).classify(c.category(), c.confidence(), c.reason());
                    }
                }
            } catch (LLMException | AgentParseException e) {
                inventory.addWarning("AI classification skipped for " + batch.size() + " files: " + e.getMessage());
                unresolved.addAll(batch);
            }
        }
        return unresolved;
    }

    private String snippet(FileRecord r) {
        try {
            return gateway.readSnippet(r.getPath(), PromptBuilder.MAX_SNIPPET);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
