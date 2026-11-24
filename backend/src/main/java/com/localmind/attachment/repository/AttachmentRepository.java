package com.localmind.attachment.repository;

import com.localmind.attachment.entity.Attachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for {@link Attachment} (table {@code attachments}).
 */
public interface AttachmentRepository extends JpaRepository<Attachment, String> {

    List<Attachment> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    boolean existsByConversationIdAndStatus(String conversationId, Attachment.Status status);

    @Transactional
    @Modifying
    @Query("""
            UPDATE Attachment a SET a.status = :status, a.chunkCount = :chunkCount, a.errorMessage = :errorMessage
            WHERE a.id = :id""")
    int updateOutcome(@Param("id") String id, @Param("status") Attachment.Status status,
            @Param("chunkCount") int chunkCount, @Param("errorMessage") String errorMessage);

    @Transactional
    @Modifying
    @Query("UPDATE Attachment a SET a.status = :failed, a.errorMessage = :errorMessage WHERE a.status = :status")
    int failAllWithStatus(@Param("status") Attachment.Status status, @Param("failed") Attachment.Status failed,
            @Param("errorMessage") String errorMessage);

    @Transactional
    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}
