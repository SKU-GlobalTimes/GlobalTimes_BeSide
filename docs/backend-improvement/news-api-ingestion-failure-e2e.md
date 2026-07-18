# News API 수집 실패 격리 E2E

## 목적

#196은 News API 응답 내부와 기존 DB URL을 애플리케이션에서 걸러 재실행 중복을 줄였고, #210은 generated SHA-256 UNIQUE로 동일 URL 저장을 DB에서 최종 거부한다. 그러나 기존 단위 테스트는 DTO 처리와 mock repository까지만 검증해 외부 HTTP 실패가 실제 MySQL 저장 경로에 미치는 영향은 확인하지 않았다.

이 테스트는 일부 News API 요청이 5xx 또는 timeout으로 실패해도 다른 정상 응답의 기사가 저장되고, 같은 수집을 재실행해도 중복이 생기지 않는지를 자동 검증한다.

## 테스트 경계

```text
JDK local mock HTTP server
→ RestTemplate
→ News API JSON 역직렬화
→ 유효성·응답 내부 중복·기존 URL 검사
→ Source/Article JPA 저장
→ Flyway V3가 적용된 MySQL 8 Testcontainers
```

mock server가 외부 News API 응답만 대신하며 나머지 수집 경로는 실제 애플리케이션 코드를 사용한다. 따라서 이 결과는 `mock upstream 기반 수집 pipeline E2E`이며 실제 News API의 인증, quota, 운영 네트워크, 실제 기사 품질까지 검증한 운영형 E2E로 표현하지 않는다.

## 시나리오와 결과

한 번의 headline 수집에서 네 category를 순서대로 요청한다.

| category | mock 조건 | 기대 결과 |
| --- | --- | --- |
| general | HTTP 200, 정상 기사 1건 | 저장 |
| business | HTTP 500 | 오류 로그 후 다음 요청 계속 |
| science | client read timeout보다 늦은 응답 | 오류 로그 후 다음 요청 계속 |
| technology | HTTP 200, 정상 기사 1건 | 저장 |

같은 시나리오를 두 번 실행한 결과는 다음과 같다.

- mock 요청: category별 2회, 총 8회
- 첫 실행 후 정상 기사: 2건
- 두 번째 실행 후 정상 기사: 2건 유지
- `url_hash` 중복 그룹: 0건
- 500과 timeout은 각각 오류 로그로 구분되고 technology 요청까지 계속 실행
- 전체 Gradle 테스트: 54개 통과, 약 2분 50초

## 실행 조건

- Docker Desktop/daemon은 MySQL Testcontainers 실행을 위해 필요하다.
- 기존 Docker Hub MySQL 컨테이너와 8080 백엔드 서버는 필요하지 않다.
- mock HTTP server는 테스트 프로세스가 임의 포트를 할당하고 종료 시 정리한다.
- MySQL Testcontainer도 테스트가 자동 생성·삭제하므로 compose DB 데이터를 사용하지 않는다.
- `news-fetch.enabled=false`를 유지하며 실제 API key와 quota를 소비하지 않는다.

## 운영 동작

`spring.newsapi.base-url`의 기본값은 기존 `https://newsapi.org`다. 테스트만 임의 포트의 local mock URL을 주입하며 운영 기본 endpoint, 스케줄, request limit, delay는 변경하지 않는다.

## 범위 경계

실제 News API/RSS 운영형 E2E, retry, circuit breaker, Redis 분산 락, Kafka, 멀티 인스턴스 수집은 포함하지 않는다. 반복 장애율이나 복구 요구가 확인되기 전에는 현재 요청 단위 실패 격리보다 복잡한 복구 기술을 추가하지 않는다.
