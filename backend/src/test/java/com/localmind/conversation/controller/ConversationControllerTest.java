package com.localmind.conversation.controller;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.localmind.conversation.dto.CreateConversationRequest;
import com.localmind.conversation.dto.UpdateConversationRequest;
import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ChatMessageRepository;
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
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private com.localmind.attachment.repository.AttachmentRepository attachmentRepository;

    @Autowired
    private UserRepository userRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        attachmentRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(User.builder().id(UUID.randomUUID().toString()).email("user_a@localmind.ai")
                .passwordHash("hashedPassA").fullName("User A").build());

        userB = userRepository.save(User.builder().id(UUID.randomUUID().toString()).email("user_b@localmind.ai")
                .passwordHash("hashedPassB").fullName("User B").build());
    }

    @Test
    @DisplayName("POST /api/conversations should create thread scoped to user")
    void createConversation() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest("My First Chat", "gemma3:1b");

        mockMvc.perform(post("/api/conversations")
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail())))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userId", is(userA.getId()))).andExpect(jsonPath("$.title", is("My First Chat")))
                .andExpect(jsonPath("$.modelName", is("gemma3:1b"))).andExpect(jsonPath("$.pinned", is(false)));

        assertEquals(1, conversationRepository.findByUserIdOrderByUpdatedAtDesc(userA.getId()).size());
    }

    @Test
    @DisplayName("GET /api/conversations should list only authenticated user's conversations")
    void listConversations() throws Exception {
        conversationRepository.save(Conversation.builder().userId(userA.getId()).title("Chat A1").build());
        conversationRepository.save(Conversation.builder().userId(userA.getId()).title("Chat A2").build());
        conversationRepository.save(Conversation.builder().userId(userB.getId()).title("Chat B1").build());

        mockMvc.perform(get("/api/conversations")
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].userId", is(userA.getId())))
                .andExpect(jsonPath("$[1].userId", is(userA.getId())));
    }

    @Test
    @DisplayName("PATCH /api/conversations/{id} should update metadata")
    void updateConversation() throws Exception {
        Conversation conv = conversationRepository.save(Conversation.builder().userId(userA.getId()).title("Old Title")
                .pinned(false).modelName("gemma3:1b").build());

        UpdateConversationRequest updateReq = new UpdateConversationRequest("New Title", true, "gemma3:1b");

        mockMvc.perform(patch("/api/conversations/" + conv.getId())
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail())))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id", is(conv.getId())))
                .andExpect(jsonPath("$.title", is("New Title"))).andExpect(jsonPath("$.pinned", is(true)));

        Conversation updated = conversationRepository.findById(conv.getId()).orElseThrow();
        assertEquals("New Title", updated.getTitle());
        assertTrue(updated.isPinned());
    }

    @Test
    @DisplayName("GET /api/conversations/{id}/messages should load message history")
    void loadMessages() throws Exception {
        Conversation conv = conversationRepository
                .save(Conversation.builder().userId(userA.getId()).title("Chat with messages").build());

        chatMessageRepository.save(ChatMessage.builder().conversationId(conv.getId()).role(ChatMessage.Role.USER)
                .content("Hello AI").createdAt(Instant.now().minusSeconds(10)).build());

        chatMessageRepository.save(ChatMessage.builder().conversationId(conv.getId()).role(ChatMessage.Role.ASSISTANT)
                .content("Hello Human!").createdAt(Instant.now()).build());

        mockMvc.perform(get("/api/conversations/" + conv.getId() + "/messages")
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].role", is("USER"))).andExpect(jsonPath("$[0].content", is("Hello AI")))
                .andExpect(jsonPath("$[1].role", is("ASSISTANT")))
                .andExpect(jsonPath("$[1].content", is("Hello Human!")));
    }

    @Test
    @DisplayName("DELETE /api/conversations/{id} should cascade delete conversation and messages")
    void deleteConversationCascade() throws Exception {
        Conversation conv = conversationRepository
                .save(Conversation.builder().userId(userA.getId()).title("To be deleted").build());

        chatMessageRepository.save(ChatMessage.builder().conversationId(conv.getId()).role(ChatMessage.Role.USER)
                .content("Cascade me").build());

        mockMvc.perform(delete("/api/conversations/" + conv.getId())
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isNoContent());

        assertFalse(conversationRepository.existsById(conv.getId()));
        assertTrue(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).isEmpty());
    }

    @Test
    @DisplayName("IDOR prevention: User B cannot access User A's conversation")
    void idorOwnershipGuardTest() throws Exception {
        Conversation convA = conversationRepository
                .save(Conversation.builder().userId(userA.getId()).title("User A Private Chat").build());

        // User B attempts to fetch messages
        mockMvc.perform(get("/api/conversations/" + convA.getId() + "/messages")
                .with(jwt().jwt(jwt -> jwt.subject(userB.getId()).claim("email", userB.getEmail()))))
                .andExpect(status().isForbidden());

        // User B attempts to patch
        UpdateConversationRequest patchReq = new UpdateConversationRequest("Hacked Title", true, null);
        mockMvc.perform(patch("/api/conversations/" + convA.getId())
                .with(jwt().jwt(jwt -> jwt.subject(userB.getId()).claim("email", userB.getEmail())))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isForbidden());

        // User B attempts to delete
        mockMvc.perform(delete("/api/conversations/" + convA.getId())
                .with(jwt().jwt(jwt -> jwt.subject(userB.getId()).claim("email", userB.getEmail()))))
                .andExpect(status().isForbidden());

        // Verify conversation still exists untouched
        assertTrue(conversationRepository.existsById(convA.getId()));
    }
}
