package com.example.globalTimes_be.externalApi.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionBatchStatsTest {

    @Test
    void freshnessSeconds_usesLatestPublicationTime() {
        CollectionBatchStats stats = CollectionBatchStats.from(
                2, 0, 0, 2,
                List.of(Instant.parse("2026-07-15T09:00:00Z"), Instant.parse("2026-07-15T11:00:00Z")));

        assertThat(stats.freshnessSeconds(Instant.parse("2026-07-15T11:05:00Z"))).isEqualTo(300L);
    }

    @Test
    void freshnessSeconds_isNullWhenBatchHasNoValidPublicationTime() {
        CollectionBatchStats stats = CollectionBatchStats.from(1, 1, 0, 0, List.of());

        assertThat(stats.oldestPublishedAt()).isNull();
        assertThat(stats.latestPublishedAt()).isNull();
        assertThat(stats.freshnessSeconds(Instant.parse("2026-07-15T11:05:00Z"))).isNull();
    }
}
