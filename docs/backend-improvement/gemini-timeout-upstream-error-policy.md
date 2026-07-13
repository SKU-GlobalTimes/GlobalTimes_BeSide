# Gemini timeout and upstream error policy

## Problem

#182 isolated the synchronous Gemini call with a local mock server.
Normal-path p95 followed mock latency, and a mock 500 response propagated as a generic backend 500 for every request.

Before #184:

- `AiService` used a fixed 90-second Reactor timeout.
- Gemini non-2xx, timeout, and internal response parsing errors all became the same backend 500.
- the non-2xx handler logged the full upstream error body
- a 15-second mock response kept the user-facing request waiting for the full upstream delay

## Overengineering Decision

This issue applies the smallest resilience change supported by the #182 evidence.

Included:

- configurable `gemini.timeout-ms`
- 10-second default timeout for the synchronous article summary path
- Gemini non-2xx mapped to 502 Bad Gateway
- Gemini timeout mapped to 504 Gateway Timeout
- internal parsing or application errors kept as 500 Internal Server Error
- regression tests and mock before/after verification

Not included:

- retry
- circuit breaker or Resilience4j
- async queue, Kafka, or RabbitMQ
- conversion of the normal summary API to an async API
- returning article content as if it were an AI summary
- changes to the SSE and trend Gemini paths

The #182 test range did not show local saturation through 20 VU at 200ms.
Adding queue or circuit-breaker infrastructure before defining traffic and recovery requirements would add more complexity than the current evidence supports.

## Policy

| Condition | Backend status | Meaning |
| --- | ---: | --- |
| Gemini success with valid body | 200 | summary returned normally |
| Gemini non-2xx | 502 | the upstream AI service failed or rejected the backend request |
| Gemini exceeds `gemini.timeout-ms` | 504 | the upstream AI service did not answer within the user-facing limit |
| Gemini 2xx with invalid response or internal processing failure | 500 | the backend could not process the response |

Runtime default:

```yaml
gemini:
  timeout-ms: 10000
```

Environment override:

```text
GEMINI_TIMEOUT_MS=<milliseconds>
```

The 10-second default is a user-facing upper bound, not a real Gemini capacity claim.
It leaves margin above #182's controlled 3-second normal path while reducing the previous 90-second maximum wait.

## Before And After

Environment:

- local Spring Boot on `localhost:8080`
- Docker MySQL and Redis
- local Node mock Gemini on `localhost:9090`
- existing k6 summary scenario, 1 VU, 35 seconds, `SLEEP_SECONDS=0.1`
- test article summary backed up, cleared during measurement, and restored afterward
- no real Gemini request or API cost

| Case | Requests | Result | Average | p95 | Interpretation |
| --- | ---: | --- | ---: | ---: | --- |
| Before, mock 15s, fixed 90s timeout | 3 | 200, failure 0% | 15.43s | 16.08s | full upstream delay reached the user |
| After, mock 15s, 10s timeout | 4 | 504, failure 100% | 10.16s | 10.42s | wait bounded near the configured timeout |

The after run intentionally fails the existing k6 success and p95 thresholds.
The improvement is bounded waiting and explicit failure classification, not a higher success rate.
The long-delay run has only three or four requests, so it is timeout-policy evidence rather than a capacity test.

Additional API checks:

| Mock condition | Backend result | Total time |
| --- | ---: | ---: |
| 3-second success | 200 | 3.09s |
| 500 after 200ms | 502 | 0.31s |
| 15-second success with 10-second timeout | 504 | 10.04s |

## Logging And Data Safety

The non-2xx handler records only the upstream status.
Timeout logging records only the configured timeout.
The upstream response body, API key, prompt, article content, and user question are not logged.

The test article's existing summary was restored after both before and after runs, and the temporary backup table was removed.

## Portfolio Summary Candidate

```text
Gemini synchronous summary calls waited through a 15-second upstream delay and exposed dependency failures as generic 500 responses; a configurable 10-second timeout and 502/504 error separation bounded p95 to about 10.42 seconds and clarified the failure source using a cost-free mock server.
```

Short version:

```text
Gemini external-call timeout and 502/504 separation, verified with mock before/after load testing
```
