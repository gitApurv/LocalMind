package com.localmind.llm.model;

/**
 * Context-window budget and tool-calling capability of a local model, all sizes in tokens.
 *
 * @param numCtx
 *            context window passed to Ollama
 * @param outputReserve
 *            tokens kept free for the answer ({@code num_predict})
 * @param ragBudget
 *            tokens available for document excerpts
 * @param historyBudget
 *            tokens available for conversation history
 * @param toolSupported
 *            whether tool calling is supported
 */
public record ModelProfile(String modelName, int numCtx, int outputReserve, int ragBudget, int historyBudget,
        boolean toolSupported) {

    public static ModelProfile fromOllama(String modelName, Integer contextLength, boolean toolSupported) {
        int ctx = (contextLength != null && contextLength > 0) ? clampContext(contextLength) : 8192;
        int reserve = Math.min(2048, ctx / 4);
        int remaining = ctx - reserve;
        int rag = Math.max(1000, (int) (remaining * 0.35));
        int history = Math.max(1000, (int) (remaining * 0.55));
        return new ModelProfile(modelName, ctx, reserve, rag, history, toolSupported);
    }

    private static int clampContext(int contextLength) {
        return Math.min(Math.max(contextLength, 4096), 32768);
    }

    /** Budget used for models without dedicated Ollama context metrics. */
    public static ModelProfile generic(String modelName, boolean toolSupported) {
        return new ModelProfile(modelName, 8192, 1600, 2000, 3400, toolSupported);
    }
}
