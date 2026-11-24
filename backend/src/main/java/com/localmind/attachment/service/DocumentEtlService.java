package com.localmind.attachment.service;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.attachment.storage.FileStorageService;
import com.localmind.common.ai.VectorFilters;
import com.localmind.common.ai.VectorMetadata;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Extracts text from an uploaded attachment (Apache Tika), splits it into chunks and indexes them in the vector store
 * with tenant metadata. Runs asynchronously after the upload commits and records {@code READY} or {@code FAILED} on the
 * attachment; it also removes indexed chunks when attachments are deleted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentEtlService {

    private static final int MAX_EXTRACTED_CHARS = 5_000_000;
    private static final String PROCESSING_FAILED_MESSAGE = "The document could not be processed. Check that the "
            + "file is a valid, unencrypted document and upload it again.";

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final VectorStore vectorStore;
    private final Tika tika = newTika();
    private final TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder().withChunkSize(500)
            .withMinChunkSizeChars(50).withMinChunkLengthToEmbed(10).withMaxNumChunks(5000).withKeepSeparator(true)
            .build();

    @Async
    public void processAttachment(String attachmentId) {
        attachmentRepository.findById(attachmentId).ifPresentOrElse(this::ingest,
                () -> log.warn("Cannot process attachment: {} not found", attachmentId));
    }

    public void deleteIndexedChunks(String userId, String conversationId) {
        try {
            vectorStore.delete(VectorFilters.documentChunks(userId, conversationId));
        } catch (Exception e) {
            log.warn("Failed to delete indexed chunks of conversation '{}': {}", conversationId, e.getMessage());
        }
    }

    private void ingest(Attachment attachment) {
        try {
            int chunkCount = index(attachment);
            recordOutcome(attachment, Attachment.Status.READY, chunkCount, null);
            log.info("ETL successfully completed for attachment '{}'", attachment.getId());
        } catch (Exception e) {
            log.error("Document ETL failed for attachment '{}': {}", attachment.getId(), e.getMessage(), e);
            deleteAttachmentChunks(attachment);
            recordFailure(attachment);
        }
    }

    private void recordFailure(Attachment attachment) {
        try {
            recordOutcome(attachment, Attachment.Status.FAILED, 0, PROCESSING_FAILED_MESSAGE);
        } catch (RuntimeException e) {
            log.error("Could not mark attachment '{}' as FAILED; it stays PROCESSING until the next restart",
                    attachment.getId(), e);
        }
    }

    private int index(Attachment attachment) throws Exception {
        log.info("Starting document ingestion ETL for attachment '{}' ({})", attachment.getFileName(),
                attachment.getId());
        String text = extractText(attachment);
        if (text == null || text.isBlank()) {
            log.info("Attachment '{}' yielded empty or whitespace-only text", attachment.getFileName());
            return 0;
        }
        List<Document> chunks = split(attachment, text);
        log.info("Indexing {} vector chunks for attachment '{}' in Qdrant", chunks.size(), attachment.getFileName());
        vectorStore.add(chunks);
        return chunks.size();
    }

    private void recordOutcome(Attachment attachment, Attachment.Status status, int chunkCount, String errorMessage) {
        int updated = attachmentRepository.updateOutcome(attachment.getId(), status, chunkCount, errorMessage);
        if (updated > 0) {
            return;
        }
        log.info("Attachment '{}' was deleted during processing; discarding its chunks", attachment.getId());
        deleteAttachmentChunks(attachment);
    }

    private void deleteAttachmentChunks(Attachment attachment) {
        try {
            vectorStore.delete(VectorFilters.attachmentChunks(attachment.getUserId(), attachment.getId()));
        } catch (Exception e) {
            log.warn("Failed to delete indexed chunks of attachment '{}': {}", attachment.getId(), e.getMessage());
        }
    }

    private String extractText(Attachment attachment) throws Exception {
        Resource resource = fileStorageService.loadAsResource(attachment.getUserId(), attachment.getId(),
                attachment.getFileName());
        try (InputStream inputStream = resource.getInputStream()) {
            return tika.parseToString(inputStream);
        }
    }

    private List<Document> split(Attachment attachment, String text) {
        Map<String, Object> tenantMetadata = tenantMetadata(attachment);
        Document document = new Document(text, new HashMap<>(tenantMetadata));
        List<Document> chunks = tokenTextSplitter.split(document);
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = chunks.get(i).getMetadata();
            metadata.putAll(tenantMetadata);
            metadata.put(VectorMetadata.CHUNK_INDEX, i);
        }
        return chunks;
    }

    private static Tika newTika() {
        Tika tika = new Tika();
        tika.setMaxStringLength(MAX_EXTRACTED_CHARS);
        return tika;
    }

    private static Map<String, Object> tenantMetadata(Attachment attachment) {
        return Map.of(VectorMetadata.USER_ID, attachment.getUserId(), VectorMetadata.CONVERSATION_ID,
                attachment.getConversationId(), VectorMetadata.ATTACHMENT_ID, attachment.getId(),
                VectorMetadata.FILE_NAME, attachment.getFileName(), VectorMetadata.TYPE, VectorMetadata.TYPE_DOC_CHUNK);
    }
}
