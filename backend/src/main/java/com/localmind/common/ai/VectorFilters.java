package com.localmind.common.ai;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder.Op;

/** Multi-tenant filter expressions for vector-store searches. Every search must be scoped to the authenticated user. */
public final class VectorFilters {

    private VectorFilters() {
    }

    public static Filter.Expression documentChunks(String userId, String conversationId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Op user = b.eq(VectorMetadata.USER_ID, userId);
        Op conversation = b.eq(VectorMetadata.CONVERSATION_ID, conversationId);
        Op type = b.eq(VectorMetadata.TYPE, VectorMetadata.TYPE_DOC_CHUNK);
        Op ownedByConversation = b.and(user, conversation);
        return b.and(ownedByConversation, type).build();
    }

    public static Filter.Expression userMemories(String userId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Op user = b.eq(VectorMetadata.USER_ID, userId);
        Op type = b.eq(VectorMetadata.TYPE, VectorMetadata.TYPE_USER_MEMORY);
        return b.and(user, type).build();
    }

    public static Filter.Expression userMemory(String userId, String memoryId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Op user = b.eq(VectorMetadata.USER_ID, userId);
        Op memory = b.eq(VectorMetadata.MEMORY_ID, memoryId);
        Op type = b.eq(VectorMetadata.TYPE, VectorMetadata.TYPE_USER_MEMORY);
        Op ownedByUser = b.and(user, memory);
        return b.and(ownedByUser, type).build();
    }
}
