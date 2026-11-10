package com.localmind.common.ai;

/**
 * Payload keys and document types stored alongside vectors in Qdrant.
 */
public final class VectorMetadata {

    public static final String USER_ID = "userId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String ATTACHMENT_ID = "attachmentId";
    public static final String MEMORY_ID = "memoryId";
    public static final String FILE_NAME = "fileName";
    public static final String CHUNK_INDEX = "chunkIndex";
    public static final String TYPE = "type";

    public static final String TYPE_DOC_CHUNK = "DOC_CHUNK";
    public static final String TYPE_USER_MEMORY = "USER_MEMORY";

    private VectorMetadata() {
    }
}
