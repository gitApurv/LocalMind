package com.localmind.memory.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param pinned
 *            pinned memories are always included in the system prompt
 */
public record UpdateMemoryRequest(@NotNull(message = "Pinned flag is required") Boolean pinned) {
}
