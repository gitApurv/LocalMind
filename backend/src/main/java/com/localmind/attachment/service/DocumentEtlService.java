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
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Extracts text from an uploaded attachment (Apache Tika), splits it into chunks and indexes them in the vector store
 * with tenant metadata.
 */
@Slf4j
@Service
public class DocumentEtlService {

    /** Guards against decompression bombs. */
    private static final int MAX_EXTRACTED_CHARS = 5_000_000;
    private static final String PROCESSING_FAILED_MESSAGE = "The document could not be processed. Check that the file is a "
            + "valid, unencrypted document and upload it again.";

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final VectorStore vectorStore;
    private final Tika tika;
    private final TokenTextSplitter tokenTextSplitter;

    public DocumentEtlService(AttachmentRepository attachmentRepository, FileStorageService fileStorageService,
            VectorStore vectorStore) {
        this.attachmentRepository = attachmentRepository;
        this.fileStorageService = fileStorageService;
        this.vectorStore = vectorStore;
        this.tika = new Tika();
        this.tika.setMaxStringLength(MAX_EXTRACTED_CHARS);
        this.tokenTextSplitter = TokenTextSplitter.builder().withChunkSize(500).withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(10).withMaxNumChunks(5000).withKeepSeparator(true).build();
    }

    @Async
    public void processAttachment(String attachmentId) {
        attachmentRepository.findById(attachmentId).ifPresentOrElse(this::ingest,
                () -> log.warn("Cannot process attachment: {} not found", attachmentId));
    }

    /**
     * Removes every indexed chunk of a conversation from the vector store. Failures are logged, not thrown: this runs
     * after the database deletion has already committed.
     */
    public void deleteIndexedChunks(String userId, String conversationId) {
        try {
            vectorStore.delete(VectorFilters.documentChunks(userId, conversationId));
        } catch (Exception e) {
            log.warn("Failed to delete indexed chunks of conversation '{}': {}", conversationId, e.getMessage());
        }
    }

    private void ingest(Attachment attachment) {
        try {
            log.info("Starting document ingestion ETL for attachment '{}' ({})", attachment.getFileName(),
                    attachment.getId());
            String text = extractText(attachment);
            int chunkCount = 0;
            if (text == null || text.isBlank()) {
                log.info("Attachment '{}' yielded empty or whitespace-only text", attachment.getFileName());
            } else {
                List<Document> chunks = split(attachment, text);
                log.info("Indexing {} vector chunks for attachment '{}' in Qdrant", chunks.size(),
                        attachment.getFileName());
                vectorStore.add(chunks);
                chunkCount = chunks.size();
            }
            attachment.markReady(chunkCount);
            saveUnlessDeleted(attachment);
            log.info("ETL successfully completed for attachment '{}'", attachment.getId());
        } catch (Exception e) {
            log.error("Document ETL failed for attachment '{}': {}", attachment.getId(), e.getMessage(), e);
            attachment.markFailed(PROCESSING_FAILED_MESSAGE);
            saveUnlessDeleted(attachment);
        }
    }

    /**
     * The conversation may have been deleted while the document was processed; saving would then resurrect the row, so
     * its chunks are removed instead.
     */
    private void saveUnlessDeleted(Attachment attachment) {
        if (attachmentRepository.existsById(attachment.getId())) {
            attachmentRepository.save(attachment);
            return;
        }
        log.info("Attachment '{}' was deleted during processing; discarding its chunks", attachment.getId());
        deleteIndexedChunks(attachment.getUserId(), attachment.getConversationId());
    }

    private String extractText(Attachment attachment) throws Exception {
        try (InputStream inputStream = fileStorageService
                .loadAsResource(attachment.getUserId(), attachment.getId(), attachment.getFileName())
                .getInputStream()) {
            return tika.parseToString(inputStream);
        }
    }

    /**
     * Every chunk carries tenancy metadata so searches can be filtered per user.
     */
    private List<Document> split(Attachment attachment, String text) {
        Map<String, Object> tenantMetadata = tenantMetadata(attachment);
        List<Document> chunks = tokenTextSplitter.split(new Document(text, new HashMap<>(tenantMetadata)));
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = chunks.get(i).getMetadata();
            metadata.putAll(tenantMetadata);
            metadata.put(VectorMetadata.CHUNK_INDEX, i);
        }
        return chunks;
    }

    private static Map<String, Object> tenantMetadata(Attachment attachment) {
        return Map.of(VectorMetadata.USER_ID, attachment.getUserId(), VectorMetadata.CONVERSATION_ID,
                attachment.getConversationId(), VectorMetadata.ATTACHMENT_ID, attachment.getId(),
                VectorMetadata.FILE_NAME, attachment.getFileName(), VectorMetadata.TYPE, VectorMetadata.TYPE_DOC_CHUNK);
    }
}
