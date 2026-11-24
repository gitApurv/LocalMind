package com.localmind.memory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localmind.common.ai.VectorFilters;
import com.localmind.memory.dto.UpdateMemoryRequest;
import com.localmind.memory.entity.UserMemory;
import com.localmind.memory.repository.UserMemoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit tests for {@link MemoryService}: pinning, deleting the row together with its vector, and denying another user's
 * or an unknown memory so ids cannot be probed.
 */
class MemoryServiceTest {

    private final UserMemoryRepository repository = mock(UserMemoryRepository.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final MemoryService service = new MemoryService(repository, vectorStore);
    private final UserMemory memory = UserMemory.builder().id("m1").userId("u1").memoryFact("Likes tea").pinned(false)
            .build();

    @Test
    @DisplayName("updateMemory() should change only the pinned flag of the caller's memory")
    void pinsOwnMemory() {
        when(repository.findById("m1")).thenReturn(Optional.of(memory));
        when(repository.save(memory)).thenReturn(memory);

        assertEquals(true, service.updateMemory("m1", "u1", new UpdateMemoryRequest(true)).pinned());
    }

    @Test
    @DisplayName("deleteMemory() should delete the row and the memory's own vector")
    void deletesRowAndVector() {
        when(repository.findById("m1")).thenReturn(Optional.of(memory));

        service.deleteMemory("m1", "u1");

        verify(repository).delete(memory);
        verify(vectorStore).delete(any(Filter.Expression.class));
        assertEquals(VectorFilters.userMemory("u1", "m1").toString(), capturedFilter().toString());
    }

    @Test
    @DisplayName("Another user's memory should be rejected with 403 and left untouched")
    void otherUsersMemoryIsForbidden() {
        when(repository.findById("m1")).thenReturn(Optional.of(memory));

        assertThrows(AccessDeniedException.class, () -> service.deleteMemory("m1", "intruder"));
        assertThrows(AccessDeniedException.class,
                () -> service.updateMemory("m1", "intruder", new UpdateMemoryRequest(true)));

        verify(repository, never()).delete(any());
        verify(repository, never()).save(any());
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    @DisplayName("An unknown memory id is denied like another user's memory, so ids cannot be probed")
    void unknownMemoryIsDenied() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.deleteMemory("missing", "u1"));

        verify(repository, never()).delete(any());
    }

    private Filter.Expression capturedFilter() {
        org.mockito.ArgumentCaptor<Filter.Expression> captor = org.mockito.ArgumentCaptor
                .forClass(Filter.Expression.class);
        verify(vectorStore).delete(captor.capture());
        return captor.getValue();
    }
}
