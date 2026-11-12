package com.localmind.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.auth.dto.LoginRequest;
import com.localmind.auth.dto.RegisterRequest;
import com.localmind.auth.entity.RefreshToken;
import com.localmind.auth.repository.RefreshTokenRepository;
import com.localmind.chat.dto.ChatRequest;
import com.localmind.conversation.dto.CreateConversationRequest;
import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ChatMessageRepository;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.user.entity.User;
import com.localmind.user.repository.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "LOCALMIND_LIVE_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndChatIntegrationTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CHAT_STREAM_TIMEOUT = Duration.ofSeconds(60);

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        baseUrl = "http://localhost:" + port;

        // Clean database state
        chatMessageRepository.deleteAll();
        attachmentRepository.deleteAll();
        conversationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    @DisplayName("End-to-End: Register -> Login -> Create Conversation -> Stream Chat with gemma3:1b -> Verify MySQL Persistence")
    void testCompleteChatLifecycleWithModel() throws Exception {
        String testEmail = "e2e." + UUID.randomUUID() + "@localmind.ai";
        String testPassword = "Password123!";
        String testFullName = "LocalMind E2E Auditor";

        // ====================================================================
        // Step 1: User Registration (POST /api/auth/register)
        // ====================================================================
        RegisterRequest registerRequest = new RegisterRequest(testEmail, testPassword, testFullName);
        HttpRequest regHttpReq = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/auth/register"))
                .header("Content-Type", "application/json").timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(registerRequest))).build();

        HttpResponse<String> regHttpRes = httpClient.send(regHttpReq, HttpResponse.BodyHandlers.ofString());
        assertThat(regHttpRes.statusCode()).isEqualTo(201);

        JsonNode regBody = objectMapper.readTree(regHttpRes.body());
        String registeredUserId = regBody.get("id").asString();
        assertThat(registeredUserId).isNotBlank();
        assertThat(regBody.get("email").asString()).isEqualTo(testEmail);
        assertThat(regBody.get("fullName").asString()).isEqualTo(testFullName);

        // Verify MySQL persistence for User
        Optional<User> persistedUser = userRepository.findByEmail(testEmail);
        assertThat(persistedUser).isPresent();
        assertThat(persistedUser.get().getId()).isEqualTo(registeredUserId);
        assertThat(persistedUser.get().getFullName()).isEqualTo(testFullName);
        assertThat(persistedUser.get().getPasswordHash()).isNotBlank().isNotEqualTo(testPassword); // BCrypt hashed

        // ====================================================================
        // Step 2: User Login (POST /api/auth/login)
        // ====================================================================
        LoginRequest loginRequest = new LoginRequest(testEmail, testPassword);
        HttpRequest loginHttpReq = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/json").timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(loginRequest))).build();

        HttpResponse<String> loginHttpRes = httpClient.send(loginHttpReq, HttpResponse.BodyHandlers.ofString());
        assertThat(loginHttpRes.statusCode()).isEqualTo(200);

        JsonNode loginBody = objectMapper.readTree(loginHttpRes.body());
        String accessToken = loginBody.get("accessToken").asString();
        assertThat(accessToken).isNotBlank();
        assertThat(loginBody.get("tokenType").asString()).isEqualTo("Bearer");

        // Verify refresh token cookie in header
        Optional<String> setCookieHeader = loginHttpRes.headers().firstValue("Set-Cookie");
        assertThat(setCookieHeader).isPresent();
        assertThat(setCookieHeader.get()).contains("refreshToken=");

        // Verify MySQL persistence for RefreshToken audit record
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).isNotEmpty();
        assertThat(tokens.stream().anyMatch(t -> t.getUserId().equals(registeredUserId) && !t.isRevoked())).isTrue();

        // ====================================================================
        // Step 3: Create Conversation (POST /api/conversations)
        // ====================================================================
        CreateConversationRequest convRequest = new CreateConversationRequest("E2E Verification Thread", "gemma3:1b");
        HttpRequest convHttpReq = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/conversations"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + accessToken)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(convRequest))).build();

        HttpResponse<String> convHttpRes = httpClient.send(convHttpReq, HttpResponse.BodyHandlers.ofString());
        assertThat(convHttpRes.statusCode()).isEqualTo(201);

        JsonNode convBody = objectMapper.readTree(convHttpRes.body());
        String conversationId = convBody.get("id").asString();
        assertThat(conversationId).isNotBlank();
        assertThat(convBody.get("title").asString()).isEqualTo("E2E Verification Thread");
        assertThat(convBody.get("modelName").asString()).isEqualTo("gemma3:1b");

        // Verify MySQL persistence for Conversation
        Optional<Conversation> persistedConv = conversationRepository.findById(conversationId);
        assertThat(persistedConv).isPresent();
        assertThat(persistedConv.get().getUserId()).isEqualTo(registeredUserId);
        assertThat(persistedConv.get().getModelName()).isEqualTo("gemma3:1b");
        assertThat(persistedConv.get().isPinned()).isFalse();

        // ====================================================================
        // Step 4: Stream Chat with gemma3:1b (POST /api/chat/stream)
        // ====================================================================
        String testPrompt = "Please reply with exactly the phrase: E2E_VERIFIED_SUCCESS";
        ChatRequest chatRequest = new ChatRequest(conversationId, testPrompt, "gemma3:1b", false,
                "You are an automated verification agent. Output only what is requested.");

        HttpRequest chatHttpReq = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/chat/stream"))
                .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + accessToken).timeout(CHAT_STREAM_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(chatRequest))).build();

        HttpResponse<String> chatHttpRes = httpClient.send(chatHttpReq, HttpResponse.BodyHandlers.ofString());
        assertThat(chatHttpRes.statusCode()).isEqualTo(200);
        assertThat(chatHttpRes.headers().firstValue("Content-Type").orElse("")).contains("text/event-stream");

        String sseBody = chatHttpRes.body();
        assertThat(sseBody).isNotBlank();
        assertThat(sseBody).contains("data:");
        // Ensure streaming completed successfully without errors
        assertThat(sseBody).doesNotContain("\"type\":\"ERROR\"");
        assertThat(sseBody).contains("\"type\":\"DONE\"");
        assertThat(sseBody.contains("\"type\":\"CONTENT\"") || sseBody.contains("\"type\":\"REASONING\""))
                .as("SSE stream must contain CONTENT or REASONING tokens").isTrue();

        // ====================================================================
        // Step 5: Verify Messages & Conversation Timestamps in MySQL
        // ====================================================================
        List<ChatMessage> persistedMessages = chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
        // Both user prompt and assistant response must be persisted in MySQL
        assertThat(persistedMessages).hasSizeGreaterThanOrEqualTo(2);

        // Check User ChatMessage
        ChatMessage userMsg = persistedMessages.get(0);
        assertThat(userMsg.getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(userMsg.getContent()).isEqualTo(testPrompt);
        assertThat(userMsg.getCreatedAt()).isNotNull();

        // Check Assistant ChatMessage
        ChatMessage assistantMsg = persistedMessages.get(1);
        assertThat(assistantMsg.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        boolean hasContent = (assistantMsg.getContent() != null && !assistantMsg.getContent().isBlank());
        boolean hasReasoning = (assistantMsg.getReasoningContent() != null
                && !assistantMsg.getReasoningContent().isBlank());
        assertThat(hasContent || hasReasoning).as("Assistant message must contain either content or reasoning tokens")
                .isTrue();
        assertThat(assistantMsg.getCreatedAt()).isNotNull();

        // Check Conversation updatedAt updated
        Conversation updatedConv = conversationRepository.findById(conversationId).orElseThrow();
        assertThat(updatedConv.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("End-to-End: History retrieval and multi-tenant security isolation")
    void testHistoryAndSecurityIsolation() throws Exception {
        // Register User 1
        String user1Email = "user1." + UUID.randomUUID() + "@localmind.ai";
        RegisterRequest reg1 = new RegisterRequest(user1Email, "Password123!", "User One");
        HttpResponse<String> reg1Res = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/auth/register"))
                        .header("Content-Type", "application/json").timeout(REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(reg1))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(reg1Res.statusCode()).isEqualTo(201);

        String user1Token = objectMapper.readTree(httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/auth/login"))
                        .header("Content-Type", "application/json").timeout(REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(new LoginRequest(user1Email, "Password123!"))))
                        .build(),
                HttpResponse.BodyHandlers.ofString()).body()).get("accessToken").asString();

        // User 1 creates conversation
        CreateConversationRequest conv1 = new CreateConversationRequest("User 1 Secret Project", "gemma3:1b");
        String convId = objectMapper
                .readTree(
                        httpClient.send(
                                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/conversations"))
                                        .header("Content-Type", "application/json")
                                        .header("Authorization", "Bearer " + user1Token).timeout(REQUEST_TIMEOUT)
                                        .POST(HttpRequest.BodyPublishers
                                                .ofString(objectMapper.writeValueAsString(conv1)))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString()).body())
                .get("id").asString();

        // Persist a message in User 1's conversation
        chatMessageRepository.save(ChatMessage.builder().conversationId(convId).role(ChatMessage.Role.USER)
                .content("Confidential Plan").build());

        // 1. User 1 successfully retrieves message history
        HttpResponse<String> user1HistoryRes = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/conversations/" + convId + "/messages"))
                        .header("Authorization", "Bearer " + user1Token).timeout(REQUEST_TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(user1HistoryRes.statusCode()).isEqualTo(200);
        JsonNode messagesArray = objectMapper.readTree(user1HistoryRes.body());
        assertThat(messagesArray.isArray()).isTrue();
        assertThat(messagesArray.size()).isEqualTo(1);
        assertThat(messagesArray.get(0).get("content").asString()).isEqualTo("Confidential Plan");

        // Register User 2 (Attacker / Different Tenant)
        String user2Email = "user2." + UUID.randomUUID() + "@localmind.ai";
        httpClient.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/auth/register"))
                .header("Content-Type", "application/json").timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(new RegisterRequest(user2Email, "Password123!", "User Two"))))
                .build(), HttpResponse.BodyHandlers.ofString());
        String user2Token = objectMapper.readTree(httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/auth/login"))
                        .header("Content-Type", "application/json").timeout(REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(new LoginRequest(user2Email, "Password123!"))))
                        .build(),
                HttpResponse.BodyHandlers.ofString()).body()).get("accessToken").asString();

        // 2. User 2's conversation list does NOT leak User 1's conversation
        HttpResponse<String> user2ListRes = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/conversations"))
                        .header("Authorization", "Bearer " + user2Token).timeout(REQUEST_TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(user2ListRes.statusCode()).isEqualTo(200);
        assertThat(user2ListRes.body()).doesNotContain(convId);

        // 3. Unauthenticated request to User 1's conversation should be rejected (401)
        HttpResponse<String> unauthRes = httpClient
                .send(HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/conversations/" + convId + "/messages"))
                        .timeout(REQUEST_TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(unauthRes.statusCode()).isEqualTo(401);

        // 4. User 2 attempting to access User 1's conversation messages should be 403
        // Forbidden
        HttpResponse<String> crossTenantRes = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/conversations/" + convId + "/messages"))
                        .header("Authorization", "Bearer " + user2Token).timeout(REQUEST_TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(crossTenantRes.statusCode()).isEqualTo(403);

        // 5. User 2 attempting to stream chat in User 1's conversation should be 403
        // Forbidden
        ChatRequest maliciousChat = new ChatRequest(convId, "Unauthorized prompt", "gemma3:1b", false, null);
        HttpResponse<String> maliciousChatRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat/stream")).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + user2Token).timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(maliciousChat))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(maliciousChatRes.statusCode()).isEqualTo(403);
    }
}
