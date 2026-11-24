package com.localmind.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.localmind.chat.dto.ChatRequest;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.support.OfflineAiStack;
import com.localmind.user.entity.User;
import com.localmind.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * MockMvc tests for {@link ChatController}: 401 without a token, 400 for a blank message, 403 for another user's
 * conversation and the start of the async SSE response. Boots the full context on the test profile (H2) with the
 * {@link OfflineAiStack} mocks; the mocked {@link ChatModel} answers with an empty stream.
 */
@SpringBootTest
@OfflineAiStack
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatModel chatModel;

    private User userA;
    private User userB;
    private Conversation convA;

    @BeforeEach
    void setUp() {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

        conversationRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(User.builder().id(UUID.randomUUID().toString())
                .email("chattest.a." + UUID.randomUUID() + "@example.com").passwordHash("hashed").fullName("User A")
                .createdAt(Instant.now()).build());

        userB = userRepository.save(User.builder().id(UUID.randomUUID().toString())
                .email("chattest.b." + UUID.randomUUID() + "@example.com").passwordHash("hashed").fullName("User B")
                .createdAt(Instant.now()).build());

        convA = conversationRepository
                .save(Conversation.builder().id(UUID.randomUUID().toString()).userId(userA.getId()).title("Thread A")
                        .modelName("gemma3:1b").createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test
    @DisplayName("POST /api/chat/stream unauthenticated should return 401 Unauthorized")
    void streamUnauthenticatedFails() throws Exception {
        ChatRequest request = new ChatRequest(convA.getId(), "Hello", null, false, null);

        mockMvc.perform(post("/api/chat/stream").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/chat/stream with blank message should return 400 Bad Request")
    void streamBlankMessageFails() throws Exception {
        ChatRequest request = new ChatRequest(convA.getId(), "   ", null, false, null);

        mockMvc.perform(post("/api/chat/stream")
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail())))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("IDOR prevention: User B streaming User A's conversation should return 403 Forbidden")
    void streamCrossTenantForbidden() throws Exception {
        ChatRequest request = new ChatRequest(convA.getId(), "Sneak peek", null, false, null);

        mockMvc.perform(post("/api/chat/stream")
                .with(jwt().jwt(jwt -> jwt.subject(userB.getId()).claim("email", userB.getEmail())))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/chat/stream authenticated starts async SSE response")
    void streamAuthenticatedStartsAsync() throws Exception {
        ChatRequest request = new ChatRequest(convA.getId(), "Hi", null, false, null);

        mockMvc.perform(post("/api/chat/stream")
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail())))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(request().asyncStarted());
    }
}
