package com.sortbot.llm;

/** Lets tests skip real waiting during retries. */
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
