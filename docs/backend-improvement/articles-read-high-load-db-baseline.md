# Articles read high-load DB baseline

## Purpose

This document defines a repeatable high-load baseline for article read APIs that are primarily DB-backed and do not call external AI, crawler, or translation services.
It is meant to reveal which API path should receive follow-up `EXPLAIN`, query, or index analysis.

This work does not change repository queries, DB indexes, API response shape, Redis policy, rate limiting, or async processing.

## Portfolio Summary Candidate

```text
주요 기사 조회 API에 고부하 k6 시나리오를 적용해 p95/오류율과 DB 조회 로그를 함께 분석하고, EXPLAIN 기반 인덱스 개선 후보를 도출할 기준선을 수립했습니다.
```

Shorter resume keyword version:

```text
기사 조회 API 고부하 p95/DB 병목 기준선 수립
```

## Target APIs

| API | Service log | Repository path | Main DB concern |
| --- | --- | --- | --- |
| `GET /api/articles/latest` | `[ArticlesLatest]` | `findAllByOrderByPublishedAtDesc(Pageable)` | Offset paging and `published_at DESC` ordering |
| `GET /api/articles/cursor` | `[ArticlesCursor]` | `findFirstPage`, `findByCursor` | Cursor range scan on `published_at` |
| `GET /api/articles/popular` | `[ArticlesPopular]` | `findByPublishedAtAfterOrderByViewCountDesc` | Recent-date filter plus `view_count DESC` ordering |
| `GET /api/articles/explore` | `[Explore]` | `findByExploreFilters` | Optional `country/category/date/cursor` filters plus latest ordering |
| `GET /api/news/detail` | none yet | `findById`, `findTop20ByIdNotOrderByPublishedAtDesc` | Detail lookup plus recent articles query and view-count update |

## Measurement Tool

Use `load-tests/k6/articles-read-high-load.js`.

The script separates each API into its own scenario and tags requests with:

- `api=articles_read_high_load`
- `endpoint=latest|cursor|popular|explore|detail`
- selected page/filter/id tags where useful

It also records response body size with `articles_payload_size`.

## Execution

Start local dependencies and server first.

```powershell
docker compose -f docker-compose.dev.yml up -d
.\gradlew.bat bootRun
```

Run a short smoke baseline:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLES_SIZE = "20"
$env:LATEST_VUS = "2"
$env:CURSOR_VUS = "2"
$env:POPULAR_VUS = "2"
$env:EXPLORE_VUS = "2"
$env:DETAIL_VUS = "1"
$env:LATEST_DURATION = "15s"
$env:CURSOR_DURATION = "15s"
$env:POPULAR_DURATION = "15s"
$env:EXPLORE_DURATION = "15s"
$env:DETAIL_DURATION = "15s"
$env:DETAIL_IDS = "7366,8449"
k6 run .\load-tests\k6\articles-read-high-load.js
```

Run a heavier local baseline only after the smoke run is stable:

```powershell
$env:LATEST_VUS = "10"
$env:CURSOR_VUS = "10"
$env:POPULAR_VUS = "10"
$env:EXPLORE_VUS = "10"
$env:DETAIL_VUS = "5"
$env:LATEST_DURATION = "2m"
$env:CURSOR_DURATION = "2m"
$env:POPULAR_DURATION = "2m"
$env:EXPLORE_DURATION = "2m"
$env:DETAIL_DURATION = "2m"
k6 run .\load-tests\k6\articles-read-high-load.js
```

## Result Recording Template

Record p95 and failure rate from k6 output.
Record service log `dbQueryMs` and `totalMs` for the same run window.

| Date | Environment | API | VUs | Duration | p95 | Failure rate | Log dbQueryMs signal | Candidate follow-up |
| --- | --- | --- | ---: | --- | ---: | ---: | --- | --- |
|  | local/dev | latest |  |  |  |  | `[ArticlesLatest] dbQueryMs` | Offset paging or `published_at` index review |
|  | local/dev | cursor |  |  |  |  | `[ArticlesCursor] dbQueryMs` | Cursor range scan review |
|  | local/dev | popular |  |  |  |  | `[ArticlesPopular] dbQueryMs` | `(published_at, view_count)` or ordering index review |
|  | local/dev | explore |  |  |  |  | `[Explore] dbQueryMs` | Optional filter composite index review |
|  | local/dev | detail |  |  |  |  | no dedicated detail log yet | Add detail DB log or EXPLAIN recent list |

## Initial Local Smoke Result

The first smoke run was measured on 2026-07-09 with local Docker MySQL/Redis containers and a local `bootRun` server on `http://localhost:8080`.
It used short staggered scenarios with `1` VU per API and `8s` duration per scenario.

This is a script validation and low-load smoke baseline.
It is not a capacity limit test.

| Date | Environment | API | VUs | Duration | p95 | Failure rate | Notes |
| --- | --- | --- | ---: | --- | ---: | ---: | --- |
| 2026-07-09 | local/dev | all tagged article reads | max 5 combined | staggered 8s | 96.5ms | 0.00% | 157 requests |
| 2026-07-09 | local/dev | latest | 1 | 8s | 85.38ms | 0.00% | offset page mix |
| 2026-07-09 | local/dev | cursor | 1 | 8s | 59.86ms | 0.00% | first-page cursor smoke |
| 2026-07-09 | local/dev | popular | 1 | 8s | 88.52ms | 0.00% | recent 30-day hot news query |
| 2026-07-09 | local/dev | explore | 1 | 8s | 109.86ms | 0.00% | country/category filter mix |
| 2026-07-09 | local/dev | detail | 1 | 8s | 95.21ms | 0.00% | article ids `7366,8449` |

## DB Analysis Candidates

Do not add indexes from k6 data alone.
Use k6 to pick the slow path, then run `EXPLAIN`/`EXPLAIN ANALYZE` for that repository query.

Candidate SQL shapes:

```sql
-- latest
SELECT *
FROM article
ORDER BY published_at DESC
LIMIT 20 OFFSET 0;

-- cursor
SELECT *
FROM article
WHERE published_at < :cursor
ORDER BY published_at DESC
LIMIT 21;

-- popular
SELECT *
FROM article
WHERE published_at > :from
ORDER BY view_count DESC
LIMIT 20 OFFSET 0;

-- explore
SELECT *
FROM article
WHERE (:country IS NULL OR country = :country)
  AND (:category IS NULL OR category = :category)
  AND (:dateFrom IS NULL OR published_at >= :dateFrom)
  AND (:dateTo IS NULL OR published_at <= :dateTo)
  AND (:cursor IS NULL OR published_at < :cursor)
ORDER BY published_at DESC
LIMIT 21;

-- detail recent articles
SELECT *
FROM article
WHERE article_id <> :id
ORDER BY published_at DESC
LIMIT 20;
```

Initial local index snapshot:

| Index | Columns | Notes |
| --- | --- | --- |
| `PRIMARY` | `article_id` | Detail lookup |
| `idx_article_published_at` | `published_at` | Latest/cursor/detail recent ordering |
| `idx_article_country` | `country` | Single filter |
| `idx_article_category` | `category` | Single filter |
| `idx_article_country_category_date` | `country`, `category`, `published_at` | Explore filter/order candidate |
| `ft_article_title_description` | `title`, `description` | FULLTEXT paths, not this read baseline focus |

Initial local `EXPLAIN` notes from 2026-07-09:

| API/query shape | Observed key | Rows estimate | Extra | Initial interpretation |
| --- | --- | ---: | --- | --- |
| latest | `idx_article_published_at` | 20 | `Backward index scan` | Current index fits latest ordering. |
| cursor | `idx_article_published_at` | 5084 | `Using index condition; Backward index scan` | Cursor uses published_at range; compare with deeper cursors later. |
| popular | `idx_article_published_at` | 2074 | `Using index condition; Using filesort` | If high-load p95/dbQueryMs is high, review an ordering index candidate. |
| explore `country+category` | `idx_article_country_category_date` | 415 | `Backward index scan` | Existing composite index helps this filter shape. |
| detail recent list | `idx_article_published_at` | 22 | `Using where; Backward index scan` | Recent-list query is likely acceptable at current scale. |

Potential follow-up issues:

- `[DB] articles popular 조회 EXPLAIN ANALYZE 및 인덱스 후보 판단` - completed as #176; see `articles-popular-filesort-analysis.md`
- `[DB] articles explore 필터 조회 EXPLAIN 분석 및 복합 인덱스 후보 정리`
- `[PERF] latest offset paging과 cursor paging 고부하 비교`
- `[OBS] news detail DB 조회 단계별 latency 로그 추가`

## Interpretation Rules

- A high p95 with low `dbQueryMs` suggests serialization, response size, JVM, or network overhead rather than a DB query bottleneck.
- A high p95 with high `dbQueryMs` points to a DB query, index, or sorting candidate.
- `latest` offset paging should be compared with `cursor` before changing indexes or removing the offset API.
- `popular` can be slow when the date filter and `view_count DESC` ordering cannot use a useful index together.
- `explore` may need separate analysis by filter combination; optional filters can hide different plans.
- `detail` includes a write-like view count update and a recent list lookup, so treat it separately from pure list reads.

## Current Scope Decision

This issue establishes a high-load measurement script and DB bottleneck interpretation document.
It intentionally avoids:

- adding DB indexes
- changing repository queries
- changing API response shape
- adding Redis cache or rate limiting
- adding async processing

The next step after collecting high-load data is to choose the slowest DB-backed path and open a focused EXPLAIN/index candidate issue.

## Follow-up: Popular Filesort Analysis

#176 followed the `popular` query because #174 found `Using filesort`.

Result summary:

- local data: 9,853 total article rows, 2,070 rows in the recent 30-day filter
- k6 popular-focused run: `20 -> 50 -> 100` VUs, p95 `134.87ms -> 423.79ms -> 902.51ms`, failure rate `0.00%`
- RPS plateau: `106.29/s -> 114.33/s -> 123.61/s`, so local/application saturation should be checked before DB index changes
- `EXPLAIN ANALYZE` page 0 content query: about `6.44ms`
- `EXPLAIN ANALYZE` page 5 content query: about `11.5ms`
- count query: about `0.696ms`

Decision:

- keep the current query/index for now
- treat `Using filesort` as a watch item, not an immediate DB index change trigger
- first make `[ArticlesPopular] dbQueryMs`, `totalMs`, and local resource capture reliable for the same run window
- revisit index experiments only if `popular` p95 and captured DB query time rise together

Detailed notes: `docs/backend-improvement/articles-popular-filesort-analysis.md`
