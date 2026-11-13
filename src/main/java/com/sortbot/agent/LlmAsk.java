package com.sortbot.agent;

import com.sortbot.llm.LLMClient;
import com.sortbot.llm.LLMException;

/** Asks the model and, if the reply cannot be parsed, asks once more with a stricter reminder. */
final class LlmAsk {
    private static final String RETRY = "\n\nYour previous reply could not be read. Reply with ONLY the JSON object described above, with no other text.";

    interface Parser<T> {
        T parse(String reply) throws AgentParseException;
    }

    private LlmAsk() {}

    static <T> T ask(LLMClient llm, String prompt, Parser<T> parser) throws LLMException, AgentParseException {
        String first = llm.generate(prompt);
        try {
            return parser.parse(first);
        } catch (AgentParseException e) {
            return parser.parse(llm.generate(prompt + RETRY));
        }
    }
}
