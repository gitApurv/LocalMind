package com.localmind.common.ai;

/**
 * Result counts and similarity thresholds for vector-store searches. The automatic prompt advisors and the LLM-callable
 * tools search the same data, so they share these values: tuning one place changes what the model sees through both
 * paths.
 */
public final class VectorSearchDefaults {

    public static final int DOCUMENT_TOP_K = 4;
    public static final double DOCUMENT_SIMILARITY_THRESHOLD = 0.4;

    public static final int MEMORY_TOP_K = 5;
    public static final double MEMORY_SIMILARITY_THRESHOLD = 0.4;
    public static final double MEMORY_RECALL_SIMILARITY_THRESHOLD = 0.3;

    private VectorSearchDefaults() {
    }
}
