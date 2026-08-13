# 실제 외부 API Smoke E2E 실행 경계

## 목적

기존 Full Stack E2E는 Frontend, Backend, MySQL, Redis의 연결을 결정적으로 검증하기 위해 외부 Gemini와 번역 응답을 대체한다. 별도의 수동 External Smoke는 실제 RSS 기사 적재부터 검색, 번역, 원문 크롤링, Gemini 요약과 질의, 사용자별 저장까지 도메인 경로가 이어지는지 소수 호출로 확인한다.

## 수집 제어

기존 `NEWS_FETCH_ENABLED`는 세 공급자의 기본값으로 유지한다. 실제 Smoke에서는 다음처럼 공급자와 RSS 범위를 별도로 제한한다.

```text
NEWS_FETCH_ENABLED=false
NEWS_API_FETCH_ENABLED=false
RSS_FETCH_ENABLED=true
TREND_FETCH_ENABLED=false
RSS_FETCH_COUNTRIES=kr
RSS_MAX_FEEDS_PER_RUN=1
RSS_MAX_ARTICLES_PER_FEED=1
```

상한이 `0`이면 기존 전체 목록을 사용한다. 실제 호출용 환경에서는 국가, feed 수, feed별 기사 후보 수를 모두 명시한다.

## 후속 Playwright 시나리오

1. 격리된 MySQL과 Redis를 실행한다.
2. 비영어 RSS 한 곳에서 소수 기사를 실제 수집하고 MySQL 적재를 확인한다.
3. 수집된 기사에서 검색어를 선택해 실제 번역과 FULLTEXT 검색 경로를 확인한다.
4. 기사 상세에서 원문을 크롤링하고 Gemini 요약을 한 번 생성해 저장한다.
5. 익명 질의를 한 번 실행해 Redis 저장과 재조회를 확인한다.
6. fixture JWT 로그인 질의를 한 번 실행해 MySQL 저장과 재조회를 확인한다.
7. 서버와 일회성 컨테이너를 종료한다.

목표 상한은 Translation 1회, Gemini 요약 1회, 익명 질의 1회, 로그인 질의 1회다. 실제 Google OAuth 로그인은 자동화하지 않는다.

## 검증 한계

실시간 RSS에는 특정 사건의 국가별 기사가 동시에 존재한다는 보장이 없다. 따라서 Perspectives는 기준 기사 조회, 키워드 추출, 필요 시 번역, FULLTEXT 조회, 국가별 응답 구성과 Redis 캐시라는 기술 경로만 Smoke 검증한다. 결과 기사 수, 국가 수, 의미적 관련성은 자동 합격 조건으로 사용하지 않는다.

실제 Gemini 문구와 기사 제목도 실행 시점마다 달라질 수 있다. 정확한 문자열 대신 HTTP/SSE 완료, 비어 있지 않은 응답, 저장 및 재조회 여부를 검증한다. 이 검증은 장기 troubleshooting Issue #110의 검색·수집·관련성 한계를 해결했다는 근거로 사용하지 않는다.
