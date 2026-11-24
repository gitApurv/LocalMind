package com.localmind.attachment.controller;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.attachment.service.DocumentEtlService;
import com.localmind.attachment.storage.FileStorageService;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.support.OfflineAiStack;
import com.localmind.user.entity.User;
import com.localmind.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc tests for {@link AttachmentController}: upload with 202, empty and disallowed files with 400, listing,
 * download headers, and 403s for cross-user, cross-conversation and unknown attachments. Boots the full context on the
 * test profile (H2). {@link DocumentEtlService} is mocked so an upload starts no real ingestion against Ollama or
 * Qdrant; the upload test checks that ingestion is triggered for the stored attachment.
 */
@SpringBootTest
@OfflineAiStack
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @MockitoBean
    private DocumentEtlService documentEtlService;

    private User userA;
    private User userB;
    private Conversation convA;

    @BeforeEach
    void setUp() {
        attachmentRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(User.builder().id(UUID.randomUUID().toString()).email("att_a@localmind.ai")
                .passwordHash("hashedA").fullName("User A").build());

        userB = userRepository.save(User.builder().id(UUID.randomUUID().toString()).email("att_b@localmind.ai")
                .passwordHash("hashedB").fullName("User B").build());

        convA = conversationRepository
                .save(Conversation.builder().userId(userA.getId()).title("User A's Conversation").build());
    }

    @Test
    @DisplayName("POST /api/conversations/{id}/attachments should accept valid file, return 202 and start ingestion")
    void uploadAttachmentSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "This is test content for attachment ingestion.".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/conversations/" + convA.getId() + "/attachments").file(file)
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.attachmentId").isNotEmpty())
                .andExpect(jsonPath("$.status", is("PROCESSING"))).andExpect(jsonPath("$.fileName", is("notes.txt")));

        List<Attachment> stored = attachmentRepository.findByConversationIdOrderByCreatedAtDesc(convA.getId());
        assertEquals(1, stored.size());
        verify(documentEtlService).processAttachment(stored.getFirst().getId());
    }

    @Test
    @DisplayName("POST /api/conversations/{id}/attachments with empty file should return 400 Bad Request")
    void uploadEmptyFileFails() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/conversations/" + convA.getId() + "/attachments").file(emptyFile)
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/conversations/{id}/attachments to another user's conversation should return 403 Forbidden")
    void uploadCrossTenantForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "Cross tenant upload attempt".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/conversations/" + convA.getId() + "/attachments").file(file)
                .with(jwt().jwt(jwt -> jwt.subject(userB.getId()).claim("email", userB.getEmail()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/conversations/{id}/attachments should list attachments with status")
    void listAttachmentsSuccess() throws Exception {
        attachmentRepository.save(Attachment.builder().conversationId(convA.getId()).userId(userA.getId())
                .fileName("first.pdf").fileType("application/pdf").fileSize(1024L).status(Attachment.Status.READY)
                .chunkCount(5).build());

        mockMvc.perform(get("/api/conversations/" + convA.getId() + "/attachments")
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fileName", is("first.pdf"))).andExpect(jsonPath("$[0].status", is("READY")))
                .andExpect(jsonPath("$[0].chunkCount", is(5)));
    }

    @Test
    @DisplayName("GET /api/conversations/{id}/attachments/{attId}/download should stream binary file")
    void downloadAttachmentSuccess() throws Exception {
        String attachmentId = UUID.randomUUID().toString();
        String fileContent = "Downloadable test binary content";
        fileStorageService.store(new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)), userA.getId(),
                attachmentId, "download.txt");

        attachmentRepository.save(Attachment.builder().id(attachmentId).conversationId(convA.getId())
                .userId(userA.getId()).fileName("download.txt").fileType("text/html")
                .fileSize((long) fileContent.length()).status(Attachment.Status.READY).chunkCount(1).build());

        mockMvc.perform(get("/api/conversations/" + convA.getId() + "/attachments/" + attachmentId + "/download")
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("download.txt")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/plain")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string(fileContent));
    }

    @Test
    @DisplayName("IDOR prevention: User B cannot download User A's attachment")
    void idorDownloadForbidden() throws Exception {
        String attachmentId = UUID.randomUUID().toString();
        attachmentRepository
                .save(Attachment.builder().id(attachmentId).conversationId(convA.getId()).userId(userA.getId())
                        .fileName("private.txt").fileType("text/plain").status(Attachment.Status.READY).build());

        mockMvc.perform(get("/api/conversations/" + convA.getId() + "/attachments/" + attachmentId + "/download")
                .with(jwt().jwt(jwt -> jwt.subject(userB.getId()).claim("email", userB.getEmail()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/conversations/{id}/attachments with a disallowed extension should return 400")
    void uploadUnsupportedExtensionFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/octet-stream",
                "MZ".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/conversations/" + convA.getId() + "/attachments").file(file)
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Unsupported file format 'exe'. Allowed: [pdf, txt, docx, md]")));

        assertTrue(attachmentRepository.findByConversationIdOrderByCreatedAtDesc(convA.getId()).isEmpty());
    }

    @Test
    @DisplayName("Downloading the caller's attachment through another of the caller's conversations should return 403")
    void downloadFromOtherConversationForbidden() throws Exception {
        Conversation otherConv = conversationRepository
                .save(Conversation.builder().userId(userA.getId()).title("User A's other Conversation").build());
        String attachmentId = UUID.randomUUID().toString();
        attachmentRepository.save(Attachment.builder().id(attachmentId).conversationId(otherConv.getId())
                .userId(userA.getId()).fileName("other.txt").status(Attachment.Status.READY).build());

        mockMvc.perform(get("/api/conversations/" + convA.getId() + "/attachments/" + attachmentId + "/download")
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("You do not have access to this resource")));
    }

    @Test
    @DisplayName("Downloading another user's attachment through the caller's own conversation should return 403")
    void downloadOtherUsersAttachmentForbidden() throws Exception {
        Conversation convB = conversationRepository
                .save(Conversation.builder().userId(userB.getId()).title("User B's Conversation").build());
        String attachmentId = UUID.randomUUID().toString();
        attachmentRepository.save(Attachment.builder().id(attachmentId).conversationId(convB.getId())
                .userId(userB.getId()).fileName("secret.txt").status(Attachment.Status.READY).build());

        mockMvc.perform(get("/api/conversations/" + convA.getId() + "/attachments/" + attachmentId + "/download")
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Downloading an unknown attachment should return 403, so ids cannot be probed")
    void downloadUnknownAttachmentForbidden() throws Exception {
        mockMvc.perform(get("/api/conversations/" + convA.getId() + "/attachments/" + UUID.randomUUID() + "/download")
                .with(jwt().jwt(jwt -> jwt.subject(userA.getId()).claim("email", userA.getEmail()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("You do not have access to this resource")));
    }
}
