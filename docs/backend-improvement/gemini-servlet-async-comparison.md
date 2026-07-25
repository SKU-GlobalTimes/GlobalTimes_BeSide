# Gemini Servlet async 전후 비교

## 한국어 학습 안내

이 문서는 느린 Gemini 작업을 전용 executor로 넘겼을 때 servlet thread 점유와 관련 없는 API latency가 어떻게 달라지는지 동기 기준선과 비교한다. 비동기는 외부 API 응답 자체를 단축하지 않으며, 요청을 받아들이는 thread와 오래 걸리는 작업 thread를 격리하는 방식이다.

핵심 개념은 **Spring MVC async**, **전용 executor**, **active worker**, **queue**, **전체 완료시간과 servlet 반환시간의 차이**다. 전용 worker가 모두 사용 중이면 작업은 queue에서 기다리므로 summary 상위 응답시간이 늘어날 수 있지만 Tomcat thread를 계속 붙잡지는 않는다.

아래 결과는 자원 격리의 효과와 동시에 queue 대기가 커질 수 있음을 보여준다. 따라서 async 전환 다음에는 executor가 가득 찼을 때 무한 대기 대신 어떻게 보호할지 확인해야 한다.

## 원본 구현·측정 기록

## Purpose

This document compares the synchronous article summary path from #186 with a minimal Spring MVC asynchronous request boundary.

The goal is not to reduce the controlled 3-second Gemini latency. The goal is to release Tomcat request threads while the blocking summary orchestration runs on a dedicated bounded executor, then measure whether an unrelated `popular` API remains responsive.

## Overengineering Guardrail

The implementation deliberately stops before a full reactive or messaging architecture.

Chosen:

- Spring MVC `WebAsyncTask`
- dedicated `ThreadPoolTaskExecutor`
- fixed and configurable worker/queue limits
- 503 response for executor rejection or MVC async timeout
- Tomcat and executor metrics in the same k6 run

Not chosen:

- end-to-end WebFlux/Reactor conversion
- Kafka or another message queue
- retry or circuit breaker
- API job ID/polling contract
- SSE/ask/Trend path changes

The current JPA and WebClient `.block()` work still occupies an `ai-summary-*` worker. This is Servlet thread isolation, not non-blocking I/O.

## Implementation

The summary controller now returns:

```text
WebAsyncTask<ResponseEntity<ApiResponse>>
```

The existing summary orchestration is executed by `aiSummaryExecutor`:

| Setting | Default |
| --- | ---: |
| core pool size | 20 |
| max pool size | 20 |
| queue capacity | 100 |
| MVC async timeout | 15,000ms |

All values can be overridden with environment variables. The pool and queue are bounded so async traffic cannot create unbounded threads.

The k6 script records:

- summary/popular request count, RPS, p95, and failure rate
- Tomcat busy/current/max threads
- async executor active/queued/pool size

## Environment

| Item | Value |
| --- | --- |
| Application | one local Spring Boot instance |
| Tomcat max threads | 20, local override |
| Async executor | 20 workers, queue capacity 100 |
| Gemini | local mock, fixed 3-second delay |
| DB/cache | Docker MySQL and Redis |
| Summary persistence | disabled for the run |
| Popular load | fixed 5 VU |
| Duration | 30 seconds plus graceful stop |

The test article had its summary backed up and cleared for each run so every summary request used the mock Gemini path.

## Results

### 20 Summary VUs

| Mode | Summary RPS | Summary p95 | Popular RPS | Popular p95 | Failure | Tomcat busy peak | Executor active/queued |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| synchronous (#186) | 6.09/s | 3.23s | 3.71/s | 2.86s | 0.00% | 20/20 | - |
| Servlet async | 6.12/s | 3.39s | 23.38/s | 160.99ms | 0.00% | 6/20 | 20/0 |

Interpretation:

- summary throughput and latency remained effectively similar
- popular p95 decreased by about 94.4%
- popular RPS increased by about 6.3 times
- Tomcat busy peak decreased from 20 to 6
- all 20 async workers were active, with no queue at this VU level

### 50 Summary VUs

| Mode | Summary requests | Summary RPS | Summary p95 | Popular requests | Popular RPS | Popular p95 | Failure | Tomcat busy peak | Executor active/queued |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| synchronous | 220 | 6.28/s | 8.98s | 32 | 0.91/s | 5.97s | 0.00% | 20/20 | - |
| Servlet async | 220 | 6.29/s | 9.15s | 784 | 22.40/s | 172.98ms | 0.00% | 8/20 | 20/30 |

Both 50 VU runs ended with 10 interrupted iterations during the 5-second graceful-stop window.

Interpretation:

- synchronous 50 VU directly showed the post-ceiling shape: summary RPS stayed near 6.28/s while p95 increased to 8.98 seconds
- async did not improve summary throughput or queueing latency; p95 remained about 9.15 seconds
- popular p95 decreased by about 97.1%
- popular RPS increased by about 24.5 times
- Tomcat busy peak decreased from 20 to 8
- executor active reached 20 and queue reached 30, proving that the bottleneck was isolated rather than removed

## Error and Cleanup Verification

- mock Gemini 500 still returned backend 502 through async dispatch
- a local 1-second MVC async timeout with a 3-second mock returned backend 503 in about 1.81 seconds
- executor rejection is mapped to the same 503 response
- unexpected internal exceptions remain backend 500
- full Gradle tests passed after the async and error-handler changes
- article 7366 summary was restored to its original 420-character value
- temporary backup table and local backend/mock processes were removed

## Decision

The minimal Servlet async boundary is justified for the current MVC/JPA architecture because it protects unrelated request traffic without changing the summary response contract.

The result must be described accurately:

- improved: Tomcat request-thread availability and cross-API isolation
- unchanged: Gemini latency and summary capacity
- moved: blocking summary work to a bounded executor
- remaining limit: executor queue growth and 503 overload policy

Kafka or full reactive conversion is not justified by this result alone. Reconsider them only when durable background processing, job recovery, much higher event volume, or the need to remove blocking worker occupancy is demonstrated.

## 핵심 결과 요약

```text
Gemini 동기 호출을 전용 bounded executor 기반 Servlet async로 격리해 50 VU 혼합 부하에서 일반 조회 API p95를 5.97초에서 173ms로 낮추고 Tomcat busy thread peak를 20에서 8로 개선했습니다.
```
