# Gemini executor overload 보호 기준선

## 한국어 학습 안내

이 문서는 전용 Gemini executor의 worker와 bounded queue가 모두 찼을 때 새 요청을 빠르게 거절하고, 이미 수락한 작업과 다른 API를 보호하는 동작을 검증한다. queue를 제한하지 않으면 서버가 처리할 수 없는 요청을 메모리에 계속 쌓아 timeout과 장애 복구 시간을 키울 수 있다.

핵심 개념은 **bounded queue**, **rejection policy**, **backpressure**, **fast failure**, **recovery**다. HTTP 거절은 서버 결함을 숨기는 것이 아니라 현재 처리 용량을 넘었다는 계약이다. 거절된 요청과 수락된 요청의 상위 응답시간을 분리해야 빠른 보호와 실제 작업 대기를 혼동하지 않는다.

queue 크기와 worker 수는 영구적인 정답이 아니다. 배포 instance의 CPU·memory, 외부 API 동시성 제한, 목표 latency와 실패 허용률을 같은 환경에서 측정해 조정해야 한다. 아래 기록은 작은 queue로 포화·거절·회복을 재현한 로컬 기준선이다.

## 원본 측정 기록

## Purpose

#188 proved that a dedicated bounded executor protects unrelated APIs from slow Gemini summary calls. Its 50 VU run reached 20 active workers and 30 queued tasks, but did not fill the default queue capacity of 100 or exercise real HTTP rejection.

This baseline reduces only the local executor capacity and verifies:

- the first observable saturation boundary
- actual HTTP 503 dispatch after task rejection
- the configured queue upper bound
- `popular` API behavior during rejection
- summary recovery after overload ends

It is a controlled single-instance protection test, not an EC2 capacity result or a production traffic claim.

## Overengineering guardrail

Chosen:

- existing Spring MVC async implementation
- local environment overrides only
- existing mock Gemini server and k6 scenario
- explicit 200, 503, and unexpected status metrics
- low VU saturation through a reduced executor

Not chosen:

- changing the default 20 workers / queue 100
- rate limiter or automatic retry
- circuit breaker
- Kafka or WebFlux
- distributed k6 or EC2 extrapolation

The reduced executor makes the same bounded rejection behavior reproducible on an 8GB laptop without generating hundreds of concurrent VUs.

## Environment

| Item | Value |
| --- | --- |
| Application | one local Spring Boot instance |
| Tomcat max threads | 20 |
| Async executor | core/max 5, queue capacity 10 |
| MVC async timeout | 15 seconds |
| Gemini | local mock, fixed 3-second delay, HTTP 200 |
| Summary persistence | disabled |
| DB/cache | Docker MySQL and Redis |
| Popular load | 5 VU |
| Metrics load | 1 VU |
| Saturation duration | 30 seconds plus 5-second graceful stop |
| Recovery duration | 15 seconds plus graceful stop |

Article 7366 had crawled content and its existing 420-character summary was backed up and cleared. The summary was restored and the temporary table was removed after all runs.

## k6 metric change

The summary scenario now separates:

- `summary_responses_200`
- `summary_responses_503`
- `summary_responses_unexpected`
- `summary_accepted_duration`
- `summary_rejected_duration`
- `summary_unexpected_failures`

`ALLOW_SUMMARY_503=true` treats only 200 and 503 as expected during an overload run. Any 500, 502, 504, or other status fails the strict `summary_unexpected_failures: rate==0` threshold.

The pass/fail latency threshold uses `summary_accepted_duration: p(95)<15000`, which contains only HTTP 200 responses. The aggregate summary request duration remains visible for analysis but is not a latency guard because fast 503 responses would hide slow accepted responses.

k6 still counts HTTP 503 in its built-in `http_req_failed` metric. The overload decision therefore uses the explicit custom status metrics rather than treating built-in HTTP failures as unexpected backend errors.

## Results

| Summary VU | Requests | HTTP 200 | HTTP 503 | Accepted p95 | Rejected p95 | Queue peak | Popular p95 | Tomcat busy peak |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 51 | 51 | 0 | 6.75s | - | 5/10 | 371.10ms | 13/20 |
| 15 | 55 | 55 | 0 | 9.35s | - | 10/10 | 163.49ms | 9/20 |
| 20 | 1,213 | 55 | 1,158 | 9.41s | 49.55ms | 10/10 | 254.51ms | 11/20 |
| 30 | 3,775 | 55 | 3,720 | 9.34s | 34.25ms | 10/10 | 256.03ms | 20/20 |
| recovery 5 | 25 | 25 | 0 | 3.68s | - | 0/10 | 575.41ms | 7/20 |

Unexpected summary status, popular failure, and metrics failure were 0 in every run.

### Boundary interpretation

- 10 VU used 5 workers and queued at most 5 tasks.
- 15 VU filled all 5 workers and all 10 queue slots without rejection.
- 20 VU was the first tested stage above the 15-task executor capacity and produced real HTTP 503 responses.
- 20 and 30 VU both kept the queue at its configured maximum of 10.
- Accepted throughput stayed at 55 requests per 35-second total run, about 1.57 RPS, close to the controlled `5 workers / 3 seconds` service limit after application overhead.
- Increasing VU above the executor capacity did not increase accepted throughput. It increased only fast rejection volume.

The 95.46% and 98.54% rejection ratios at 20 and 30 VU are specific to this closed-model script. A VU receiving a fast 503 sleeps only 0.1 seconds and immediately retries, creating a rejection storm. These ratios must not be presented as production traffic failure rates or arrival-rate capacity.

### Queue and timeout interpretation

With five workers and a 3-second service time:

```text
theoretical service capacity = 5 / 3 seconds = about 1.67 RPS
```

A queue of 10 represents approximately two additional 5-task batches. The 15 VU accepted p95 of 9.35 seconds is consistent with two queue batches plus the 3-second execution time and local application overhead.

General configuration guidance:

```text
workers ~= target RPS * external-call latency / target utilization
queue ~= service throughput * allowed queue-wait time
queue wait + external-call timeout < MVC async timeout
```

Increasing queue size does not increase throughput. Sustained load requires additional permitted external-call concurrency or more instances, subject to the total Gemini quota. If external capacity cannot increase, bounded rejection and client backoff are safer than an unbounded queue.

## Recovery

Immediately after the 30 VU run, Actuator reported executor active 0 and queued 0. A subsequent 5 VU run returned 25/25 HTTP 200 responses, no 503, queue peak 0, and summary p95 3.68 seconds.

This verifies functional recovery of the executor and summary path. The recovery `popular` p95 of 575.41ms is local-run variance and is not claimed as an improvement; it remained failure-free and below the existing 5-second guard threshold.

## Decision

- Keep the default executor settings at 20 workers and queue 100 in this issue.
- Use 503 as an intentional temporary-overload response, not a server defect.
- Require clients to apply retry delay/backoff; immediate retry can amplify rejection traffic.
- Size each future instance from measured arrival RPS, external-call latency, acceptable wait, and total Gemini quota.
- Re-run the same script in a fixed EC2/container environment before making production capacity claims.

## 핵심 결과 요약

```text
Gemini 외부 호출용 bounded executor를 5 workers/queue 10으로 축소한 포화 실험에서 20 VU부터 초과 요청을 p95 34~50ms의 503으로 차단하고 queue 상한을 유지했으며, 동시 popular API p95를 약 255ms로 격리하고 부하 종료 후 25/25 정상 응답 회복을 검증했습니다.
```
