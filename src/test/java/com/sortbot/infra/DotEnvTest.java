package com.sortbot.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotEnvTest {
    @TempDir Path tmp;

    @Test
    void readsKeysIgnoringCommentsBlankLinesQuotesAndExport() throws IOException {
        Path f = Files.writeString(tmp.resolve(".env"),
                "# comment\n\nSORTBOT_T_ONE=abc\nSORTBOT_T_TWO=\"quoted value\"\nexport SORTBOT_T_THREE='single'\nbadline\n  SORTBOT_T_FOUR = spaced  \n");
        DotEnv env = new DotEnv(f);
        assertEquals("abc", env.get("SORTBOT_T_ONE"));
        assertEquals("quoted value", env.get("SORTBOT_T_TWO"));
        assertEquals("single", env.get("SORTBOT_T_THREE"));
        assertEquals("spaced", env.get("SORTBOT_T_FOUR"));
        assertNull(env.get("badline"));
    }

    @Test
    void missingFileFallsBackToDefaults() {
        DotEnv env = new DotEnv(tmp.resolve("does-not-exist.env"));
        assertNull(env.get("SORTBOT_T_MISSING"));
        assertEquals("fallback", env.get("SORTBOT_T_MISSING", "fallback"));
    }
}
