package com.localmind.llm.dto;

/** One installed chat model in the {@code /api/models} response. */
public record ModelInfoResponse(String name, String model, Long size, String modifiedAt, boolean supportsTools) {
}
