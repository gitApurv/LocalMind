package com.localmind.llm.dto;

import java.util.List;

/**
 * Response body of {@code GET /api/models}: the installed chat models and the default model.
 *
 * @param defaultModel
 *            installed model that the configured default resolves to; the first installed model when that one is
 *            missing, or the configured name when nothing is installed
 */
public record ModelResponse(List<ModelInfoResponse> models, String defaultModel) {
}
