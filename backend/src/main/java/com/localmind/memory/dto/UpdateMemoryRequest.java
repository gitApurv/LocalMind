package com.localmind.memory.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body of {@code PATCH /api/memories/{id}}: the new pin state.
 *
 * @param pinned
 *            pinned memories are always included in the system prompt
 */
public record UpdateMemoryRequest(@NotNull(message = "Pinned flag is required") Boolean pinned) {
}
