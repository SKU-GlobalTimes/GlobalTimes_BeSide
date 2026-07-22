# Collection Freshness And Coverage Baseline

## Purpose

This baseline separates two causes of weak Perspectives results:

- the related article was not collected from the configured upstream sources
- the article exists locally, but query translation, tokenization, or matching did not find it

It adds measurement to the existing collection path. It does not translate or backfill articles and does not change collection frequency.

## Known Boundary

The backend polls News API headlines every four hours, News API Everything once a day, and RSS every six hours when `news-fetch.enabled=true`. These schedules control when this application asks for data; they do not guarantee when an upstream source publishes, refreshes, or exposes an article.

Real upstream calls remain disabled by default. Unit tests use News API DTO and RSS XML fixtures, so CI does not consume API quota or depend on external source availability.

## Batch Statistics

Each processed News API or RSS batch emits one structured collection log containing:

| Field | Meaning |
| --- | --- |
| `provider` | `news-api` or `rss` |
| `source` / `sourceCount` | RSS source name or distinct News API source count |
| `country`, `language`, `category` | configured collection dimensions |
| `response` | articles or RSS items received |
| `invalid` | items rejected before duplicate checks |
| `duplicate` | duplicate URL in the response or already present in the DB |
| `saved` | new articles passed to persistence |
| `oldestPublishedAt`, `latestPublishedAt` | valid publication-time range in the response |
| `freshnessSeconds` | observation time minus the latest valid publication time |

`freshnessSeconds` is a source-response observation, not API latency. A negative value is retained because it exposes a future timestamp or clock/timezone anomaly instead of hiding it.

## Database Snapshot

Run [`sql/collection-coverage-snapshot.sql`](sql/collection-coverage-snapshot.sql) against the existing MySQL database. It reports:

- total article count and publication-time range
- source-level counts and latest article age
- country, language, and category distribution
- country/language counts collected during the last 24 hours, 7 days, and 30 days

The database stores `published_at` as a timezone-less `DATETIME`. The snapshot consistently compares it with `UTC_TIMESTAMP()` according to the application's existing UTC convention. Per-batch logs use the upstream offset as an `Instant` and are the more reliable freshness observation.

## Interpretation

| Observation | Primary follow-up |
| --- | --- |
| Relevant article is absent from the DB | inspect source coverage, feed selection, and collection timing |
| Relevant article exists but matching returns zero | inspect query language, translation, tokenization, and matching |
| One country/language has little or stale data | treat cross-country result quality as coverage-limited |
| Batch response is fresh but `saved=0` with high duplicates | current poll may be healthy without new upstream content |
| Batch response itself is stale | inspect upstream refresh behavior before changing ranking |

Counts alone do not prove that a specific world event is covered. A future matching-quality snapshot should pair this distribution with representative article IDs and returned titles.

## Fixture Verification

News API and RSS fixtures each process four inputs: one new item, one same-response duplicate, one DB-existing item, and one invalid item. The expected result is:

- response: 4
- invalid: 1
- duplicate: 2
- saved: 1
- valid publication range: `2026-07-15T09:00:00Z` through `2026-07-15T11:00:00Z`

`CollectionBatchStats` also verifies that freshness is calculated from the latest valid publication time and remains absent when a batch has no valid publication time.

All 71 Gradle tests passed with MySQL and Redis Testcontainers. No compose database was running during this measurement, so this issue does not claim a current production-like article distribution. The SQL is retained as the repeatable read-only snapshot for a populated local database.

## Deferred Decisions

Ingestion-time English normalization is not introduced by this issue. Translating every article would add external-call cost, ingestion latency, backfill work, failure handling, and schema decisions before the actual coverage gap is measured. If snapshots later show that collected non-English articles exist but are repeatedly missed, selective asynchronous title/keyword normalization can be evaluated with that evidence.
