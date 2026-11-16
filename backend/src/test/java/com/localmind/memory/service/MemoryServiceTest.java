package com.localmind.memory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localmind.common.ai.VectorFilters;
import com.localmind.common.exception.ResourceNotFoundException;
import com.localmind.memory.dto.UpdateMemoryRequest;
import com.localmind.memory.entity.UserMemory;
import com.localmind.memory.repository.UserMemoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

class MemoryServiceTest {

    private final UserMemoryRepository repository = mock(UserMemoryRepository.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final MemoryService service = new MemoryService(repository, vectorStore);
    private final UserMemory memory = UserMemory.builder().id("m1").userId("u1").memoryFact("Likes tea").pinned(false)
            .build();

    @Test
    @DisplayName("updateMemory() should change only the pinned flag of the caller's memory")
    void pinsOwnMemory() {
        when(repository.findByIdAndUserId("m1", "u1")).thenReturn(Optional.of(memory));
        when(repository.save(memory)).thenReturn(memory);

        assertEquals(true, service.updateMemory("m1", "u1", new UpdateMemoryRequest(true)).pinned());
    }

    @Test
    @DisplayName("deleteMemory() should delete the row and the memory's own vector")
    void deletesRowAndVector() {
        when(repository.findByIdAndUserId("m1", "u1")).thenReturn(Optional.of(memory));

        service.deleteMemory("m1", "u1");

        verify(repository).delete(memory);
        // Without an active transaction the after-commit hook runs immediately.
        verify(vectorStore).delete(any(Filter.Expression.class));
        assertEquals(VectorFilters.userMemory("u1", "m1").toString(), capturedFilter().toString());
    }

    @Test
    @DisplayName("Another user's memory should be reported as not found and left untouched")
    void otherUsersMemoryIsNotFound() {
        when(repository.findByIdAndUserId("m1", "intruder")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteMemory("m1", "intruder"));

        verify(repository, never()).delete(any());
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    private Filter.Expression capturedFilter() {
        org.mockito.ArgumentCaptor<Filter.Expression> captor = org.mockito.ArgumentCaptor
                .forClass(Filter.Expression.class);
        verify(vectorStore).delete(captor.capture());
        return captor.getValue();
    }
}
