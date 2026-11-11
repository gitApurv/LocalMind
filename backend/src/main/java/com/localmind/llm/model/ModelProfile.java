package com.localmind.llm.model;

/**
 * Context-window budget and tool-calling fallback of a local model, all sizes in tokens.
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
 *            fallback used when Ollama does not report the model's capabilities
 */
public record ModelProfile(String modelName, int numCtx, int outputReserve, int ragBudget, int historyBudget,
        boolean toolSupported) {

    public static ModelProfile defaultProfile(String modelName) {
        return generic(modelName, false);
    }

    /** Budget used for models without a dedicated profile. */
    public static ModelProfile generic(String modelName, boolean toolSupported) {
        return new ModelProfile(modelName, 8192, 1600, 2000, 3400, toolSupported);
    }
}
