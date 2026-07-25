# Trend Gemini 요청 오류 정책

## 문제

Trend 기사 요약 경로의 system prompt가 손상된 문자열로 전송되고 있었다. 또한 Gemini 호출에 제한 시간이 없고 외부 서비스의 비정상 응답, timeout, 내부 응답 처리 실패가 모두 500으로 보일 수 있었다.

## 적용 범위

- 요청 언어와 2문장 요약 조건을 포함한 system prompt 복구
- 기사 요약 경로와 동일한 `gemini.timeout-ms` 설정 사용
- Gemini 4xx/5xx를 502 Bad Gateway로 변환
- timeout을 504 Gateway Timeout으로 변환
- 응답 파싱과 내부 처리 실패는 500 Internal Server Error 유지
- Redis cache hit, cache read fallback, cache write 실패 시 응답 유지 정책 보존

## 검증 방법

실제 Gemini API와 운영 Redis는 호출하지 않는다. random port에서 실행한 JDK `HttpServer`가 Gemini 성공, 500, 지연, 비정상 JSON 응답을 재현하고 Mockito Redis fixture가 cache hit와 장애 조건을 재현한다.

요청 본문을 mock server에서 캡처해 기사 본문, 선택 언어, `2 sentences` 조건이 포함되고 손상된 `??` 문자열이 없는지도 검증한다.

## 오류 계약

| 조건 | HTTP 상태 | 의미 |
| --- | ---: | --- |
| 정상 Gemini 응답 | 200 | 요약 반환 및 가능한 경우 Redis 저장 |
| 기사 크롤링 불가 | 400 | 기존 crawler 정책 유지 |
| Gemini 4xx/5xx | 502 | 외부 AI 서비스 응답 실패 |
| Gemini 제한 시간 초과 | 504 | 외부 AI 서비스 응답 지연 |
| 응답 구조 오류·내부 실패 | 500 | 서버 내부 처리 실패 |

## 제외 범위

Trend controller의 동기 처리 방식은 유지한다. 비동기 executor, retry, circuit breaker, queue, Kafka, cache key·TTL 변경은 별도 측정 근거 없이 추가하지 않는다.

## 검증 결과

- mock HTTP·Redis 집중 테스트 7개 통과
- Docker 비의존 14개 클래스 58개 테스트 통과, 실패·오류·skip 0건
- GitHub Backend CI 전체 Gradle test 통과
- AI Reviewer: Blocking 없음, MERGE_READY

로컬 전체 suite는 Docker daemon의 CLI 무응답으로 Testcontainers 실행 전에 중단됐고, 동일 commit의 전체 suite를 GitHub CI에서 검증했다.

연결 거부나 DNS 실패처럼 HTTP 응답 이전에 발생하는 transport 예외는 현재 내부 오류 500으로 처리된다. 이번 이슈는 non-2xx 응답과 timeout 계약을 대상으로 하므로 transport 오류 재분류는 후속 검토 대상으로 남긴다.
