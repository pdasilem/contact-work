package com.pdasilem.contactwork.api;

public record SendStatusResponse(
        boolean running,
        String batchSelection,
        long newCount,
        long eligibleBatchCount,
        long sentCount,
        long sendFailedCount,
        long bouncedCount,
        long repliedCount
) {
}
