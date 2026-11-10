package com.localmind.common.ai;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Multi-tenant filter expressions for vector-store searches. Every search must be scoped to the authenticated user.
 */
public final class VectorFilters {

    private VectorFilters() {
    }

    public static Filter.Expression documentChunks(String userId, String conversationId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.and(b.and(b.eq(VectorMetadata.USER_ID, userId), b.eq(VectorMetadata.CONVERSATION_ID, conversationId)),
                b.eq(VectorMetadata.TYPE, VectorMetadata.TYPE_DOC_CHUNK)).build();
    }

    public static Filter.Expression userMemories(String userId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.and(b.eq(VectorMetadata.USER_ID, userId), b.eq(VectorMetadata.TYPE, VectorMetadata.TYPE_USER_MEMORY))
                .build();
    }

    public static Filter.Expression userMemory(String userId, String memoryId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.and(b.and(b.eq(VectorMetadata.USER_ID, userId), b.eq(VectorMetadata.MEMORY_ID, memoryId)),
                b.eq(VectorMetadata.TYPE, VectorMetadata.TYPE_USER_MEMORY)).build();
    }
}
