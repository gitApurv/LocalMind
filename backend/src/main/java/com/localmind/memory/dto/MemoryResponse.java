package com.localmind.memory.dto;

import com.localmind.memory.entity.UserMemory;
import java.time.Instant;

public record MemoryResponse(String id, String memoryFact, boolean pinned, Instant createdAt) {

    public static MemoryResponse from(UserMemory memory) {
        boolean pinned = Boolean.TRUE.equals(memory.getPinned());
        return new MemoryResponse(memory.getId(), memory.getMemoryFact(), pinned, memory.getCreatedAt());
    }
}
