# Mixed API constant-arrival-rate baseline

## Status

Issue #192 is in progress. The local 20/40/60 RPS measurement is complete and awaits PR review.

## Purpose

This baseline measures a synthetic mix of public API traffic against one local Spring Boot instance. It combines k6 results with Spring Boot Actuator signals so latency can be correlated with servlet threads, DB connections, or the bounded AI executor.

This is a local bottleneck investigation on an 8GB laptop. It is not a production traffic model, SLA, or multi-Pod capacity claim.

## Overengineering guardrail

Chosen:

- existing k6, mock Gemini, and Actuator tooling
- one local Spring Boot process with Docker MySQL and Redis
- short `20 -> 40 -> 60 RPS` stages
- measurement before tuning

Not chosen:

- OAuth, social-login, or authenticated scrap traffic
- real Gemini or Google Translate calls
- distributed load generation, EC2, or Kubernetes
- index, cache, pool, or executor tuning without evidence

The ratio is synthetic and repeatable. It must not be presented as observed production behavior.

## Traffic model

Use `load-tests/k6/mixed-arrival-rate.js`.

| Endpoint | Ratio | 20 RPS | 40 RPS | 60 RPS |
| --- | ---: | ---: | ---: | ---: |
| latest | 35% | 7 | 14 | 21 |
| popular | 25% | 5 | 10 | 15 |
| detail | 20% | 4 | 8 | 12 |
| search | 15% | 3 | 6 | 9 |
| mock summary | 5% | 1 | 2 | 3 |

Actuator runs as a separate telemetry scenario every five seconds. Its requests are excluded from target business RPS.

## Controlled prerequisites

- Use an article with `crawled_content` and no saved summary so summary reaches mock Gemini.
- Back up the article summary and view count, then restore both after testing.
- Disable summary persistence and use a fixed three-second mock response.
- Seed `translation:war` in Redis so search cannot call Google Translate.
- Expose Actuator health/metrics only for this local run.

```powershell
docker exec globaltimes_beside-redis-1 redis-cli SETEX translation:war 86400 war

$env:MOCK_GEMINI_PORT = "9090"
$env:MOCK_GEMINI_DELAY_MS = "3000"
$env:MOCK_GEMINI_STATUS = "200"
node .\load-tests\mock-gemini-server.js
```

Backend overrides:

```powershell
$env:GEMINI_BASE_URL = "http://localhost:9090"
$env:AI_SUMMARY_SAVE_ENABLED = "false"
$env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = "health,metrics"
$env:SERVER_TOMCAT_MBEANREGISTRY_ENABLED = "true"
.\gradlew.bat bootRun
```

## Execution

Run each stage separately and continue only when the previous stage has no unexpected status, no dropped iterations, and no unsafe memory pressure.

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLE_ID = "7366"
$env:SEARCH_TEXT = "war"
$env:DURATION = "30s"
$env:METRICS_INTERVAL_SECONDS = "5"

$env:TOTAL_RPS = "20"
k6 run .\load-tests\k6\mixed-arrival-rate.js

$env:TOTAL_RPS = "40"
k6 run .\load-tests\k6\mixed-arrival-rate.js

$env:TOTAL_RPS = "60"
k6 run .\load-tests\k6\mixed-arrival-rate.js
```

Stop escalation when unexpected errors occur, dropped iterations are nonzero, the host becomes unresponsive, or p95 rises sharply without additional achieved throughput.

Summary HTTP 503 is an expected bounded-overload response. Record it separately instead of hiding it in aggregate latency or treating it as an unexpected status.

## Metrics

- scheduled and achieved RPS, count, p95, and failure rate per endpoint
- summary 200/503/unexpected counts and accepted/rejected p95
- `dropped_iterations`
- Tomcat busy/current threads
- Hikari active/pending connections
- AI executor active/queued tasks
- application, MySQL, and Redis resource snapshots

## Results

| Target RPS | Achieved business RPS | Dropped | Latest p95 | Popular p95 | Detail p95 | Search p95 | Summary 200/503 | Summary accepted p95 | Tomcat busy peak | Hikari active/pending peak | Executor active/queued peak |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 20 | 20.13 | 0 | 46.04ms | 70.84ms | 53.34ms | 78.01ms | 30/0 | 3.08s | 6/10 current | 5/0 | 3/0 |
| 40 | 40.17 | 0 | 35.43ms | 59.75ms | 41.30ms | 63.99ms | 61/0 | 3.06s | 6/10 current | 5/0 | 6/0 |
| 60 | 60.13 | 0 | 45.08ms | 75.90ms | 54.92ms | 81.59ms | 91/0 | 3.07s | 6/10 current | 5/0 | 9/0 |

### Measurement environment

| Item | Value |
| --- | --- |
| Date | 2026-07-15 |
| Application | one local `bootRun` process on port 8080 |
| DB/cache | Docker MySQL 8.0 and Redis 7 |
| Gemini | local mock on port 9090, fixed 3-second HTTP 200 |
| Summary persistence | disabled |
| Search translation | Redis-seeded `translation:war=war` |
| Test article | 7366; summary and view count restored after all runs |
| Actuator interval | six metric requests about every five seconds |

`Achieved business RPS` is the sum of the five endpoint request counters divided by the 30-second arrival window. It excludes Actuator requests. The aggregate k6 `http_reqs/s` is lower because its denominator includes summary graceful completion after arrivals stop, and its numerator includes telemetry requests.

Business request counts were 604, 1,205, and 1,804. Every read request returned HTTP 200, every summary request returned HTTP 200, unexpected status was zero, and all k6 thresholds passed.

### Resource samples

The following values are the highest periodic samples observed during each run. They are not continuous profilers or guaranteed absolute peaks.

| Target RPS | App working set | MySQL CPU sample | MySQL memory | Redis memory |
| ---: | ---: | ---: | ---: | ---: |
| 20 | 380.8MiB | 20.99% | 449.0MiB | 11.64MiB |
| 40 | 394.0MiB | 37.96% | 449.2MiB | 11.58MiB |
| 60 | 395.4MiB | 54.41% | 451.4MiB | 11.70MiB |

### Result interpretation

- All three stages achieved the requested business arrival rate without dropped iterations or failures.
- Read p95 did not rise monotonically and stayed below 82ms, so this range does not show read-path saturation.
- Summary accepted p95 stayed near the controlled three-second upstream delay. Executor active tasks scaled from 3 to 9 without queueing or 503 responses.
- Tomcat busy stayed at or below 6 of 10 current threads, and Hikari pending stayed zero. Neither servlet threads nor DB connection acquisition was the first bottleneck.
- MySQL CPU samples rose with arrival rate while memory remained stable. This is a utilization trend, not enough evidence for an index or Hikari change because p95 and pending connections remained stable.
- The first saturation boundary is above 60 RPS for this synthetic local mix. A future step should repeat the same environment at a cautiously higher rate or use a fixed container CPU/memory limit before selecting an implementation technology.

## Interpretation rules

- Rising Hikari pending connections with API p95 supports a DB connection-wait investigation.
- Stable Hikari metrics with high Tomcat busy threads supports thread, CPU, or serialization investigation.
- A full AI queue plus fast 503 responses confirms intentional overload protection, not higher throughput.
- Low internal metrics with high client p95 requires checking host contention and application logs before changing indexes.
- Do not multiply one local result directly into a multi-Pod production capacity promise.

Select the next implementation issue from the first repeated bottleneck signal, not from a preferred technology.
