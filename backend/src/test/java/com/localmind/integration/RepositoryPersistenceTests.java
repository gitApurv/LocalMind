package com.localmind.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.auth.entity.RefreshToken;
import com.localmind.auth.repository.RefreshTokenRepository;
import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ChatMessageRepository;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.memory.entity.UserMemory;
import com.localmind.memory.repository.UserMemoryRepository;
import com.localmind.user.entity.User;
import com.localmind.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepositoryPersistenceTests {

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

    @Autowired
    private UserMemoryRepository userMemoryRepository;

    @Test
    @DisplayName("User lifecycle: Persist, auto-generate UUID, query by email, check existence")
    void testUserPersistence() {
        User user = User.builder().email("alice@localmind.dev").passwordHash("$2a$12$e8YV0.mockedHashedPassword")
                .fullName("Alice Developer").customInstructions("Always reply in Python and concise markdown").build();

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull().hasSize(36);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(userRepository.existsByEmail("alice@localmind.dev")).isTrue();

        Optional<User> fetched = userRepository.findByEmail("alice@localmind.dev");
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getFullName()).isEqualTo("Alice Developer");
        assertThat(fetched.get().getCustomInstructions()).contains("concise markdown");
    }

    @Test
    @DisplayName("RefreshToken lifecycle: Persist, query by hash, revoke and delete by userId")
    void testRefreshTokenPersistence() {
        User user = userRepository
                .saveAndFlush(User.builder().email("bob@localmind.dev").passwordHash("hashed").build());

        RefreshToken token = RefreshToken.builder().userId(user.getId()).tokenHash("sample_sha256_hash_value_12345")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS)).build();

        RefreshToken saved = refreshTokenRepository.saveAndFlush(token);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isRevoked()).isFalse();

        Optional<RefreshToken> fetched = refreshTokenRepository.findByTokenHash("sample_sha256_hash_value_12345");
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getUserId()).isEqualTo(user.getId());

        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.flush();

        assertThat(refreshTokenRepository.findByTokenHash("sample_sha256_hash_value_12345")).isEmpty();
    }

    @Test
    @DisplayName("Conversation and ChatMessage hierarchy: Multi-tenant ownership, ENUM mapping, JSON tool_calls")
    void testConversationAndMessagePersistence() {
        User user = userRepository
                .saveAndFlush(User.builder().email("charlie@localmind.dev").passwordHash("hashed").build());

        Conversation conv = Conversation.builder().userId(user.getId()).title("Quantum Computing Fundamentals")
                .modelName("gemma3:1b").pinned(true).build();

        Conversation savedConv = conversationRepository.saveAndFlush(conv);
        assertThat(savedConv.getId()).isNotNull();
        assertThat(savedConv.isPinned()).isTrue();
        assertThat(savedConv.getModelName()).isEqualTo("gemma3:1b");

        // Verify multi-tenant ownership guard query
        assertThat(conversationRepository.existsByIdAndUserId(savedConv.getId(), user.getId())).isTrue();
        assertThat(conversationRepository.existsByIdAndUserId(savedConv.getId(), "unauthorized_user_id")).isFalse();

        // Create Messages with explicit chronological order
        Instant now = Instant.now();
        ChatMessage userMsg = ChatMessage.builder().conversationId(savedConv.getId()).role(ChatMessage.Role.USER)
                .content("What is quantum entanglement?").tokenCount(15).createdAt(now.minus(5, ChronoUnit.SECONDS))
                .build();

        ChatMessage assistantMsg = ChatMessage.builder().conversationId(savedConv.getId())
                .role(ChatMessage.Role.ASSISTANT).content("Quantum entanglement is a physical phenomenon...")
                .reasoningContent("Considering Bell's inequality and EPR paradox...")
                .toolCalls("[{\"tool\":\"searchWeb\",\"query\":\"quantum entanglement 2026\"}]").tokenCount(120)
                .createdAt(now).build();

        chatMessageRepository.saveAllAndFlush(List.of(userMsg, assistantMsg));

        List<ChatMessage> history = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(savedConv.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(history.get(1).getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(history.get(1).getReasoningContent()).contains("Bell's inequality");
        assertThat(history.get(1).getToolCalls()).contains("searchWeb");
    }

    @Test
    @DisplayName("Attachment lifecycle: Status ENUM, ordering, and user-scoping")
    void testAttachmentPersistence() {
        User user = userRepository
                .saveAndFlush(User.builder().email("dave@localmind.dev").passwordHash("hashed").build());

        Conversation conv = conversationRepository
                .saveAndFlush(Conversation.builder().userId(user.getId()).title("Document RAG Session").build());

        Attachment attachment = Attachment.builder().conversationId(conv.getId()).userId(user.getId())
                .fileName("annual_report_2026.pdf").fileType("application/pdf").fileSize(1048576L)
                .status(Attachment.Status.PROCESSING).chunkCount(0).build();

        Attachment saved = attachmentRepository.saveAndFlush(attachment);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(Attachment.Status.PROCESSING);

        // Update status to READY with chunk count
        saved.setStatus(Attachment.Status.READY);
        saved.setChunkCount(42);
        attachmentRepository.saveAndFlush(saved);

        List<Attachment> attachments = attachmentRepository.findByConversationIdOrderByCreatedAtDesc(conv.getId());
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getStatus()).isEqualTo(Attachment.Status.READY);
        assertThat(attachments.get(0).getChunkCount()).isEqualTo(42);
    }

    @Test
    @DisplayName("UserMemory: Semantic fact persistence per user")
    void testUserMemoryPersistence() {
        User user = userRepository
                .saveAndFlush(User.builder().email("eve@localmind.dev").passwordHash("hashed").build());

        UserMemory memory = UserMemory.builder().userId(user.getId())
                .memoryFact("Prefers dark mode and concise responses").build();

        userMemoryRepository.saveAndFlush(memory);

        List<UserMemory> memories = userMemoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(memories).hasSize(1);
        assertThat(memories.get(0).getMemoryFact()).contains("dark mode");
    }
}
