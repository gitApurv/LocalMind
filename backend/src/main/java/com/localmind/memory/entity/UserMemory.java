package com.localmind.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * JPA entity for table {@code user_memories}: one long-term fact the model saved about a user. Each row has an
 * embedding in the vector store under the same id; pinned memories are always added to the system prompt.
 */
@Entity
@Table(name = "user_memories", indexes = {
        @Index(name = "idx_user_memories_user_created", columnList = "user_id, created_at")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMemory {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "memory_fact", columnDefinition = "TEXT", nullable = false)
    private String memoryFact;

    @Builder.Default
    @Column(name = "pinned")
    private Boolean pinned = false;

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
