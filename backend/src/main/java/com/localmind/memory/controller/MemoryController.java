package com.localmind.memory.controller;

import com.localmind.common.security.CurrentUserId;
import com.localmind.memory.dto.MemoryResponse;
import com.localmind.memory.dto.UpdateMemoryRequest;
import com.localmind.memory.service.MemoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    @GetMapping
    public List<MemoryResponse> listMemories(@CurrentUserId String userId) {
        return memoryService.listMemories(userId);
    }

    @PatchMapping("/{id}")
    public MemoryResponse updateMemory(@CurrentUserId String userId, @PathVariable("id") String id,
            @Valid @RequestBody UpdateMemoryRequest request) {
        return memoryService.updateMemory(id, userId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMemory(@CurrentUserId String userId, @PathVariable("id") String id) {
        memoryService.deleteMemory(id, userId);
    }
}
