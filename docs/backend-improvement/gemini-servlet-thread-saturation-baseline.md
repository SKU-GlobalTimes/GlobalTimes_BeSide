# Gemini servlet thread 포화 기준선

## 한국어 학습 안내

이 문서는 느린 동기 Gemini 호출이 Tomcat servlet thread를 점유할 때 관련 없는 인기 기사 API까지 영향을 받는지 확인한다. 요청 thread는 CPU를 계속 쓰지 않더라도 외부 응답을 기다리는 동안 다른 요청을 처리할 수 없으므로 제한된 pool이 모두 busy가 되면 queueing과 latency가 증가한다.

핵심 개념은 **servlet thread pool**, **blocking I/O**, **busy/current thread**, **포화 경계**, **안전 중단 조건**이다. summary 부하와 가벼운 popular 부하를 동시에 보내 외부 호출의 느림이 DB 병목과 다른 형태로 전파되는지 본다.

이 기준선은 제한된 로컬 설정에서 thread 포화 신호를 재현하기 위한 것이며 운영 서버 capacity가 아니다. 높은 VU 단계를 무조건 실행하지 않고 busy thread, 실패율, 노트북 자원 압박이 안전 조건을 넘으면 중단한다.

## 원본 측정 기록

## Purpose

This document measures how the synchronous Gemini summary path affects servlet thread availability and an unrelated article read API.

The target is not a production capacity claim. The experiment deliberately limits one local Spring Boot instance to 20 Tomcat request threads so an 8GB laptop can reproduce saturation with controlled load.

## Overengineering Guardrail

This issue measures before changing the execution model.

Included:

- local Gemini mock with a fixed 3-second response delay
- summary VU steps of 5, 10, 20, and 50
- a fixed 5 VU `popular` read workload in the same run window
- Tomcat busy/current/max thread metrics
- k6 RPS, p95, and failure rate
- local application and Docker resource signals

Excluded:

- async conversion
- retry, circuit breaker, queue, or bulkhead libraries
- real Gemini load traffic
- production thread-pool tuning
- multi-instance or Kubernetes scaling

Async conversion remains a separate candidate and requires measured thread saturation or cross-API latency propagation from this issue.

## Controlled Environment

| Item | Value |
| --- | --- |
| Application | local Spring Boot, one instance |
| DB/cache | local Docker MySQL and Redis |
| Gemini | Node mock server on port 9090 |
| Mock delay | 3,000ms |
| Tomcat max threads | 20, local run override only |
| Tomcat MBean registry | enabled for the local run only |
| Summary persistence | disabled for the run |
| Load generator | local k6 |
| Summary VUs | 5 -> 10 -> 20 -> 50 |
| Popular VUs | fixed at 5 |

The theoretical summary-only upper bound is approximately:

```text
20 request threads / 3 seconds per external call = 6.7 requests/second
```

This is only a hypothesis. DB lookup, request queueing, JSON processing, metric polling, and local resource contention can lower the measured result.

## Local-only Metrics Exposure

Actuator web endpoints are not exposed by default. The load-test process explicitly enables `health` and `metrics`:

```powershell
$env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = "health,metrics"
$env:SERVER_TOMCAT_THREADS_MAX = "20"
$env:SERVER_TOMCAT_MBEANREGISTRY_ENABLED = "true"
```

The k6 script samples:

- `tomcat.threads.busy`
- `tomcat.threads.current`
- `tomcat.threads.config.max`

Production API behavior, response bodies, DB schema, and query logic remain unchanged.

## Run Commands

Mock Gemini:

```powershell
$env:MOCK_GEMINI_DELAY_MS = "3000"
$env:MOCK_GEMINI_STATUS = "200"
node .\load-tests\mock-gemini-server.js
```

Application:

```powershell
$env:GEMINI_BASE_URL = "http://localhost:9090"
$env:GEMINI_API_KEY = "local-mock-key"
$env:AI_SUMMARY_SAVE_ENABLED = "false"
$env:SERVER_TOMCAT_THREADS_MAX = "20"
$env:SERVER_TOMCAT_MBEANREGISTRY_ENABLED = "true"
$env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = "health,metrics"
.\gradlew.bat bootRun --console=plain
```

k6 shared settings:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLE_ID = "7366"
$env:DURATION = "30s"
$env:POPULAR_VUS = "5"
$env:SUMMARY_SLEEP_SECONDS = "0.1"
$env:POPULAR_SLEEP_SECONDS = "0.1"
```

Run a `SUMMARY_VUS=0` popular-only control first, then change only `SUMMARY_VUS` between 5, 10, 20, and 50.

```powershell
$env:SUMMARY_VUS = "5"
k6 run .\load-tests\k6\gemini-thread-saturation.js
```

## Safety Stop Rules

Stop before the next VU step when any of the following occurs:

- the laptop becomes persistently unresponsive
- application or k6 processes keep growing in memory after a run
- repeated connection failures prevent controlled comparison
- 20 VU already shows sustained 20/20 busy threads and severe popular API queueing

Do not run the 50 VU step only to produce a larger request count when saturation is already clear.

## Results

| Summary VUs | Summary requests | Summary RPS | Summary p95 | Summary failure | Popular requests | Popular RPS | Popular p95 | Popular failure | Busy threads peak | Interpretation |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 0 (control) | 0 | 0 | - | - | 758 | 24.55/s | 154.46ms | 0.00% | 6/20 | popular-only control |
| 5 | 50 | 1.55/s | 3.30s | 0.00% | 703 | 21.76/s | 199.10ms | 0.00% | 11/20 | no saturation; summary throughput scales with VU |
| 10 | 100 | 3.09/s | 3.20s | 0.00% | 740 | 22.84/s | 170.39ms | 0.00% | 16/20 | below the configured thread limit |
| 20 | 200 | 6.09/s | 3.23s | 0.00% | 122 | 3.71/s | 2.86s | 0.00% | 20/20 | saturated; popular queued behind summary calls |
| 50 | skipped | - | - | - | - | - | - | - | - | 20 VU already met the safety stop rule |

The 20 VU summary RPS of `6.09/s` was close to the `6.7/s` theoretical upper bound. Increasing from 10 to 20 summary VUs raised throughput from `3.09/s` to `6.09/s`, but it also consumed all configured request threads.

Compared with the popular-only control, the 20 VU mixed run changed the unrelated popular API as follows:

```text
popular p95: 154.46ms -> 2,861.78ms (about 18.5x)
popular RPS: 24.55/s -> 3.71/s (about 84.9% lower)
failure:     0.00% -> 0.00%
```

This was latency and throughput saturation rather than an availability failure.

## Application and Resource Signals

Samples from the end of the 20 VU run showed:

```text
[ArticlesPopular] dbQueryMs=36~42 totalMs=68~76
[AiSummary] aiSummaryMs=3003~3028 totalMs=3030~3245
```

The popular service-side `totalMs` remained tens of milliseconds while its client-side p95 reached 2.86 seconds. Most of the added latency therefore occurred before controller processing, consistent with waiting for an available servlet request thread rather than a slow popular DB query.

Point-in-time resource samples after the saturated run:

| Resource | Signal |
| --- | --- |
| MySQL | about 1.09% CPU, 472.7MiB |
| Redis | about 0.75% CPU, 11.51MiB |
| Spring Boot process | about 510MiB working set |

These snapshots did not show DB/container memory pressure. They are local observations, not production capacity metrics.

## Decision Rule

Async is justified for a follow-up comparison only when the same run shows both:

1. Tomcat busy threads reach the configured maximum while synchronous summary calls occupy the request pool
2. the unrelated `popular` API p95 increases materially despite its DB query remaining inexpensive

Both signals appeared at 20 summary VUs. Summary RPS still increased almost linearly from `1.55/s` to `3.09/s` to `6.09/s`, so this run did not directly observe a throughput plateau beyond the ceiling. It observed 20/20 request-thread occupancy and cross-API queueing; additional load would be required to measure the actual post-ceiling throughput shape. A separate follow-up issue may therefore compare the current synchronous path with a minimal async boundary under the same 20-thread, 3-second mock, and mixed popular-load conditions.

This issue does not claim that async will reduce Gemini's own 3-second latency. The expected improvement is reduced servlet thread occupancy and less cross-API queueing.

## 핵심 결과 요약

```text
Gemini 3초 동기 호출 부하 테스트에서 Tomcat request thread가 20/20 ceiling에 도달하고 일반 조회 API p95가 154ms에서 2.86초로 증가하는 지연 전파를 수치화했습니다.
```
