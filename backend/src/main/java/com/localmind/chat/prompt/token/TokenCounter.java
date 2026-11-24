package com.localmind.chat.prompt.token;

/** Estimates how many tokens a text takes up in the prompt, for budgeting. */
public interface TokenCounter {
    int estimate(String text);
}
