# Article crawl latency baseline

## Purpose

This document defines a repeatable baseline for the article summary path that may trigger synchronous external article crawling.
It connects #137/#138 timeout/fallback and transaction-boundary work to API-level measurements such as p95 response time, failure rate, crawler fallback rate, and summary success rate.

This work does not change crawler behavior, timeout values, API response shape, DB schema, Redis policy, Gemini calls, or async processing.

## Portfolio Summary Candidate

```text
기사 요약 요청에서 동기 원문 크롤링으로 인한 응답 지연을 cold/warm/fallback 조건으로 분리 측정하고, 외부 호출 개선 기준선을 수립했습니다.
```

Shorter resume keyword version:

```text
기사 요약 API 동기 외부 크롤링 p95/fallback 기준선 수립
```

## Background

#137/#138 stabilized the article crawling path.

Current behavior:

- `ArticleCrawler` uses `crawler.timeout-ms`, default `5000`.
- `ArticleCrawler` returns `Optional.empty()` when crawling or paragraph extraction fails.
- `DetailService#getArticleCrawledContent()` runs external crawling outside the DB transaction.
- `ArticleCrawlContentService` handles crawl target lookup and crawled-content save in separate transactions.
- `GET /api/ai/{id}/summary` returns cached `summary` immediately when it exists.
- If `summary` is missing, the API tries `crawledContent`; when it is missing, it performs external crawling.
- If crawling fails, the non-SSE summary endpoint returns HTTP 202 with `isSuccess=false` and the article lead content as fallback data.

This means the summary endpoint can include different latency sources:

| Scenario | What happens | What it measures |
| --- | --- | --- |
| Summary hit | Existing `summary` is returned | Fastest stored-summary path |
| Crawled-content hit | Existing `crawledContent` is used, then Gemini summary may run | AI call path without external crawling |
| Crawl cold success | External crawl succeeds, then Gemini summary may run | External crawl plus AI call path |
| Crawl fallback | External crawl fails or paragraphs are empty | Timeout/failure handling and fallback path |

Do not interpret this baseline as a pure crawler microbenchmark.
The public API path can include AI summary generation unless the summary is already cached.
This document intentionally measures the user-facing path first because that is the path affected by synchronous external crawling.

## Measurement Tool

Use `load-tests/k6/article-crawl-baseline.js`.

The script requests `GET /api/ai/{id}/summary` for a comma-separated article id set and tags each request with:

- `api=article_crawl_summary`
- `endpoint=summary`
- `articleId`
- `scenario`

It records:

- `http_req_duration{api:article_crawl_summary}`
- `http_req_failed{api:article_crawl_summary}`
- `article_crawler_fallback`
- `article_summary_success`
- `article_summary_payload_size`

## Execution

Start local dependencies and server first.

```powershell
docker compose -f docker-compose.dev.yml up -d
.\gradlew.bat bootRun
```

Run a short smoke baseline:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLE_IDS = "1"
$env:LANGUAGE = "영어"
$env:SCENARIO_LABEL = "summary_smoke"
$env:ARTICLE_CRAWL_VUS = "1"
$env:ARTICLE_CRAWL_DURATION = "30s"
$env:SLEEP_SECONDS = "1"
k6 run .\load-tests\k6\article-crawl-baseline.js
```

Run separate scenario labels when preparing a before/after comparison:

```powershell
$env:SCENARIO_LABEL = "summary_hit"
k6 run .\load-tests\k6\article-crawl-baseline.js

$env:SCENARIO_LABEL = "crawl_fallback"
k6 run .\load-tests\k6\article-crawl-baseline.js
```

## Result Recording Template

Record p95 and failure rate from k6 output.
Record fallback/success rates from custom metrics.
Use service logs to confirm whether the run was summary hit, crawled-content hit, crawl cold success, or crawl fallback.

| Date | Environment | Article IDs | Scenario | p95 | Failure rate | Crawler fallback rate | Summary success rate | Notes |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- |
|  | local/dev |  | summary hit |  |  |  |  | Existing summary returned |
|  | local/dev |  | crawled-content hit |  |  |  |  | External crawl skipped, AI may run |
|  | local/dev |  | crawl cold success |  |  |  |  | External crawl and AI may run |
|  | local/dev |  | crawl fallback |  |  |  |  | HTTP 202 fallback expected |

## Initial Local Smoke Result

The first smoke run was measured on 2026-07-09 with local Docker MySQL/Redis containers and a local `bootRun` server on `http://localhost:8080`.
It used article `7366`, which already had both `summary` and `crawled_content`.

This result validates the k6 script and records the stored-summary hit baseline.
It does not measure cold external crawling.

| Date | Environment | Article IDs | Scenario | p95 | Failure rate | Crawler fallback rate | Summary success rate | Payload size | Notes |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 2026-07-09 | local/dev | `7366` | summary hit | 332.6ms | 0.00% | 0.00% | 100.00% | 420 | Existing summary returned; crawler and Gemini skipped |

## Interpretation Rules

- HTTP 202 with `isSuccess=false` and a crawler message is a handled crawler fallback, not a server failure.
- HTTP 5xx should be treated as an API failure.
- A low p95 on summary hit does not prove crawler performance is good; it proves stored summary bypasses crawler/AI work.
- A high p95 on crawl cold success can include both external crawling and Gemini summary generation.
- If the goal is pure crawler timing, add service-level instrumentation or an isolated test in a later issue.
- Kafka, async jobs, Redis cache changes, or crawler timeout changes should be considered only after repeated evidence that the synchronous path is unstable or too slow.

## Current Scope Decision

This issue establishes a measurement script and baseline document for the user-facing article summary path.
It intentionally avoids:

- changing crawler timeout values
- adding async processing or Kafka
- adding Redis/local cache behavior
- changing summary or fallback API responses
- changing DB schema
- changing external AI call behavior

The next step after collecting stable local measurements is to decide whether timeout/fallback tuning, stored-content reuse, local cache, or async processing is justified.
