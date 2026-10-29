package com.moveinsync.evfleet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PageResponse<T>(
    @Schema(description = "Page elements") List<T> content,
    @Schema(description = "Current page index (0-indexed)") int pageNumber,
    @Schema(description = "Page size") int pageSize,
    @Schema(description = "Total elements across all pages") long totalElements,
    @Schema(description = "Total pages") int totalPages
) {}
