# 수집 freshness 및 coverage 기준선

## 한국어 학습 안내

이 문서는 RSS와 News API 수집 결과를 source, country, language, category, 수신·무효·중복·저장 개수와 발행 시각 범위로 관찰하는 기준선이다. scheduler가 정해진 주기로 실행된다는 사실만으로 각 외부 source의 기사가 최신이거나 국가별 coverage가 균형적이라고 볼 수 없다.

핵심 개념은 **source freshness**, **coverage**, **upstream 편향**, **batch 통계**, **검색 실패 원인 분리**다. Perspectives 결과가 없을 때 retrieval이 실패한 것인지 관련 기사가 아직 DB에 수집되지 않은 것인지 구분하려면 검색 계층 밖의 수집 근거가 필요하다.

fixture 검증은 통계 계산과 log field가 의도대로 동작함을 확인하지만 실제 외부 source의 갱신 품질을 증명하지 않는다. 운영에 가까운 판단을 하려면 실행 중인 DB의 read-only snapshot과 수집 시각을 별도로 기록해야 한다.

## 원본 기준선 기록

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
