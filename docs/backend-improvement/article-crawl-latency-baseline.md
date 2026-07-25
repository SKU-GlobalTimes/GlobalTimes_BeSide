# 기사 본문 크롤링 지연 기준선

## 한국어 학습 안내

이 문서는 기사 요약 API의 전체 응답시간을 크롤링, Gemini 요약, 기존 summary 재사용 단계로 나눠 해석하기 위한 기준선이다. 응답이 느리다는 사실만으로 외부 AI를 병목으로 단정하지 않고, 저장된 summary가 있는 경우와 본문 크롤링·외부 호출이 필요한 경우를 분리한다.

핵심 개념은 **단계별 latency**, **fallback**, **cache 또는 저장 결과 재사용**이다. 부하 테스트의 상위 응답시간 경계는 사용자 관점의 전체 시간이고, 애플리케이션 log field는 내부 단계 시간을 설명한다. 두 값을 함께 봐야 crawler 지연, Gemini 지연, 로컬 자원 압박을 구분할 수 있다.

현재 기록은 기존 summary가 있는 smoke 조건이며 crawler와 Gemini 경로의 처리량을 증명하지 않는다. 아래 원본 측정 기록은 후속 mock 외부 호출 기준선과 연결하기 위한 실행 조건·명령·표를 보존한다.

## 원본 측정 기록

## Purpose

This document defines a repeatable baseline for the article summary path that may trigger synchronous external article crawling.
It connects #137/#138 timeout/fallback and transaction-boundary work to API-level measurements such as p95 response time, failure rate, crawler fallback rate, and summary success rate.

This work does not change crawler behavior, timeout values, API response shape, DB schema, Redis policy, Gemini calls, or async processing.

## 핵심 결과 요약

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

## Stage-Level Log Fields

#172 adds stage-level latency logs so the k6 summary path can be interpreted without guessing from the response body alone.

`AiController#summarizeArticle()` logs the user-facing summary path:

```text
[AiSummary] articleId={} summaryHit={} crawledContentRequested={} crawlerFallback={} aiRequested={} summaryLookupMs={} crawlContentMs={} fallbackContentMs={} aiSummaryMs={} summarySaveMs={} totalMs={}
```

`DetailService#getArticleCrawledContent()` logs the crawled-content lookup and external crawling path:

```text
[ArticleCrawlContent] articleId={} crawledContentHit={} crawlAttempted={} crawlSuccess={} crawlTargetLookupMs={} crawlMs={} saveMs={} totalMs={}
```

The logs intentionally avoid article body, summary text, URL, question text, API keys, tokens, and `.env` values.
Use them with `SCENARIO_LABEL` in k6 to identify whether a run was summary hit, crawled-content hit, crawl cold success, or crawl fallback.

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
