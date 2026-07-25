# 단일 instance TPS 기준선

## 한국어 학습 안내

이 문서는 하나의 로컬 Spring Boot instance가 인기 기사 조회를 어느 정도 처리하는지 점진적으로 부하를 높여 관찰한다. 목표는 운영 TPS를 보증하는 것이 아니라 처리량 증가가 멈추고 상위 응답시간이 커지는 지점을 찾아 다음 조사 대상을 정하는 것이다.

핵심 개념은 **throughput plateau**, **latency 증가**, **단일 instance capacity**, **고정 환경의 필요성**이다. VU를 늘렸는데 RPS가 거의 늘지 않고 응답시간만 증가하면 요청이 내부 자원을 기다리는 포화 신호다. 다만 로컬 OS와 IDE, Docker 자원이 고정되지 않았으므로 이 수치를 Pod 수로 단순 곱해 운영 용량을 주장할 수 없다.

아래 표는 동일 endpoint와 데이터셋에서 VU 단계별 request, RPS, 상위 응답시간과 MySQL·Redis·app 자원을 함께 기록한다. 이 기준선이 이후 mixed arrival-rate와 포화 경계 측정의 출발점이다.

## 원본 측정 기록

## Purpose

This document records a local single-instance capacity baseline for DB-backed article read APIs.

The goal is not to prove cloud-scale throughput.
The goal is to establish a portfolio-friendly baseline:

- one Spring Boot application instance
- local Docker MySQL and Redis
- local k6 load generator
- p95, failure rate, RPS/TPS, application latency logs, and resource signals in the same run window

This issue does not change production code, API responses, repository queries, DB schema/indexes, Redis/cache policy, async processing, or Kubernetes settings.

## 핵심 결과 요약

```text
로컬 Docker 기반 단일 인스턴스 환경에서 주요 기사 조회 API의 점진 부하 테스트를 수행해 20 VU 구간 약 87 RPS까지 안정적으로 처리되고, 50 VU에서는 RPS가 증가하지 않은 채 p95가 456ms로 상승하는 포화 신호를 확인했습니다.
```

Shorter resume keyword version:

```text
단일 인스턴스 조회 API TPS 한계/포화 구간 측정
```

## Environment

| Item | Value |
| --- | --- |
| Date | 2026-07-10 |
| Run ID | `20260710-1530-single-instance-popular` |
| Application | local `bootRun`, one Spring Boot instance on `localhost:8080` |
| DB/cache | Docker MySQL and Redis |
| Load generator | local k6 |
| Dataset note | article rows around `9,853`; recent 30-day `popular` filter returned `2,066` rows during this run |
| Log file | `.codex-tmp/load-runs/20260710-1530-single-instance-popular/application.log` |

Runtime artifacts under `.codex-tmp/` are local-only and must not be committed.

## Scope Decision

This issue intentionally measures before implementing.

Not included:

- DB index changes
- query changes
- Redis/local cache changes
- connection pool tuning
- async processing
- multi-process or Kubernetes Pod scaling
- cloud load testing

Reasoning:

- On an 8GB local machine, very high VU counts can saturate the laptop before the application or DB bottleneck is clear.
- #176 already showed that `Using filesort` alone did not justify an immediate DB index.
- #178 added a runbook for correlating k6, application logs, and local resource signals.
- Therefore #180 focuses on single-instance capacity shape: stable range, saturation signal, and next investigation criteria.

## Scenario

The existing `load-tests/k6/articles-read-high-load.js` script was reused.
`popular` received the main load, while other article-read scenarios were kept at `1 VU / 1s` to keep basic script coverage with minimal noise.

Shared settings:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLES_SIZE = "20"
$env:LATEST_VUS = "1"
$env:CURSOR_VUS = "1"
$env:EXPLORE_VUS = "1"
$env:DETAIL_VUS = "1"
$env:LATEST_DURATION = "1s"
$env:CURSOR_DURATION = "1s"
$env:EXPLORE_DURATION = "1s"
$env:DETAIL_DURATION = "1s"
$env:POPULAR_PAGES = "0,1,5"
$env:SLEEP_SECONDS = "0.1"
```

Popular VU steps:

```text
5 VUs / 30s
10 VUs / 30s
20 VUs / 30s
50 VUs / 30s
```

The planned 100 VU step was skipped for this run because 50 VU already showed a clear local saturation signal: RPS did not increase from the 20 VU run while p95 rose sharply.

## Results

| Popular VUs | Duration | Requests | RPS | Popular avg | Popular p95 | Failure rate |
| ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 5 | 30s | 970 | 24.19/s | 57.61ms | 105.05ms | 0.00% |
| 10 | 30s | 1,841 | 45.91/s | 65.16ms | 94.62ms | 0.00% |
| 20 | 30s | 3,505 | 87.29/s | 71.84ms | 105.48ms | 0.00% |
| 50 | 30s | 3,474 | 86.15/s | 334.86ms | 456.72ms | 0.00% |

`Duration` is the active `popular` scenario duration.
`Requests` and `RPS` are from the k6 summary and use the whole local run window, including scenario start offsets and short noise scenarios.
Because every measured step used the same script shape, the RPS values are comparable for saturation judgment, but they are not a pure 30-second popular-only TPS calculation.

Interpretation:

- 5 -> 10 -> 20 VUs increased throughput from `24.19/s` to `87.29/s` while p95 stayed around `95ms ~ 105ms`.
- 20 -> 50 VUs did not increase throughput (`87.29/s` -> `86.15/s`) but raised popular p95 from `105.48ms` to `456.72ms`.
- This is a local single-instance saturation signal: extra concurrency queued or waited somewhere instead of increasing completed requests.
- Since failure rate stayed `0.00%`, this was latency saturation rather than availability failure.

## Resource Signals

Resource samples were taken with `docker stats --no-stream` before/after runs.
These are point-in-time samples, not peak metrics.

| Moment | MySQL CPU | MySQL memory | Redis CPU | Redis memory | App process memory |
| --- | ---: | ---: | ---: | ---: | ---: |
| Before run | 4.39% | 392.2MiB | 0.59% | 8.18MiB | 291MiB |
| After 5 VU | 0.69% | 407.3MiB | 0.65% | 8.05MiB | 332MiB |
| After 20 VU | 0.76% | 413.0MiB | 0.62% | 8.05MiB | 335MiB |
| After 50 VU | 0.66% | 414.3MiB | 0.53% | 8.05MiB | 338MiB |

Observed implication:

- MySQL and Redis memory stayed stable.
- `docker stats --no-stream` did not capture a sustained DB/container saturation after the runs.
- The 50 VU plateau should not be interpreted as a DB index issue without peak app/thread/CPU metrics or repeated application `dbQueryMs` aggregation.

## Application Log Signal

`[ArticlesPopular]` logs were captured in the same run window.

Sample signal near the 50 VU run end:

```text
[ArticlesPopular] ... dbQueryMs=37 totalMs=70
[ArticlesPopular] ... dbQueryMs=34 totalMs=48
[ArticlesPopular] ... dbQueryMs=49 totalMs=71
[ArticlesPopular] ... dbQueryMs=51 totalMs=70
[ArticlesPopular] ... dbQueryMs=19 totalMs=34
```

Interpretation:

- Sampled service-side `totalMs` values were far lower than k6 p95 `456.72ms` in the 50 VU run.
- This suggests part of the latency may be outside the repository call itself, such as servlet thread scheduling, request queueing, local machine contention, k6/application sharing the same host, or response transfer timing.
- A DB index change is not justified from this run alone.

## Capacity Statement

For this local 8GB development environment:

```text
The `popular` read path was stable up to the 20 VU step at about 87 RPS with p95 around 105ms and 0.00% failure rate.
At 50 VUs, throughput stayed around 86 RPS while p95 rose to 456ms, so the practical single-instance saturation signal appeared between 20 and 50 VUs.
```

This is not a production SLA.
It is a local single-instance baseline that can support follow-up reasoning:

- if app-side `dbQueryMs` also rises, open a query/index issue
- if `dbQueryMs` stays low but k6 p95 rises, inspect application threads, local CPU contention, response serialization, connection pool, or k6 host contention
- if stable single-instance RPS is needed for scale-out estimation, use the 20 VU result as a conservative local baseline before assuming multi-Pod scaling

## Follow-up Candidates

- `[OBS] 로컬 부하 테스트 중 애플리케이션 thread/CPU peak 지표 캡처 보강`
- `[PERF] latest/cursor 단일 인스턴스 TPS 비교`
- `[PERF] Gemini mock latency 기반 외부 호출 병목 기준선 수립`
