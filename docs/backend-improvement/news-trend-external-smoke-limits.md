# News API·Google Trends 소규모 실호출 설정

## 목적

최종 수동 Full-stack External Smoke에서 News API, 언론사 RSS, Google Trends를 실제 호출하되 개발용 quota와 외부 서버에 불필요한 부하를 주지 않도록 실행 범위를 설정으로 강제한다. 수집기는 기존처럼 기본 비활성이며, 아래 값은 수동 Smoke 프로세스에만 주입한다.

## 최소 호출 설정

```text
NEWS_FETCH_ENABLED=false

NEWS_API_FETCH_ENABLED=true
NEWS_API_PAGE_SIZE=1
NEWS_API_MAX_REQUESTS_PER_RUN=1

RSS_FETCH_ENABLED=true
RSS_FETCH_COUNTRIES=kr
RSS_MAX_FEEDS_PER_RUN=1
RSS_MAX_ARTICLES_PER_FEED=1

TREND_FETCH_ENABLED=true
TREND_FETCH_COUNTRIES=KR
TREND_MAX_ITEMS_PER_COUNTRY=1
```

- News API는 첫 번째 `US/general` headline 요청 1회만 실행하고 기사 후보를 최대 1건 요청한다.
- RSS는 선택 국가의 첫 feed에서 기사 후보 1건만 처리한다. 실제 원문 확보 가능 여부는 후속 preflight가 별도로 확인한다.
- Google Trends RSS는 KR 1개 국가만 요청하고 Redis `trend:KR`에 최대 1건을 저장한다.
- 기본값은 기존 News API page size 100·최대 30회, Trend 26개국·국가별 6건을 유지한다.

## 최종 E2E 판정 경계

후속 Frontend 수동 E2E는 실제 공급자 수집과 대표 로그인 사용자 사이클을 다음 순서로 확인한다.

```text
News API·RSS MySQL 적재 / Google Trends Redis 적재
→ 실제 Translation 검색
→ 실제 기사 원문 crawling
→ 실제 Gemini 요약
→ fixture JWT 로그인
→ 실제 Gemini SSE 질의와 MySQL 대화 저장·재조회
→ 스크랩 저장·재조회
```

Perspectives는 외부 기사 구성에 따라 결과가 달라지므로 HTTP 200, 번역·FULLTEXT·국가별 구성, Redis cache 생성 같은 기술 경로만 자동 판정한다. 반환 기사 수, 국가 수, 동일 사건 여부와 의미적 관련성은 합격 조건이 아니며 관찰 결과로만 기록한다. 이 검증은 Backend Issue #110의 수집·검색·관련성 한계를 해결했다는 근거가 아니다.

실제 Google OAuth, 자동 retry, VU 부하, News API·Trend 반복 호출, ranking·schema·queue·Kafka 변경은 포함하지 않는다.

## 구현 검증

- 설정 기본값·override, News API 1회 요청과 `pageSize=1`, Trend KR 1회·결과 1건, 수집기 비활성 시 외부 호출 0회를 집중 테스트 11개로 확인했다.
- Testcontainers를 포함한 Backend 전체 테스트 112개가 통과했다.
- 이 단계에서는 실제 외부 API를 호출하지 않는다. 실제 호출과 성공 결과는 후속 Frontend 수동 External Smoke에서 기록한다.
