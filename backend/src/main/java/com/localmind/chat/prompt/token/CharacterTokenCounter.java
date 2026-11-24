package com.localmind.chat.prompt.token;

import org.springframework.stereotype.Component;

/**
 * Estimates tokens as one per four characters (at least one for non-empty text), a model-independent approximation that
 * is good enough for prompt budgets.
 */
@Component
public class CharacterTokenCounter implements TokenCounter {

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
