package com.localmind.chat.prompt;

import org.springframework.stereotype.Component;

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
