package com.sortbot.llm;

import java.io.IOException;
import java.util.Map;

/** Minimal HTTP POST abstraction so the clients can be tested without a network. */
public interface HttpTransport {
    record Response(int status, String body) {}

    Response post(String url, Map<String, String> headers, String body) throws IOException;
}
