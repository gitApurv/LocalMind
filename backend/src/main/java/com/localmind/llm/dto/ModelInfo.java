package com.localmind.llm.dto;

public record ModelInfo(String name, String model, Long size, String modifiedAt, boolean supportsTools) {
}
