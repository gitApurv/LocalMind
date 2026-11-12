package com.localmind.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.chat.config.PromptProperties;
import com.localmind.chat.dto.ChatEvent;
import com.localmind.chat.dto.ChatRequest;
import com.localmind.chat.prompt.AutoRagAdvisor;
import com.localmind.chat.prompt.CharacterTokenCounter;
import com.localmind.chat.prompt.HistoryAdvisor;
import com.localmind.chat.prompt.HistoryTrimmer;
import com.localmind.chat.prompt.MemoryRetrievalAdvisor;
import com.localmind.chat.prompt.PromptAdvisor;
import com.localmind.chat.prompt.PromptStatsAdvisor;
import com.localmind.chat.prompt.PromptStructureAdvisor;
import com.localmind.chat.prompt.TokenCounter;
import com.localmind.chat.prompt.ToolNoteRenderer;
import com.localmind.chat.prompt.ToolSelector;
import com.localmind.common.exception.ResourceNotFoundException;
import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ChatMessageRepository;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.conversation.service.AutoTitleService;
import com.localmind.conversation.service.ConversationSummaryService;
import com.localmind.conversation.service.OwnershipGuard;
import com.localmind.llm.config.LlmProperties;
import com.localmind.llm.service.ModelProfileRegistry;
import com.localmind.memory.repository.UserMemoryRepository;
import com.localmind.support.OfflineModelCatalog;
import com.localmind.tools.config.SearxngProperties;
import com.localmind.tools.config.WebScraperProperties;
import com.localmind.tools.service.DateTimeTools;
import com.localmind.tools.service.DocumentSearchTools;
import com.localmind.tools.service.MathTools;
import com.localmind.tools.service.UserMemoryTools;
import com.localmind.tools.service.WebScraperTools;
import com.localmind.tools.service.WebSearchTools;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ChatOrchestratorServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private UserMemoryRepository userMemoryRepository;

    @Mock
    private com.localmind.user.repository.UserRepository userRepository;

    private TestChatModel chatModel;
    private OwnershipGuard ownershipGuard;
    private TestAutoTitleService autoTitleService;
    private ChatOrchestratorService chatOrchestratorService;

    private final String userId = "user-123";
    private final String conversationId = "conv-456";
    private Conversation conversation;

    static class TestChatModel implements ChatModel {
        Flux<ChatResponse> streamResponses = Flux.empty();
        Prompt lastPrompt;

        @Override
        public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
            return org.springframework.ai.ollama.api.OllamaChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("test"))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.lastPrompt = prompt;
            return streamResponses;
        }
    }

    static class TestAutoTitleService extends AutoTitleService {
        String lastConversationId;
        String lastFirstPrompt;
        String lastModelName;

        public TestAutoTitleService() {
            super(null, null, LlmProperties.defaults());
        }

        @Override
        public void generateTitleAsync(String conversationId, String firstPrompt, String modelName) {
            this.lastConversationId = conversationId;
            this.lastFirstPrompt = firstPrompt;
            this.lastModelName = modelName;
        }
    }

    static class TestVectorStore implements VectorStore {
        @Override
        public void add(List<Document> documents) {
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }
    }

    @BeforeEach
    void setUp() {
        chatModel = new TestChatModel();
        ownershipGuard = new OwnershipGuard(conversationRepository);
        autoTitleService = new TestAutoTitleService();

        TestVectorStore vectorStore = new TestVectorStore();
        DateTimeTools dateTimeTools = new DateTimeTools();
        MathTools mathTools = new MathTools();
        WebSearchTools webSearchTools = new WebSearchTools(RestClient.create(),
                new SearxngProperties("http://localhost:8081"));
        WebScraperTools webScraperTools = new WebScraperTools(new WebScraperProperties(false));
        UserMemoryTools userMemoryTools = new UserMemoryTools(userMemoryRepository, vectorStore);
        DocumentSearchTools documentSearchTools = new DocumentSearchTools(vectorStore);

        conversation = Conversation.builder().id(conversationId).userId(userId).title("New Chat").modelName("gemma3:1b")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        TokenCounter tokenCounter = new CharacterTokenCounter();
        HistoryTrimmer historyTrimmer = new HistoryTrimmer(tokenCounter, new ToolNoteRenderer(new ObjectMapper()));
        PromptProperties promptProperties = PromptProperties.defaults();
        PromptProperties ragDisabled = new PromptProperties(new PromptProperties.Rag(false), null, null, null);

        List<PromptAdvisor> advisors = List.of(new PromptStructureAdvisor(userRepository, userMemoryRepository),
                new MemoryRetrievalAdvisor(vectorStore, userMemoryRepository, tokenCounter, promptProperties),
                new AutoRagAdvisor(vectorStore, null, tokenCounter, ragDisabled),
                new HistoryAdvisor(chatMessageRepository, conversationRepository, historyTrimmer,
                        mock(ConversationSummaryService.class), promptProperties),
                new PromptStatsAdvisor(tokenCounter, promptProperties));
        ModelProfileRegistry registry = new ModelProfileRegistry();
        ToolSelector toolSelector = new ToolSelector(new OfflineModelCatalog(registry),
                mock(AttachmentRepository.class), dateTimeTools,
                mathTools, webSearchTools, webScraperTools, userMemoryTools, documentSearchTools);

        chatOrchestratorService = new ChatOrchestratorService(chatModel, chatMessageRepository, conversationRepository,
                ownershipGuard, autoTitleService, advisors, toolSelector, registry, LlmProperties.defaults(),
                tokenCounter, new ObjectMapper());
    }

    @Test
    @DisplayName("streamChat() should stream content tokens and persist user & assistant messages upon completion")
    void streamChatNormalContent() {
        when(conversationRepository.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        ChatResponse chunk1 = new ChatResponse(List.of(new Generation(new AssistantMessage("Hello! "))));
        ChatResponse chunk2 = new ChatResponse(List.of(new Generation(new AssistantMessage("How can I help you?"))));
        chatModel.streamResponses = Flux.just(chunk1, chunk2);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            return ChatMessage.builder().id("msg-" + System.nanoTime()).conversationId(msg.getConversationId())
                    .role(msg.getRole()).content(msg.getContent()).reasoningContent(msg.getReasoningContent())
                    .createdAt(Instant.now()).build();
        });

        ChatRequest request = new ChatRequest(conversationId, "Hi there", null, false, null);

        Flux<ChatEvent> eventFlux = chatOrchestratorService.streamChat(request, userId);

        StepVerifier.create(eventFlux)
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.CONTENT && "Hello! ".equals(e.content()))
                .expectNextMatches(
                        e -> e.type() == ChatEvent.EventType.CONTENT && "How can I help you?".equals(e.content()))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.DONE && e.messageId() != null).verifyComplete();

        // Verify user and assistant messages saved
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        List<ChatMessage> savedMessages = captor.getAllValues();
        assertEquals(ChatMessage.Role.USER, savedMessages.get(0).getRole());
        assertEquals("Hi there", savedMessages.get(0).getContent());
        assertNotNull(savedMessages.get(0).getTokenCount());
        assertTrue(savedMessages.get(0).getTokenCount() > 0);

        assertEquals(ChatMessage.Role.ASSISTANT, savedMessages.get(1).getRole());
        assertEquals("Hello! How can I help you?", savedMessages.get(1).getContent());
        assertNull(savedMessages.get(1).getReasoningContent());
        // The stale conversation snapshot must never be saved back; only updatedAt is bumped.
        verify(conversationRepository).touch(eq(conversationId), any(Instant.class));
        verify(conversationRepository, never()).save(any());
        assertNotNull(savedMessages.get(1).getTokenCount());
        assertTrue(savedMessages.get(1).getTokenCount() > 0);

        assertEquals(conversationId, autoTitleService.lastConversationId);
        assertEquals("Hi there", autoTitleService.lastFirstPrompt);
    }

    @Test
    @DisplayName("streamChat() should extract <think>...</think> reasoning tags into REASONING events")
    void streamChatWithReasoningTags() {
        when(conversationRepository.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        ChatResponse chunk1 = new ChatResponse(
                List.of(new Generation(new AssistantMessage("<think>Analyzing user query</think>"))));
        ChatResponse chunk2 = new ChatResponse(List.of(new Generation(new AssistantMessage("42 is the answer."))));
        chatModel.streamResponses = Flux.just(chunk1, chunk2);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            return ChatMessage.builder().id("msg-" + System.nanoTime()).conversationId(msg.getConversationId())
                    .role(msg.getRole()).content(msg.getContent()).reasoningContent(msg.getReasoningContent())
                    .createdAt(Instant.now()).build();
        });

        ChatRequest request = new ChatRequest(conversationId, "What is the answer?", null, false, null);

        Flux<ChatEvent> eventFlux = chatOrchestratorService.streamChat(request, userId);

        StepVerifier.create(eventFlux)
                .expectNextMatches(
                        e -> e.type() == ChatEvent.EventType.REASONING && "Analyzing user query".equals(e.content()))
                .expectNextMatches(
                        e -> e.type() == ChatEvent.EventType.CONTENT && "42 is the answer.".equals(e.content()))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.DONE).verifyComplete();

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        ChatMessage assistantMsg = captor.getAllValues().get(1);
        assertEquals("Analyzing user query", assistantMsg.getReasoningContent());
        assertEquals("42 is the answer.", assistantMsg.getContent());
    }

    @Test
    @DisplayName("streamChat() when ownership check fails should propagate AccessDeniedException")
    void streamChatOwnershipFailed() {
        when(conversationRepository.existsByIdAndUserId(conversationId, userId)).thenReturn(false);

        ChatRequest request = new ChatRequest(conversationId, "Hello", null, false, null);

        assertThrows(AccessDeniedException.class, () -> chatOrchestratorService.streamChat(request, userId));
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    @DisplayName("streamChat() with custom model and system prompt should use provided parameters")
    void streamChatWithCustomModelAndSystemPrompt() {
        when(conversationRepository.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        ChatResponse chunk = new ChatResponse(List.of(new Generation(new AssistantMessage("Custom model reply"))));
        chatModel.streamResponses = Flux.just(chunk);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            return ChatMessage.builder().id("msg-" + System.nanoTime()).conversationId(msg.getConversationId())
                    .role(msg.getRole()).content(msg.getContent()).reasoningContent(msg.getReasoningContent())
                    .createdAt(Instant.now()).build();
        });

        ChatRequest request = new ChatRequest(conversationId, "Hello", "llama3.2:3b", true, "You are a pirate");

        Flux<ChatEvent> eventFlux = chatOrchestratorService.streamChat(request, userId);

        StepVerifier.create(eventFlux)
                .expectNextMatches(
                        e -> e.type() == ChatEvent.EventType.CONTENT && "Custom model reply".equals(e.content()))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.DONE).verifyComplete();

        assertNotNull(chatModel.lastPrompt);
        assertEquals("llama3.2:3b", chatModel.lastPrompt.getOptions().getModel());
    }

    @Test
    @DisplayName("streamChat() with non-existent conversation should throw ResourceNotFoundException")
    void streamChatConversationNotFound() {
        when(conversationRepository.existsByIdAndUserId("nonexistent", userId)).thenReturn(true);
        when(conversationRepository.findById("nonexistent")).thenReturn(Optional.empty());

        ChatRequest request = new ChatRequest("nonexistent", "Hello", null, false, null);

        assertThrows(ResourceNotFoundException.class, () -> chatOrchestratorService.streamChat(request, userId));
    }

    @Test
    @DisplayName("streamChat() should include prior conversation history in chat prompt")
    void streamChatWithMultiTurnHistory() {
        when(conversationRepository.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        ChatMessage prevUser = ChatMessage.builder().id("prev-1").conversationId(conversationId)
                .role(ChatMessage.Role.USER).content("What is the capital of France?")
                .createdAt(Instant.now().minusSeconds(60)).build();
        ChatMessage prevAssistant = ChatMessage.builder().id("prev-2").conversationId(conversationId)
                .role(ChatMessage.Role.ASSISTANT).content("Paris is the capital of France.")
                .createdAt(Instant.now().minusSeconds(30)).build();

        // History includes prior messages
        when(chatMessageRepository.findByConversationIdAndIdNotOrderByCreatedAtAsc(eq(conversationId), any()))
                .thenReturn(List.of(prevUser, prevAssistant));

        ChatResponse chunk = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Its population is about 2.1 million."))));
        chatModel.streamResponses = Flux.just(chunk);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            return ChatMessage.builder().id("msg-" + System.nanoTime()).conversationId(msg.getConversationId())
                    .role(msg.getRole()).content(msg.getContent()).reasoningContent(msg.getReasoningContent())
                    .createdAt(Instant.now()).build();
        });

        ChatRequest request = new ChatRequest(conversationId, "What is its population?", null, false, null);

        Flux<ChatEvent> eventFlux = chatOrchestratorService.streamChat(request, userId);

        StepVerifier.create(eventFlux)
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.CONTENT && e.content().contains("population"))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.DONE).verifyComplete();

        assertNotNull(chatModel.lastPrompt);
        List<org.springframework.ai.chat.messages.Message> instructions = chatModel.lastPrompt.getInstructions();
        assertTrue(instructions.stream().anyMatch(m -> "What is the capital of France?".equals(m.getText())));
        assertTrue(instructions.stream().anyMatch(m -> "Paris is the capital of France.".equals(m.getText())));
        assertTrue(instructions.stream().anyMatch(m -> "What is its population?".equals(m.getText())));
    }

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

    @Test
    @DisplayName("MonitoredToolCallback should record resultSummary in executedToolCalls")
    void wrapToolRecordsResultSummary() {
        AtomicInteger counter = new AtomicInteger(0);
        List<Map<String, Object>> executedToolCalls = new ArrayList<>();
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.call(any(), any())).thenReturn("Search returned 3 results: result A, result B, result C");

        @SuppressWarnings("unchecked")
        reactor.core.publisher.FluxSink<com.localmind.chat.dto.ChatEvent> mockSink = mock(
                reactor.core.publisher.FluxSink.class);
        ToolCallback wrapped = new MonitoredToolCallback(delegate, mockSink, counter, executedToolCalls);
        wrapped.call("test query", null);

        assertEquals(1, executedToolCalls.size());
        assertNotNull(executedToolCalls.get(0).get("resultSummary"));
        assertTrue(executedToolCalls.get(0).get("resultSummary").toString().contains("Search returned 3 results"));
    }

    @Test
    @DisplayName("streamChat() should parse split <think> tags across stream chunks")
    void streamChatWithSplitThinkTagsEndToEnd() {
        when(conversationRepository.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        ChatResponse chunk1 = new ChatResponse(List.of(new Generation(new AssistantMessage("<th"))));
        ChatResponse chunk2 = new ChatResponse(List.of(new Generation(new AssistantMessage("ink>Pondering</thin"))));
        ChatResponse chunk3 = new ChatResponse(List.of(new Generation(new AssistantMessage("k>Done!"))));
        chatModel.streamResponses = Flux.just(chunk1, chunk2, chunk3);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            return ChatMessage.builder().id("msg-" + System.nanoTime()).conversationId(msg.getConversationId())
                    .role(msg.getRole()).content(msg.getContent()).reasoningContent(msg.getReasoningContent())
                    .createdAt(Instant.now()).build();
        });

        ChatRequest request = new ChatRequest(conversationId, "Think please", null, false, null);

        Flux<ChatEvent> eventFlux = chatOrchestratorService.streamChat(request, userId);

        StepVerifier.create(eventFlux)
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.REASONING && "Pondering".equals(e.content()))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.CONTENT && "Done!".equals(e.content()))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.DONE).verifyComplete();

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        ChatMessage assistantMsg = captor.getAllValues().get(1);
        assertEquals("Pondering", assistantMsg.getReasoningContent());
        assertEquals("Done!", assistantMsg.getContent());
    }

    @Test
    @DisplayName("MonitoredToolCallback should emit TOOL_START and TOOL_END events and break loops after 5 iterations")
    void wrapToolEmitsEventsAndBreaksLoop() {
        AtomicInteger counter = new AtomicInteger(0);
        Flux<ChatEvent> flux = Flux.create(sink -> {
            ToolCallback mockCallback = FunctionToolCallback
                    .builder("testTool", (Map<String, Object> input) -> "result").description("test tool description")
                    .inputType(Map.class).build();

            ToolCallback wrapped = new MonitoredToolCallback(mockCallback, sink, counter, null);

            // Execute 5 times (allowed)
            for (int i = 0; i < 5; i++) {
                String res = wrapped.call("{}");
                assertTrue(res.contains("result"));
            }

            // 6th execution should trigger loop protection
            String blocked = wrapped.call("{}");
            assertTrue(blocked.contains("Loop protection triggered"));

            sink.complete();
        });

        StepVerifier.create(flux)
                // 5 iterations * 2 events = 10 events (TOOL_START, TOOL_END)
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.TOOL_START && "testTool".equals(e.tool()))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.TOOL_END && "testTool".equals(e.tool()))
                .expectNextCount(8).verifyComplete();

        assertEquals(6, counter.get());
    }

    @Test
    @DisplayName("streamChat() should inject custom instructions and user memories into system prompt")
    void streamChatWithCustomInstructionsAndMemoriesEnrichment() {
        when(conversationRepository.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(com.localmind.user.entity.User.builder().id(userId).email("user@localmind.ai")
                        .customInstructions("Always use Markdown bullet points and bold headers.").build()));

        when(userMemoryRepository.findByUserIdAndPinnedTrue(userId)).thenReturn(List.of(
                com.localmind.memory.entity.UserMemory.builder().id("m1").userId(userId).memoryFact("Prefers Java 21")
                        .pinned(true).build(),
                com.localmind.memory.entity.UserMemory.builder().id("m2").userId(userId).memoryFact("Lives in Tokyo")
                        .pinned(true).build()));

        ChatResponse chunk = new ChatResponse(List.of(new Generation(new AssistantMessage("Response"))));
        chatModel.streamResponses = Flux.just(chunk);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            return ChatMessage.builder().id("msg-" + System.nanoTime()).conversationId(msg.getConversationId())
                    .role(msg.getRole()).content(msg.getContent()).reasoningContent(msg.getReasoningContent())
                    .createdAt(Instant.now()).build();
        });

        ChatRequest request = new ChatRequest(conversationId, "Hello", null, false, "Base system prompt.");

        StepVerifier.create(chatOrchestratorService.streamChat(request, userId))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.CONTENT)
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.DONE).verifyComplete();

        assertNotNull(chatModel.lastPrompt);
        String promptInstructions = chatModel.lastPrompt.getInstructions().stream()
                .map(org.springframework.ai.chat.messages.Message::getText).reduce("", (a, b) -> a + "\n" + b);

        assertTrue(promptInstructions.contains("Base system prompt."));
        assertTrue(promptInstructions.contains("User Custom Instructions:"));
        assertTrue(promptInstructions.contains("Always use Markdown bullet points and bold headers."));
        assertTrue(promptInstructions.contains("Pinned User Preferences:"));
        assertTrue(promptInstructions.contains("Prefers Java 21"));
        assertTrue(promptInstructions.contains("Lives in Tokyo"));
    }

    @Test
    @DisplayName("streamChat() with toolsEnabled=true on gemma3:1b should skip tool registration")
    void streamChatSkipsToolsForIncompatibleModel() {
        when(conversationRepository.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        ChatResponse chunk = new ChatResponse(List.of(new Generation(new AssistantMessage("Hello!"))));
        chatModel.streamResponses = Flux.just(chunk);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            return ChatMessage.builder().id("msg-" + System.nanoTime()).conversationId(msg.getConversationId())
                    .role(msg.getRole()).content(msg.getContent()).reasoningContent(msg.getReasoningContent())
                    .createdAt(Instant.now()).build();
        });

        // toolsEnabled is explicitly true, but conversation.modelName is "gemma3:1b"
        ChatRequest request = new ChatRequest(conversationId, "Calculate 2+2", null, true, null);

        StepVerifier.create(chatOrchestratorService.streamChat(request, userId))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.CONTENT)
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.DONE).verifyComplete();

        assertNotNull(chatModel.lastPrompt);
        org.springframework.ai.chat.prompt.ChatOptions options = chatModel.lastPrompt.getOptions();
        if (options instanceof org.springframework.ai.model.tool.ToolCallingChatOptions tco) {
            assertTrue(tco.getToolCallbacks() == null || tco.getToolCallbacks().isEmpty(),
                    "Tool callbacks should not be registered on gemma3:1b");
        }
    }

    @Test
    @DisplayName("streamChat() translates Ollama tool unsupported error into a user-friendly message")
    void streamChatTranslatesToolUnsupportedError() {
        when(conversationRepository.existsByIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatModel.streamResponses = Flux.error(new RuntimeException(
                "400 Bad Request from POST http://127.0.0.1:11434/api/chat: {\"error\":\"registry.ollama.ai/library/gemma3:1b does not support tools\"}"));

        ChatRequest request = new ChatRequest(conversationId, "Hello", null, false, null);

        StepVerifier.create(chatOrchestratorService.streamChat(request, userId))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.ERROR
                        && e.content().contains("does not support tool calling in Ollama"))
                .verifyComplete();
    }
}
