package com.localmind.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.localmind.chat.dto.ChatEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link StreamingTagParser}: reasoning tags split across chunks and flushing a trailing partial tag as
 * content.
 */
class StreamingTagParserTest {

    @Test
    @DisplayName("StreamingTagParser handles <think> and </think> tags split across token chunks")
    void streamingTagParserHandlesSplitTokens() {
        StreamingTagParser parser = new StreamingTagParser();
        Flux<ChatEvent> flux = Flux.create(sink -> {
            parser.processChunk("Hello! <th", sink);
            parser.processChunk("ink>This is ", sink);
            parser.processChunk("deep thinking</thin", sink);
            parser.processChunk("k>Here is the answer.", sink);
            parser.flush(sink);
            sink.complete();
        });

        StepVerifier.create(flux)
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.CONTENT && "Hello! ".equals(e.content()))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.REASONING && "This is ".equals(e.content()))
                .expectNextMatches(
                        e -> e.type() == ChatEvent.EventType.REASONING && "deep thinking".equals(e.content()))
                .expectNextMatches(
                        e -> e.type() == ChatEvent.EventType.CONTENT && "Here is the answer.".equals(e.content()))
                .verifyComplete();

        assertEquals("Hello! Here is the answer.", parser.getAccumulatedContent());
        assertEquals("This is deep thinking", parser.getAccumulatedReasoning());
    }

    @Test
    @DisplayName("StreamingTagParser flush should emit pending buffer if stream ends with partial tag")
    void streamingTagParserFlushPending() {
        StreamingTagParser parser = new StreamingTagParser();
        Flux<ChatEvent> flux = Flux.create(sink -> {
            parser.processChunk("Testing <th", sink);
            parser.flush(sink);
            sink.complete();
        });

        StepVerifier.create(flux)
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.CONTENT && "Testing ".equals(e.content()))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.CONTENT && "<th".equals(e.content()))
                .verifyComplete();

        assertEquals("Testing <th", parser.getAccumulatedContent());
    }
}
