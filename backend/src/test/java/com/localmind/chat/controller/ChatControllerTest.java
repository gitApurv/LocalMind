package com.localmind.chat.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.localmind.chat.dto.ChatRequest;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.user.entity.User;
import com.localmind.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
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

    private User userA;
    private User userB;
    private Conversation convA;

    @BeforeEach
    void setUp() {
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
