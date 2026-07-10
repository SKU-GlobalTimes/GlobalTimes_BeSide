# Articles popular filesort analysis

## Purpose

This document records the follow-up measurement for the `GET /api/articles/popular` path after #174 found `Using filesort` in the initial MySQL plan.

The goal is not to add an index immediately.
The goal is to combine k6 high-load API data with MySQL `EXPLAIN ANALYZE` and decide whether the current `Using filesort` signal is strong enough to justify a DB index/query change.

## Portfolio Summary Candidate

```text
기사 popular 조회 API의 고부하 p95와 MySQL EXPLAIN ANALYZE를 함께 분석해 filesort 병목 여부를 검증하고, 인덱스 적용 여부를 수치 기반으로 판단했습니다.
```

Shorter resume keyword version:

```text
popular 기사 조회 filesort 병목 검증
```

## Scope

Target API:

```text
GET /api/articles/popular?page={page}&size={size}
```

Repository method:

```text
findByPublishedAtAfterOrderByViewCountDesc(LocalDateTime from, Pageable pageable)
```

Representative SQL shape:

```sql
SELECT *
FROM article
WHERE published_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY view_count DESC
LIMIT 20 OFFSET 0;
```

This analysis does not change:

- DB indexes or schema
- Repository queries
- API response shape
- Redis/cache policy
- rate limiting or async processing

## Overengineering Decision

Adding an index immediately would be too much for the current evidence.

Reasons:

- The local dataset has fewer than 10,000 article rows.
- The first #174 smoke result showed `popular` p95 under 100ms.
- `Using filesort` can be acceptable when the sorted candidate set is small.
- A junior backend portfolio story is stronger if it shows measured judgment: `EXPLAIN signal -> high-load check -> EXPLAIN ANALYZE -> apply or defer`.

Therefore this issue records measurement and decision criteria only.

## Local Data Snapshot

Measured on 2026-07-09 against local Docker MySQL.

| Metric | Value |
| --- | ---: |
| Total `article` rows | 9,853 |
| Recent 30-day rows | 2,070 |
| Min `published_at` | 2015-06-07 09:25:07 |
| Max `published_at` | 2026-06-22 15:26:57 |

## k6 Popular-Focused Result

Measured on 2026-07-09 with local Docker MySQL/Redis and local `bootRun` server on `http://localhost:8080`.

The existing #174 script was reused.
Other article-read scenarios were reduced to `1` VU for `1s`; `popular` was measured with gradual local load.

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLES_SIZE = "20"
$env:LATEST_VUS = "1"
$env:CURSOR_VUS = "1"
$env:POPULAR_VUS = "20"
$env:EXPLORE_VUS = "1"
$env:DETAIL_VUS = "1"
$env:LATEST_DURATION = "1s"
$env:CURSOR_DURATION = "1s"
$env:POPULAR_DURATION = "60s"
$env:EXPLORE_DURATION = "1s"
$env:DETAIL_DURATION = "1s"
$env:POPULAR_PAGES = "0,1,5"
$env:SLEEP_SECONDS = "0.1"
k6 run .\load-tests\k6\articles-read-high-load.js
```

Gradual local load result:

| Popular VUs | Duration | Total requests | RPS | Popular p95 | Failure rate | Notes |
| ---: | --- | ---: | ---: | ---: | ---: | --- |
| 20 | 60s | 6,396 | 106.29/s | 134.87ms | 0.00% | Initial popular-focused baseline |
| 50 | 30s | 3,474 | 114.33/s | 423.79ms | 0.00% | `latest` one-shot threshold crossed, but target `popular` stayed under threshold |
| 100 | 20s | 2,561 | 123.61/s | 902.51ms | 0.00% | p95 increased sharply while RPS rose only slightly |

Interpretation:

- `popular` p95 increased as VUs rose: `134.87ms -> 423.79ms -> 902.51ms`.
- RPS did not scale proportionally: `106.29/s -> 114.33/s -> 123.61/s`.
- Failure rate stayed `0.00%` through 100 VUs.
- This pattern suggests local execution or application-side saturation may be involved. It does not by itself prove a DB index bottleneck.
- The run did not capture application stdout logs reliably, so `dbQueryMs` was not recorded from `[ArticlesPopular]` logs in this issue. DB-side `EXPLAIN ANALYZE` actual time is used as the supporting DB signal instead.

## EXPLAIN ANALYZE Result

### Page 0 content query

```sql
EXPLAIN ANALYZE
SELECT *
FROM article
WHERE published_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY view_count DESC
LIMIT 20 OFFSET 0;
```

Observed plan summary:

| Step | Actual time | Rows |
| --- | --- | ---: |
| Index range scan on `idx_article_published_at` | 0.0362..4.88ms | 2,070 |
| Sort by `view_count DESC` with top-20 limit | 6.41..6.44ms | 20 |
| Limit | 6.41..6.44ms | 20 |

Traditional `EXPLAIN` still reports:

```text
Using index condition; Using filesort
```

### Page 5 content query

The script includes `POPULAR_PAGES=0,1,5`.
Page 5 maps to `OFFSET 100` when `size=20`.

```sql
EXPLAIN ANALYZE
SELECT *
FROM article
WHERE published_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY view_count DESC
LIMIT 20 OFFSET 100;
```

Observed plan summary:

| Step | Actual time | Rows |
| --- | --- | ---: |
| Index range scan on `idx_article_published_at` | 0.0935..7.95ms | 2,070 |
| Sort by `view_count DESC` with top-120 limit | 11.2..11.4ms | 120 |
| Limit/Offset | 11.4..11.5ms | 20 |

Traditional `EXPLAIN` still reports:

```text
Using index condition; Using filesort
```

### Count query

Spring Data `Page` can also trigger a count query.

```sql
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM article
WHERE published_at > DATE_SUB(NOW(), INTERVAL 30 DAY);
```

Observed plan summary:

| Step | Actual time | Rows |
| --- | --- | ---: |
| Covering index range scan on `idx_article_published_at` | 0.0215..0.448ms | 2,070 |
| Filter | 0.0242..0.616ms | 2,070 |
| Aggregate count | 0.696..0.696ms | 1 |

The count path uses `idx_article_published_at` as a covering index and is not the current concern.

## Decision

Defer DB index changes for now.

The `popular` query does use filesort, and API p95 rises under local VU pressure.
However, the current evidence still does not prove that a DB index change is the right next step:

- `popular` k6 p95: 134.87ms at 20 VUs, 423.79ms at 50 VUs, 902.51ms at 100 VUs
- failure rate: 0.00%
- RPS plateaued from 106.29/s to 123.61/s while VUs increased from 20 to 100
- page 0 DB-side actual time: about 6.44ms
- page 5 DB-side actual time: about 11.5ms
- count query actual time: about 0.696ms

At the current data scale, the filesort is a watch item rather than an immediate optimization target.
The sharper p95 increase should first be investigated with reliable application log capture, especially `[ArticlesPopular] dbQueryMs`, connection pool signals, and local resource usage.

## Follow-up Criteria

Open a DB index implementation issue only if one of the following becomes true under a repeatable measurement shape:

- `popular` p95 repeatedly exceeds a chosen service threshold, for example 300ms to 500ms locally, and DB time rises with it.
- `[ArticlesPopular] dbQueryMs` is captured and repeatedly dominates `totalMs`.
- `EXPLAIN ANALYZE` sort time grows materially as recent 30-day rows increase.
- deeper popular pages become product-critical and offset cost starts to dominate.

Candidate index experiments, if needed later:

```sql
CREATE INDEX idx_article_view_count_published_at
ON article (view_count DESC, published_at);

CREATE INDEX idx_article_published_at_view_count
ON article (published_at, view_count DESC);
```

These should not be applied without a before/after measurement because each index has different trade-offs:

- `(view_count DESC, published_at)` may help ordering but can be less direct for the 30-day range filter.
- `(published_at, view_count DESC)` matches the range filter first but may still not fully remove sorting after a range condition.
- both add write/storage overhead.

## Next Candidate

If continuing the DB-read performance track, the next useful issue is not index implementation yet.
A better next issue is to make local performance runs capture application logs and resource signals reliably so `dbQueryMs`, `totalMs`, connection pool behavior, and k6 output can be compared in the same run window.

Candidate next issue:

```text
[OBS] 로컬 부하 테스트 시 애플리케이션 latency 로그와 리소스 지표 캡처 안정화
```

This was started as #178.
The runbook is `docs/backend-improvement/local-load-observability-runbook.md`.

After that, rerun the same 20/50/100 VU test and decide whether the p95 growth is DB query time, application thread/connection pool pressure, or local machine saturation.
