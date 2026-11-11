package com.localmind.conversation.repository;

import com.localmind.conversation.entity.Conversation;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background updates (end of a chat turn, auto-title, rolling summary) use the targeted {@code UPDATE} queries below
 * instead of saving a whole entity, so they never overwrite a concurrent rename, pin or model change.
 */
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId);

    boolean existsByIdAndUserId(String id, String userId);

    @Transactional
    @Modifying
    @Query("UPDATE Conversation c SET c.updatedAt = :now WHERE c.id = :id")
    int touch(@Param("id") String id, @Param("now") Instant now);

    /** Sets the title only while it is still empty or the placeholder; a title chosen by the user is kept. */
    @Transactional
    @Modifying
    @Query("""
            UPDATE Conversation c SET c.title = :title, c.updatedAt = :now
            WHERE c.id = :id AND (c.title IS NULL OR TRIM(c.title) = '' OR LOWER(c.title) = LOWER(:placeholder))""")
    int replacePlaceholderTitle(@Param("id") String id, @Param("title") String title,
            @Param("placeholder") String placeholder, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("UPDATE Conversation c SET c.summary = :summary, c.summarizedThrough = :through WHERE c.id = :id")
    int updateSummary(@Param("id") String id, @Param("summary") String summary, @Param("through") Instant through);
}
