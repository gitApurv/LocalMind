package com.localmind.memory.service;

import com.localmind.common.ai.VectorFilters;
import com.localmind.common.transaction.TransactionHooks;
import com.localmind.memory.dto.MemoryResponse;
import com.localmind.memory.dto.UpdateMemoryRequest;
import com.localmind.memory.entity.UserMemory;
import com.localmind.memory.repository.UserMemoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists, pins and deletes the long-term memories the model saved about a user, so users can review what it remembers.
 * Deleting a memory also removes its embedding from the vector store once the deletion commits.
 */
@Service
@RequiredArgsConstructor
public class MemoryService {

    static final String ACCESS_DENIED_MESSAGE = "Access denied: Memory does not belong to user";

    private final UserMemoryRepository userMemoryRepository;
    private final VectorStore vectorStore;

    @Transactional(readOnly = true)
    public List<MemoryResponse> listMemories(String userId) {
        return userMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(MemoryResponse::from)
                .toList();
    }

    @Transactional
    public MemoryResponse updateMemory(String memoryId, String userId, UpdateMemoryRequest request) {
        UserMemory memory = findMemory(memoryId, userId);
        memory.setPinned(request.pinned());
        UserMemory saved = userMemoryRepository.save(memory);
        return MemoryResponse.from(saved);
    }

    @Transactional
    public void deleteMemory(String memoryId, String userId) {
        UserMemory memory = findMemory(memoryId, userId);
        userMemoryRepository.delete(memory);
        TransactionHooks.afterCommit(() -> vectorStore.delete(VectorFilters.userMemory(userId, memoryId)));
    }

    private UserMemory findMemory(String memoryId, String userId) {
        return userMemoryRepository.findById(memoryId).filter(memory -> memory.getUserId().equals(userId))
                .orElseThrow(() -> new AccessDeniedException(ACCESS_DENIED_MESSAGE));
    }
}
