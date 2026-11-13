package com.sortbot.agent;

import com.sortbot.TestFiles;
import com.sortbot.core.FileScanner;
import com.sortbot.core.FolderAuthorizer;
import com.sortbot.core.MemoryManager;
import com.sortbot.core.RuleRepository;
import com.sortbot.core.TraceLogger;
import com.sortbot.domain.Inventory;
import com.sortbot.infra.FileSystemGateway;
import com.sortbot.llm.LLMClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds a small wired-up agent around a temporary folder for the agent tests. */
class AgentTestSupport {
    final Path root;
    final FolderAuthorizer authorizer = new FolderAuthorizer();
    final FileSystemGateway gateway;
    final TraceLogger trace = new TraceLogger();
    final MemoryManager memory = new MemoryManager(new RuleRepository(null));
    final PromptBuilder prompts = new PromptBuilder();
    final ResponseParser parser = new ResponseParser();
    final PlanValidator validator = new PlanValidator(authorizer);

    AgentTestSupport(Path tmp) throws IOException {
        root = Files.createDirectory(tmp.resolve("Downloads"));
        authorizer.add(root);
        gateway = new FileSystemGateway(authorizer);
    }

    Planner planner(LLMClient llm) {
        return new Planner(llm, prompts, parser, validator, memory, gateway, trace);
    }

    Classifier classifier(LLMClient llm) {
        return new Classifier(java.util.List.of(new NameRuleStrategy(), new LLMClassificationStrategy(llm, prompts, parser, gateway)));
    }

    Inventory scan(String... names) throws IOException {
        for (String n : names) TestFiles.write(root, n, "content of " + n);
        return new FileScanner(gateway).scan(root, true);
    }
}
