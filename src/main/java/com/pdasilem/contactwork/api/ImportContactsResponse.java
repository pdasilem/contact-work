package com.pdasilem.contactwork.api;

public record ImportContactsResponse(
        int totalRows,
        int inserted,
        int skippedExisting,
        int skippedInvalid
) {
}
