package com.sortbot.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LLMClientsTest {
    /** Scripted HTTP transport that records requests. */
    static class FakeHttp implements HttpTransport {
        final List<Object> script = new ArrayList<>();
        final List<String> urls = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();

        FakeHttp reply(int status, String body) { script.add(new Response(status, body)); return this; }
        FakeHttp fail(String msg) { script.add(new IOException(msg)); return this; }

        @Override
        public Response post(String url, Map<String, String> h, String body) throws IOException {
            urls.add(url);
            headers.add(h);
            bodies.add(body);
            Object next = script.size() > 1 ? script.remove(0) : script.get(0);
            if (next instanceof IOException e) throw e;
            return (Response) next;
        }
    }

    static final String GEMINI_OK = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hello\"}]}}]}";
    static final String SECRET = "SUPER-SECRET-KEY";

    private LLMConfig gemini() { return new LLMConfig("gemini", "test-model", SECRET, null, null); }

    @Test
    void geminiSendsTheKeyInAHeaderNeverInTheUrlAndReturnsTheText() throws Exception {
        FakeHttp http = new FakeHttp().reply(200, GEMINI_OK);
        assertEquals("hello", new GeminiClient(gemini(), http, ms -> {}).generate("say hi"));
        assertTrue(http.urls.get(0).contains("test-model:generateContent"));
        assertFalse(http.urls.get(0).contains(SECRET));
        assertEquals(SECRET, http.headers.get(0).get("x-goog-api-key"));
        assertTrue(http.bodies.get(0).contains("say hi"));
    }

    @Test
    void geminiRetriesRateLimitsThenSucceeds() throws Exception {
        FakeHttp http = new FakeHttp().reply(429, "slow down").reply(503, "busy").reply(200, GEMINI_OK);
        List<Long> waits = new ArrayList<>();
        assertEquals("hello", new GeminiClient(gemini(), http, waits::add).generate("x"));
        assertEquals(3, http.urls.size());
        assertEquals(2, waits.size());
    }

    @Test
    void geminiGivesUpAfterThreeAttempts() {
        FakeHttp http = new FakeHttp().reply(429, "slow down");
        LLMException e = assertThrows(LLMException.class, () -> new GeminiClient(gemini(), http, ms -> {}).generate("x"));
        assertEquals(3, http.urls.size());
        assertTrue(e.getMessage().contains("429"));
    }

    @Test
    void geminiRejectedKeyFailsImmediatelyWithoutLeakingTheKey() {
        FakeHttp http = new FakeHttp().reply(403, "{\"error\":\"bad key " + SECRET + "\"}");
        LLMException e = assertThrows(LLMException.class, () -> new GeminiClient(gemini(), http, ms -> {}).generate("x"));
        assertEquals(1, http.urls.size());
        assertFalse(e.getMessage().contains(SECRET));
        assertTrue(e.getMessage().contains("LLM_API_KEY"));
    }

    @Test
    void geminiReportsMissingKeyModelAndUnknownModel() {
        FakeHttp http = new FakeHttp().reply(404, "nope");
        assertThrows(LLMException.class, () -> new GeminiClient(new LLMConfig("gemini", "m", "", null, null), http).generate("x"));
        assertThrows(LLMException.class, () -> new GeminiClient(new LLMConfig("gemini", "", "k", null, null), http).generate("x"));
        LLMException e = assertThrows(LLMException.class, () -> new GeminiClient(gemini(), http, ms -> {}).generate("x"));
        assertTrue(e.getMessage().contains("test-model"));
    }

    @Test
    void geminiHandlesNetworkFailuresAndUnusableReplies() {
        assertThrows(LLMException.class, () -> new GeminiClient(gemini(), new FakeHttp().fail("timeout"), ms -> {}).generate("x"));
        assertThrows(LLMException.class, () -> new GeminiClient(gemini(), new FakeHttp().reply(200, "{\"candidates\":[]}"), ms -> {}).generate("x"));
        assertThrows(LLMException.class, () -> new GeminiClient(gemini(), new FakeHttp().reply(200, "<html>"), ms -> {}).generate("x"));
    }

    @Test
    void ollamaPostsToTheLocalServerAndReturnsTheResponseField() throws Exception {
        FakeHttp http = new FakeHttp().reply(200, "{\"response\":\"local reply\",\"done\":true}");
        LLMConfig cfg = new LLMConfig("ollama", null, null, "http://localhost:11434/", "tiny");
        assertEquals("local reply", new OllamaClient(cfg, http).generate("hi"));
        assertEquals("http://localhost:11434/api/generate", http.urls.get(0));
        assertTrue(http.bodies.get(0).contains("\"stream\":false"));
    }

    @Test
    void ollamaErrorsAreReadable() {
        LLMConfig cfg = new LLMConfig("ollama", null, null, null, "tiny");
        assertTrue(assertThrows(LLMException.class, () -> new OllamaClient(cfg, new FakeHttp().fail("refused")).generate("x")).getMessage().contains("Is it running"));
        assertThrows(LLMException.class, () -> new OllamaClient(cfg, new FakeHttp().reply(500, "boom")).generate("x"));
        assertThrows(LLMException.class, () -> new OllamaClient(cfg, new FakeHttp().reply(200, "garbage")).generate("x"));
        assertThrows(LLMException.class, () -> new OllamaClient(new LLMConfig("ollama", null, null, null, ""), new FakeHttp()).generate("x"));
    }

    @Test
    void factoryBuildsTheRightClientAndRejectsUnknownProviders() {
        HttpTransport http = new FakeHttp();
        assertTrue(LLMClientCreator.forConfig(gemini(), http).createClient() instanceof GeminiClient);
        assertTrue(LLMClientCreator.forConfig(new LLMConfig("OLLAMA", null, null, null, "m"), http).createClient() instanceof OllamaClient);
        assertThrows(IllegalArgumentException.class, () -> LLMClientCreator.forConfig(new LLMConfig("skynet", null, null, null, null), http));
    }

    @Test
    void configNeverPrintsTheKey() {
        assertFalse(gemini().toString().contains(SECRET));
    }

    @Test
    void fakeClientReplaysRepliesAndRecordsPrompts() throws Exception {
        FakeLLMClient f = new FakeLLMClient(List.of("one", "two"));
        assertEquals("one", f.generate("p1"));
        assertEquals("two", f.generate("p2"));
        assertEquals("two", f.generate("p3"));
        assertEquals(3, f.calls());
        assertThrows(LLMException.class, () -> FakeLLMClient.failing("down").generate("x"));
    }
}
