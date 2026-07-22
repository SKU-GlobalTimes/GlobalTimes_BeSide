package com.example.globalTimes_be.externalApi.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;

public record CollectionBatchStats(
        int receivedCount,
        int invalidCount,
        int duplicateCount,
        int savedCount,
        Instant oldestPublishedAt,
        Instant latestPublishedAt
) {

    public static CollectionBatchStats from(
            int receivedCount,
            int invalidCount,
            int duplicateCount,
            int savedCount,
            Collection<Instant> publishedAtValues
    ) {
        Instant oldest = publishedAtValues.stream().min(Comparator.naturalOrder()).orElse(null);
        Instant latest = publishedAtValues.stream().max(Comparator.naturalOrder()).orElse(null);
        return new CollectionBatchStats(
                receivedCount, invalidCount, duplicateCount, savedCount, oldest, latest);
    }

    public Long freshnessSeconds(Instant observedAt) {
        return latestPublishedAt == null ? null : Duration.between(latestPublishedAt, observedAt).getSeconds();
    }
}
