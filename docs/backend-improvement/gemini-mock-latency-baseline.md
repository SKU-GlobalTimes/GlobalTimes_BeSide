# Gemini mock latency baseline

## Purpose

This document records the plan and baseline shape for measuring the article AI summary path with controlled Gemini-like latency.

The goal is not to benchmark the real Gemini service.
The goal is to measure how this backend behaves when the user-facing summary path includes a synchronous external AI call with predictable latency.

## Portfolio Summary Candidate

```text
Gemini external call path was isolated with a local mock server and k6 load testing, so AI summary latency could be measured without API cost or quota noise.
```

Shorter resume keyword version:

```text
AI external call mock latency load-test baseline
```

## Overengineering Decision

This issue adds the minimum runtime hooks needed for repeatable local measurement.

Included:

- configurable `gemini.base-url`
- local-only `AI_SUMMARY_SAVE_ENABLED=false` option for repeated AI-path measurement
- lightweight Node mock server
- measurement document and run policy

Not included:

- async processing
- queue/Kafka
- Redis cache policy changes
- real Gemini load testing
- production timeout policy changes
- API response shape changes

Reasoning:

- Real Gemini load testing can create cost, quota, rate-limit, and response-variance noise.
- Existing summary caching means only the first request would hit Gemini unless persistence is disabled or the DB is reset between requests.
- Disabling summary save is safer as a local measurement flag because production default remains enabled.
- On the current 8GB local laptop, 50+ VUs already showed local saturation on read-only APIs, so AI-call tests should use smaller gradual steps first.

## Runtime Controls

Production defaults are unchanged:

```yaml
gemini:
  base-url: https://generativelanguage.googleapis.com

ai:
  summary-save-enabled: true
```

Local mock test override:

```powershell
$env:GEMINI_BASE_URL = "http://localhost:9090"
$env:AI_SUMMARY_SAVE_ENABLED = "false"
```

`AI_SUMMARY_SAVE_ENABLED=false` prevents the first successful mock summary from being persisted and turning later requests into summary hits.
Use it only for local load tests.

## Mock Gemini Server

Start a local mock server:

```powershell
$env:MOCK_GEMINI_PORT = "9090"
$env:MOCK_GEMINI_DELAY_MS = "200"
$env:MOCK_GEMINI_STATUS = "200"
node .\load-tests\mock-gemini-server.js
```

Health check:

```powershell
Invoke-WebRequest http://localhost:9090/health
```

The mock server returns a Gemini-like JSON body:

```json
{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "Mock Gemini summary for local load testing."
          }
        ]
      }
    }
  ]
}
```

The mock does not log prompt bodies, API keys, article text, or user questions.

## k6 Scenario

Reuse the existing user-facing summary path script:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLE_IDS = "<article-id-with-crawled-content>"
$env:LANGUAGE = "English"
$env:SCENARIO_LABEL = "gemini_mock_200ms"
$env:ARTICLE_CRAWL_DURATION = "30s"
$env:SLEEP_SECONDS = "0.1"
```

Recommended VU steps for this laptop:

```text
1 VU / 30s
3 VUs / 30s
5 VUs / 30s
10 VUs / 30s
20 VUs / 30s only if the 10 VU run is stable
```

Do not start with 50+ VUs for this path.
External-call latency holds request threads longer than DB read-only APIs, so the same VU count can create stronger pressure.

## Existing k6 VU/RPS Context

| Previous work | Scenario | VUs | RPS/TPS signal | p95 signal | Interpretation |
| --- | --- | ---: | ---: | ---: | --- |
| #168 | search FULLTEXT term smoke | 1 | about 10 requests per term run | 24ms to 103ms by term | query-type smoke, not capacity |
| #170 | summary hit smoke | 1 | 30s smoke | 332.6ms | existing summary returned; Gemini skipped |
| #176 | popular-focused read load | 20 | 106.29/s | 134.87ms | stable local read load |
| #176 | popular-focused read load | 50 | 114.33/s | 423.79ms | p95 rose, RPS barely increased |
| #176 | popular-focused read load | 100 | 123.61/s | 902.51ms | saturation signal, not immediate DB-index proof |
| #180 | single-instance popular baseline | 5 | 24.19/s | 105.05ms | stable |
| #180 | single-instance popular baseline | 10 | 45.91/s | 94.62ms | stable |
| #180 | single-instance popular baseline | 20 | 87.29/s | 105.48ms | stable local baseline |
| #180 | single-instance popular baseline | 50 | 86.15/s | 456.72ms | throughput plateau with latency growth |

For these HTTP tests, RPS and TPS can be treated similarly when one API request is one transaction.
VU is concurrency, not throughput.
Throughput is the completed request rate produced by those VUs.

Approximate relation:

```text
RPS ~= VUs / (average response time + sleep time + script overhead)
```

## Laptop Resource Policy

Use the local laptop budget conservatively:

- start from 1 VU and increase gradually
- stop when RPS plateaus while p95 rises sharply
- stop when failure rate becomes non-zero under the same condition
- stop if Docker memory, app memory, or local responsiveness degrades noticeably
- treat 20 VU as the upper normal step for AI-call mock tests on this machine
- treat 50+ VU as an optional stress signal only after smaller steps are understood

## Measurement Environment

- Date: 2026-07-13
- Backend: local Spring Boot on `localhost:8080`
- Dependencies: Docker MySQL 8.0 and Redis 7
- Mock: local Node server on `localhost:9090`
- k6 scenario: `article-crawl-baseline.js`, one article ID, `SLEEP_SECONDS=0.1`
- Test article: an article with existing `crawled_content`; its summary was backed up, cleared only for the run, and restored afterward
- Runtime flags: `GEMINI_BASE_URL=http://localhost:9090`, `GEMINI_API_KEY=dummy`, `AI_SUMMARY_SAVE_ENABLED=false`

The first manual request was excluded from the baseline because WebClient and application warm-up raised it to about 1.63 seconds.
The following k6 runs used the warmed application path.

## Results

| Date | Environment | Mock delay | VUs | Duration | Requests | RPS | p95 | Failure rate | `aiSummaryMs` signal | Notes |
| --- | --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | --- | --- |
| 2026-07-13 | local/dev | 200ms | 1 | 30s | 87 | 2.87 | 264.02ms | 0.00% | 224ms | stable |
| 2026-07-13 | local/dev | 200ms | 3 | 30s | 252 | 8.33 | 289.96ms | 0.00% | - | stable |
| 2026-07-13 | local/dev | 200ms | 5 | 30s | 437 | 14.45 | 262.90ms | 0.00% | - | stable |
| 2026-07-13 | local/dev | 200ms | 10 | 30s | 864 | 28.52 | 270.10ms | 0.00% | - | stable |
| 2026-07-13 | local/dev | 200ms | 20 | 30s | 1,753 | 57.80 | 266.27ms | 0.00% | - | stable upper step |
| 2026-07-13 | local/dev | 1000ms | 1 | 30s | 27 | 0.88 | 1040.88ms | 0.00% | 1033ms | stable |
| 2026-07-13 | local/dev | 1000ms | 3 | 30s | 81 | 2.63 | 1057.59ms | 0.00% | - | stable |
| 2026-07-13 | local/dev | 1000ms | 5 | 30s | 135 | 4.39 | 1047.58ms | 0.00% | - | stable |
| 2026-07-13 | local/dev | 3000ms | 1 | 30s | 10 | 0.32 | 3039.06ms | 0.00% | 3020ms | stable |
| 2026-07-13 | local/dev | 3000ms | 3 | 30s | 30 | 0.95 | 3050.94ms | 0.00% | - | stable |
| 2026-07-13 | local/dev | 3000ms | 5 | 30s | 50 | 1.59 | 3051.71ms | 0.00% | - | stable |
| 2026-07-13 | local/dev | 500 at 200ms | 1 | 15s | 43 | 2.85 | 263.30ms | 100.00% | - | expected threshold failure |

Use `[AiSummary]` logs to verify:

```text
summaryHit=false
aiRequested=true
summarySaveMs=0
```

`summarySaveMs=0` is expected when `AI_SUMMARY_SAVE_ENABLED=false`.

Representative application logs confirmed the intended path:

```text
200ms:  summaryHit=false aiRequested=true aiSummaryMs=224  summarySaveMs=0 totalMs=264
1000ms: summaryHit=false aiRequested=true aiSummaryMs=1033 summarySaveMs=0 totalMs=1068
3000ms: summaryHit=false aiRequested=true aiSummaryMs=3020 summarySaveMs=0 totalMs=3052
```

## Interpretation

- Normal-path p95 followed the configured mock delay closely. The synchronous external call dominates this API latency.
- At 200ms, throughput increased from 2.87 RPS at 1 VU to 57.80 RPS at 20 VU while p95 stayed near 263ms to 290ms and failures stayed at 0%.
- At 1s and 3s, throughput remained approximately proportional to VU count, but each VU completed fewer requests because it waited on the external call longer.
- No local saturation was observed in the tested range. This does not predict real Gemini capacity because real quota, network variance, and rate limits were intentionally excluded.
- A controlled Gemini 500 response produced backend 5xx responses for all 43 requests. The current summary path has no Gemini failure fallback, so external failure propagates to the user-facing API.
- The 500 scenario intentionally crossed the k6 `http_req_failed < 5%` threshold; its non-zero exit is the expected test result.

This baseline supports a later, separate decision about timeout/fallback or asynchronous processing. It does not justify adding those mechanisms in #182 by itself.

## Follow-up Boundary

If a follow-up issue is approved, compare a minimal timeout/fallback policy against this baseline before considering async processing, queues, or additional infrastructure.
