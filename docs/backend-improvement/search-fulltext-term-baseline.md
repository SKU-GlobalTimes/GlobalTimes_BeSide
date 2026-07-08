# Search API FULLTEXT term baseline

## Purpose

This document defines a repeatable baseline for `GET /api/search` by search term type.
It connects the #133/#134 MySQL FULLTEXT query-plan improvement to API-level measurements such as p95 response time, failure rate, and result count.

This work does not change code behavior, DB schema, FULLTEXT query shape, API response shape, Redis policy, or translation policy.

## Portfolio Summary Candidate

```text
검색 API의 MySQL FULLTEXT 쿼리를 검색어 유형별로 측정하고 실행 계획 개선 근거와 연결해, p95 응답 시간과 실패율 기준선을 수립했습니다.
```

Shorter resume keyword version:

```text
검색 API MySQL FULLTEXT 실행 계획 분석 및 검색어별 p95/실패율 기준선 수립
```

## Background

#133/#134 analyzed the Search API FULLTEXT query path.
For English inputs where the original search text and translated text are effectively the same, the previous duplicated `OR MATCH` shape could make MySQL choose `idx_article_published_at` instead of the intended FULLTEXT index.

The current code avoids duplicated `OR MATCH` for same original/translated terms and keeps the OR query only when the original and translated terms differ.

Known local DB-level match counts from #133:

| Keyword | Match count | Interpretation |
| --- | ---: | --- |
| `war` | 404 | English same-term, high-match sample |
| `technology` | 82 | English same-term, medium-match sample |
| `economy` | 39 | English same-term, lower-match sample |
| `대구` OR `daegu` | 0 | Korean input translated to English, zero current FULLTEXT match sample |

Known API checks after #123/#126 and #133/#134:

| Request | Status | Notes |
| --- | --- | --- |
| `/api/search?text=war` | 200 | English, many matches |
| `/api/search?text=economy` | 200 | English, fewer matches |
| `/api/search?text=technology` | 200 | English, medium matches |
| `/api/search?text=대구` | 200 | Translates to `daegu`, zero current FULLTEXT matches |

## Measurement Tool

Use `load-tests/k6/search-fulltext-terms.js`.

The script requests `/api/search` for a comma-separated search term set and tags each request with `searchTerm`.
It also records the response `data.searchArticles.length` into the custom `search_result_count` metric.

Default terms:

```text
war,korea,economy,technology,대구,AI
```

## Search Term Set

| Type | Term | Why |
| --- | --- | --- |
| English high-match | `war` | Exercises many-match same-term FULLTEXT behavior from #133. |
| English common topic | `korea` | Checks common English keyword behavior after #123 fixed 500 responses. |
| English lower-match | `economy` | Checks lower result-count path. |
| English medium-match | `technology` | Checks medium result-count path from #133. |
| Korean translated zero-match | `대구` | Confirms translated/original search can return 200 even with zero local FULLTEXT matches. |
| Short/common technical term | `AI` | Checks short uppercase input handling and result count variance. |

Do not treat result count alone as search quality.
This baseline records performance and stability first.
Search relevance and multilingual quality require separate samples and manual review.

## Execution

Start local dependencies and server first.

```powershell
docker compose -f docker-compose.dev.yml up -d
.\gradlew.bat bootRun
```

Run a short smoke baseline:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:SEARCH_TERMS = "war,korea,economy,technology,대구,AI"
$env:SEARCH_VUS = "1"
$env:SEARCH_DURATION = "30s"
$env:SLEEP_SECONDS = "1"
k6 run .\load-tests\k6\search-fulltext-terms.js
```

Run a longer local baseline:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:SEARCH_TERMS = "war,korea,economy,technology,대구,AI"
$env:SEARCH_VUS = "3"
$env:SEARCH_DURATION = "2m"
$env:SLEEP_SECONDS = "1"
k6 run .\load-tests\k6\search-fulltext-terms.js
```

Optional filters:

```powershell
$env:COUNTRY = "kr"
$env:CATEGORY = "business"
$env:DATE = "2026-07-01"
```

## Result Recording Template

Record p95 and failure rate from k6 output.
Record result count from `search_result_count` per `searchTerm` and confirm `SearchArticlesService` logs for `resultCount`, `dbSearchMs`, and `totalMs` when needed.

| Date | Environment | Term | Type | p95 | Failure rate | Result count | Notes |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
|  | local/dev | `war` | English high-match |  |  |  |  |
|  | local/dev | `korea` | English common topic |  |  |  |  |
|  | local/dev | `economy` | English lower-match |  |  |  |  |
|  | local/dev | `technology` | English medium-match |  |  |  |  |
|  | local/dev | `대구` | Korean translated zero-match |  |  |  |  |
|  | local/dev | `AI` | Short/common technical term |  |  |  |  |

## Initial Local Smoke Result

The first baseline was measured on 2026-07-09 with local Docker MySQL/Redis containers and a local `bootRun` server on `http://localhost:8080`.
Each term was executed separately with `SEARCH_VUS=1`, `SEARCH_DURATION=10s`, and `SLEEP_SECONDS=1`.

This is a smoke baseline, not a capacity limit test.
Use it as a same-environment comparison point for later query, cache, or search-policy changes.

| Date | Environment | Term | Type | p95 | Failure rate | Result count | Requests | Checks | Notes |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 2026-07-09 | local/dev | `war` | English high-match | 76.63ms | 0.00% | 100 | 10 | 100.00% | API limit-sized result set |
| 2026-07-09 | local/dev | `korea` | English common topic | 60.38ms | 0.00% | 40 | 10 | 100.00% | Common English keyword |
| 2026-07-09 | local/dev | `economy` | English lower-match | 63.24ms | 0.00% | 39 | 10 | 100.00% | Lower result-count sample |
| 2026-07-09 | local/dev | `technology` | English medium-match | 103.41ms | 0.00% | 82 | 10 | 100.00% | Slowest sample in this smoke run |
| 2026-07-09 | local/dev | `대구` | Korean translated zero-match | 50.14ms | 0.00% | 0 | 10 | 100.00% | Valid zero-match result |
| 2026-07-09 | local/dev | `AI` | Short/common technical term | 24.78ms | 0.00% | 0 | 10 | 100.00% | Valid zero-match result |

## Interpretation Rules

- A 2xx response with `0` results is not a failure. It can be a valid zero-match FULLTEXT result.
- A high result count can still be low relevance; this document is not a relevance benchmark.
- If English same-term searches are slow, compare with #133 EXPLAIN notes and inspect whether the single-MATCH path is still used.
- If translated/original terms differ and performance is slow, analyze the OR-MATCH path separately before considering query rewrites.
- Elasticsearch or another search engine should be considered only after repeated evidence of missing recall, poor multilingual relevance, or p95 instability that cannot be addressed with MySQL FULLTEXT tuning.

## Current Scope Decision

This issue establishes the measurement baseline and script.
The current PR validates the script shape with `node --check`, `k6 inspect`, `git diff --check`, and a local k6 smoke run against `localhost:8080`.
The smoke run recorded per-term p95, failure rate, result count, request count, and check success rate.

It intentionally avoids:

- changing repository queries
- adding Elasticsearch
- changing translation behavior
- changing API response shape
- adding new DB indexes

The next step is to repeat the same term set after a future query, cache, or search-policy change and compare p95/failure-rate trends by term type.
