package com.sortbot.facade;

import com.sortbot.infra.DotEnv;
import com.sortbot.llm.JdkHttpTransport;
import com.sortbot.llm.LLMClient;
import com.sortbot.llm.LLMClientCreator;
import com.sortbot.llm.LLMConfig;
import com.sortbot.llm.LLMException;

import java.nio.file.Path;

/** Builds the real application objects from the local .env file. Shared by the CLI and the GUI. */
public final class FacadeFactory {
    private FacadeFactory() {}

    /** A client for the configured provider. If the configuration is unusable, every call fails with a clear message. */
    public static LLMClient llmFromEnv(Path envFile) {
        LLMConfig cfg = LLMConfig.fromEnv(new DotEnv(envFile));
        try {
            return LLMClientCreator.forConfig(cfg, new JdkHttpTransport()).createClient();
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            return prompt -> { throw new LLMException(msg); };
        }
    }

    public static Path defaultDataDir() {
        return Path.of(System.getProperty("user.home"), ".sortbot");
    }

    public static SortBotFacade create(Path dataDir, Path envFile, int cleanupMonths) {
        return SortBotFacade.create(dataDir, llmFromEnv(envFile), cleanupMonths);
    }
}
