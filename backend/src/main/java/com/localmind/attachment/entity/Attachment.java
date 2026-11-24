package com.localmind.attachment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for table {@code attachments}: one document uploaded to a conversation, with its ingestion status. The
 * file itself lives in file storage and its text chunks in the vector store.
 */
@Entity
@Table(name = "attachments", indexes = {
        @Index(name = "idx_attachments_conversation_created", columnList = "conversation_id, created_at")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment {

    public enum Status {
        PROCESSING, READY, FAILED
    }

    public static final String UNNAMED_FILE_NAME = "unnamed";

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "conversation_id", length = 36, nullable = false)
    private String conversationId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.PROCESSING;

    @Builder.Default
    @Column(name = "chunk_count")
    private Integer chunkCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
