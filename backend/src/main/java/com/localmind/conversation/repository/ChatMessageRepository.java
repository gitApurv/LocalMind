package com.localmind.conversation.repository;

import com.localmind.conversation.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for {@link ChatMessage} (table {@code messages}).
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    List<ChatMessage> findByConversationIdAndIdNotOrderByCreatedAtAsc(String conversationId, String excludedId);

    @Transactional
    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}
