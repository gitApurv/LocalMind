package com.localmind.tools.service;

import com.localmind.common.ai.VectorFilters;
import com.localmind.common.ai.VectorMetadata;
import com.localmind.common.transaction.TransactionHooks;
import com.localmind.memory.entity.UserMemory;
import com.localmind.memory.repository.UserMemoryRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a user memory row and its embedding together for {@link UserMemoryTools#saveUserMemory}. It is a separate bean
 * because the tool method must catch every exception, which a {@code @Transactional} method on the tool itself could
 * not do for commit failures (and a self-call would bypass the transaction proxy).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryWriteService {

    private final UserMemoryRepository userMemoryRepository;
    private final VectorStore vectorStore;

    @Transactional
    public boolean saveIfAbsent(String userId, String fact) {
        if (userMemoryRepository.existsByUserIdAndMemoryFact(userId, fact)) {
            return false;
        }
        String memoryId = UUID.randomUUID().toString();
        userMemoryRepository.save(UserMemory.builder().id(memoryId).userId(userId).memoryFact(fact).build());
        TransactionHooks.onRollback(() -> vectorStore.delete(VectorFilters.userMemory(userId, memoryId)));
        Map<String, Object> metadata = Map.of(VectorMetadata.USER_ID, userId, VectorMetadata.TYPE,
                VectorMetadata.TYPE_USER_MEMORY, VectorMetadata.MEMORY_ID, memoryId);
        vectorStore.add(List.of(new Document(fact, metadata)));
        log.info("Saved user memory '{}' for user '{}'", memoryId, userId);
        return true;
    }
}
