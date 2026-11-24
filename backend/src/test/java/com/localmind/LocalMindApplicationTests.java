package com.localmind;

import com.localmind.support.OfflineAiStack;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for the application context: it starts with every bean wired. Runs on the test profile (H2), with
 * no MySQL, Qdrant or Ollama.
 */
@SpringBootTest
@OfflineAiStack
@ActiveProfiles("test")
class LocalMindApplicationTests {

    @Test
    void contextLoads() {
    }
}
