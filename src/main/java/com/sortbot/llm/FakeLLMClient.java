package com.sortbot.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Scripted model for tests: never touches the network. Records every prompt it receives. */
public class FakeLLMClient implements LLMClient {
    private final Function<String, String> responder;
    private final List<String> prompts = new ArrayList<>();

    public FakeLLMClient(List<String> replies) {
        List<String> queue = new ArrayList<>(replies);
        this.responder = p -> {
            if (queue.isEmpty()) return "";
            return queue.size() == 1 ? queue.get(0) : queue.remove(0);
        };
    }

    public FakeLLMClient(Function<String, String> responder) {
        this.responder = responder;
    }

    /** A fake that always fails, to test "model unavailable" behavior. */
    public static FakeLLMClient failing(String message) {
        return new FakeLLMClient(p -> { throw new IllegalStateException(message); });
    }

    @Override
    public String generate(String prompt) throws LLMException {
        prompts.add(prompt);
        try {
            return responder.apply(prompt);
        } catch (IllegalStateException e) {
            throw new LLMException(e.getMessage());
        }
    }

    public List<String> prompts() { return prompts; }
    public int calls() { return prompts.size(); }
}
