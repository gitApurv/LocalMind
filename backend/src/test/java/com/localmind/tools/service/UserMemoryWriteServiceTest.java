package com.localmind.tools.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.localmind.memory.entity.UserMemory;
import com.localmind.memory.repository.UserMemoryRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Unit tests for {@link UserMemoryWriteService}: saving the row and its embedding under one id, skipping duplicates,
 * propagating embedding failures, and removing the embedding on rollback but not on commit.
 */
class UserMemoryWriteServiceTest {

    private final UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final UserMemoryWriteService service = new UserMemoryWriteService(userMemoryRepository, vectorStore);

    @Test
    @DisplayName("saveIfAbsent stores the row and embeds the fact with the same memory id")
    void savesRowAndEmbedding() {
        assertTrue(service.saveIfAbsent("user-1", "Likes tea"));

        ArgumentCaptor<UserMemory> row = ArgumentCaptor.forClass(UserMemory.class);
        verify(userMemoryRepository).save(row.capture());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> docs = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(docs.capture());
        Document document = docs.getValue().getFirst();
        assertEquals("Likes tea", document.getText());
        assertEquals(row.getValue().getId(), document.getMetadata().get("memoryId"));
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    @DisplayName("saveIfAbsent skips a fact the user already saved")
    void skipsDuplicate() {
        when(userMemoryRepository.existsByUserIdAndMemoryFact("user-1", "Likes tea")).thenReturn(true);

        assertFalse(service.saveIfAbsent("user-1", "Likes tea"));

        verify(userMemoryRepository, never()).save(any());
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("A failed embedding propagates so the transaction rolls the row back; no manual delete")
    void embeddingFailurePropagates() {
        doThrow(new IllegalStateException("qdrant down")).when(vectorStore).add(anyList());

        assertThrows(IllegalStateException.class, () -> service.saveIfAbsent("user-1", "Likes tea"));

        verify(userMemoryRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("A rollback after the embedding was added deletes the embedding again")
    void rollbackDeletesEmbedding() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.saveIfAbsent("user-1", "Likes tea");
            verify(vectorStore, never()).delete(any(Filter.Expression.class));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(vectorStore).delete(any(Filter.Expression.class));
    }

    @Test
    @DisplayName("A commit keeps the embedding")
    void commitKeepsEmbedding() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.saveIfAbsent("user-1", "Likes tea");
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> {
                sync.afterCommit();
                sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            });
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }
}
