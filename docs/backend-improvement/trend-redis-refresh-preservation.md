# Trend Redis 갱신 데이터 보존

## 문제

Trend scheduler는 국가별 새 목록을 수집하고 DTO로 만든 뒤 기존 `trend:{countryCode}` key를 삭제하고 새 JSON을 저장했다.

새 목록이 준비된 뒤에도 `DELETE → SET`을 수행하므로 두 명령 사이에 조회 공백이 생긴다. 직렬화 또는 Redis write가 실패하면 TTL이 남아 있던 기존 정상 목록까지 잃을 수 있다.

## 변경 정책

1. 국가별 새 목록을 JSON으로 먼저 직렬화한다.
2. 직렬화가 성공한 경우에만 Redis `SET`을 한 번 호출한다.
3. 동일 key의 Redis 문자열은 `SET`으로 교체하고 별도 DELETE를 수행하지 않는다.
4. 직렬화나 write가 실패하면 예외는 기존처럼 호출자에게 전달하되, 선행 DELETE 때문에 기존 데이터를 잃지 않는다.

TTL은 기존 `48시간 + 10분`을 유지한다. `news-fetch.enabled` flag, 시작 시 수집, 매시간 scheduler, 국가 목록과 조회 API도 변경하지 않는다.

## 검증 방법

실제 Google Trends와 운영 Redis는 호출하지 않는다. Mockito Redis fixture로 다음 경계를 검증한다.

- 정상 목록은 `trend:KR` key와 기존 TTL을 사용해 `SET` 한 번으로 저장한다.
- 직렬화가 실패하면 `SET`과 `DELETE`가 모두 호출되지 않는다.
- Redis write가 실패하면 DELETE가 호출되지 않고 기존 JSON fixture를 계속 조회한다.

## 제한

클라이언트가 Redis write 결과를 받기 전에 연결이 끊긴 경우에는 서버에 새 값이 반영됐는지 확정할 수 없다. 다만 선행 DELETE를 제거했으므로 결과는 기존 값 또는 완성된 새 값이며, 의도적으로 빈 key를 만드는 단계는 없다.

Redis 자료구조 변경, Lua/MULTI, 분산 락, retry, 국가별 batch 실패 격리, scheduler 병렬화, queue와 Kafka는 이번 범위에 포함하지 않는다.

## 검증 결과

- 정상 교체, 직렬화 실패, Redis write 실패 집중 테스트 3개 통과
- Docker 비의존 15개 클래스 61개 테스트 통과, 실패·오류·skip 0건
- GitHub Backend CI 전체 Gradle test 통과
- AI Reviewer: Blocking 없음, MERGE_READY

write 실패 검증은 mock Redis로 기존 JSON 반환과 DELETE 미호출을 확인한 것이다. 실제 Redis 프로세스 또는 네트워크 장애를 재현한 결과로 해석하지 않는다. 이번 변경의 보장 범위는 애플리케이션이 의도적으로 기존 key를 먼저 삭제하지 않고 완성된 JSON을 SET 한 번으로 전달한다는 점이다.
