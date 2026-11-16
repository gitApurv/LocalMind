package com.localmind.attachment.repository;

import com.localmind.attachment.entity.Attachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AttachmentRepository extends JpaRepository<Attachment, String> {

    List<Attachment> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    boolean existsByConversationIdAndStatus(String conversationId, Attachment.Status status);

    Optional<Attachment> findByIdAndUserId(String id, String userId);

    /**
     * Records the result of document processing. A targeted update, so a row deleted while it was processed is never
     * re-inserted; returns 0 in that case.
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE Attachment a SET a.status = :status, a.chunkCount = :chunkCount, a.errorMessage = :errorMessage
            WHERE a.id = :id""")
    int updateOutcome(@Param("id") String id, @Param("status") Attachment.Status status,
            @Param("chunkCount") int chunkCount, @Param("errorMessage") String errorMessage);

    /** Marks every attachment still in {@code status} as failed; returns how many changed. */
    @Transactional
    @Modifying
    @Query("UPDATE Attachment a SET a.status = :failed, a.errorMessage = :errorMessage WHERE a.status = :status")
    int failAllWithStatus(@Param("status") Attachment.Status status, @Param("failed") Attachment.Status failed,
            @Param("errorMessage") String errorMessage);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}
