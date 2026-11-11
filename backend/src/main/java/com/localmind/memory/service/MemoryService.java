package com.localmind.memory.service;

import com.localmind.common.ai.VectorFilters;
import com.localmind.common.exception.ResourceNotFoundException;
import com.localmind.common.transaction.TransactionHooks;
import com.localmind.memory.dto.MemoryResponse;
import com.localmind.memory.dto.UpdateMemoryRequest;
import com.localmind.memory.entity.UserMemory;
import com.localmind.memory.repository.UserMemoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets users review, pin and delete the long-term memories the model saved about them.
 */
@Service
@RequiredArgsConstructor
public class MemoryService {

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
        return MemoryResponse.from(userMemoryRepository.save(memory));
    }

    /** Removes the memory and, once the deletion commits, its embedding. */
    @Transactional
    public void deleteMemory(String memoryId, String userId) {
        userMemoryRepository.delete(findMemory(memoryId, userId));
        TransactionHooks.afterCommit(() -> vectorStore.delete(VectorFilters.userMemory(userId, memoryId)));
    }

    private UserMemory findMemory(String memoryId, String userId) {
        return userMemoryRepository.findByIdAndUserId(memoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found"));
    }
}
