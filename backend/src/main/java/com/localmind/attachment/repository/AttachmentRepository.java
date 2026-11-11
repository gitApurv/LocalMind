package com.localmind.attachment.repository;

import com.localmind.attachment.entity.Attachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, String> {

    List<Attachment> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    boolean existsByConversationIdAndStatus(String conversationId, Attachment.Status status);

    Optional<Attachment> findByIdAndUserId(String id, String userId);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}
