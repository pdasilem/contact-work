package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.project.asset.ProjectAssetType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectAssetResponse(
        UUID id,
        ProjectAssetType type,
        String originalFilename,
        String contentType,
        long sizeBytes,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
