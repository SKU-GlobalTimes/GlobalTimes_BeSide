# 2025 Asia Impact Hackathon - GlobalTimes

<img width="1257" height="400" alt="Image" src="https://github.com/user-attachments/assets/9c05e9eb-def0-4a6e-8d2d-77d58ed240e7" />

[dchallenge\_개요](https://www.dchallenge.org/hackathons/hackathons-2025)

`2025 Asia Impact Hackathon` : 디지털 기술과 AI 기술을 활용하여 공공 과제를 해결하는 것에 주 목적을 둔다.

### 주요 발제

> "동일한 사건이더라도, 각국의 이해관계에 따라 기사들은 다르게 작성된다."  
> (1) 정보 편향 (격차), (2) 정보 접근성, (3) 관점의 다양성

[서비스 소개 (Dchallenge)](https://www.dchallenge.org/hackathon-2025/globaltimes)

[시연 및 서비스 소개 (Youtube) ](https://www.youtube.com/watch?v=nT2tRXkR7pI)  

[백엔드 회고록 (velog.io)](https://velog.io/@oyisbe/2025-%EC%95%84%EC%8B%9C%EC%95%84-%EC%9E%84%ED%8C%A9%ED%8A%B8-%ED%95%B4%EC%BB%A4%ED%86%A4-%ED%9A%8C%EA%B3%A0)

### Backend 팀원 소개

| <img src="https://github.com/Oyisbe.png" width="100"/><br>컴퓨터공학과<br>[김온유](https://github.com/Oyisbe) | <img src="https://github.com/kimjy0117.png" width="100"/><br>소프트웨어학과<br>[김주영](https://github.com/kimjy0117) |
| :-----------------------------------------------------------------------------------------------------------: | :-------------------------------------------------------------------------------------------------------------------: |

---

## 🧱 프로젝트 개요

개발 기간 : 2025 3/17 ~ 4/13

전 세계 다양한 국가의 뉴스 데이터를 자동으로 수집하고,  
이를 기반으로 AI가 요약 및 질의응답 기능을 제공하는 글로벌 뉴스 플랫폼입니다.

<img width="1375" height="840" alt="Image" src="https://github.com/user-attachments/assets/8f0e9f71-b623-4b0c-9c60-23be0744b33b" />

---

## ⚙️ Backend Skills

| 분류                | 기술                                                        |
| ------------------- | ----------------------------------------------------------- |
| **Backend**         | Spring Boot (Java 17), JPA, Lombok                          |
| **Infra / CI**      | GitHub Actions, Docker Compose, Testcontainers              |
| **Legacy Deploy**   | DockerHub, EC2 (해커톤 제출 당시 구성, 현재 비활성)         |
| **Database**        | MySQL, Redis                                                |
| **인증**            | Google OAuth2, JWT (jjwt)                                   |
| **AI / API**        | Google Gemini API, Google RSS & Cloud Translation, News API |
| **Etc.**            | Jsoup (crawling), Swagger                                   |

---

## 🧩 주요 기능

> 해커톤 제출 버전 기준 커밋: `43639bf`

### 1️⃣ CI 및 배포 이력

- **현재 CI**: `develop` 대상 PR과 push마다 GitHub Actions에서 JDK 17 기반 전체 Gradle 테스트 실행
- MySQL·Redis 의존 회귀 테스트는 Testcontainers로 임시 컨테이너를 생성해 검증
- **해커톤 제출 당시 배포**: Docker 이미지 생성 → DockerHub Push → EC2 `docker pull` 자동 배포
- 현재는 대상 EC2가 삭제되어 legacy 자동 배포는 비활성 상태이며, 활성 workflow는 Backend CI만 유지

### 2️⃣ RSS 기반 국가별 다국어 뉴스 수집

- 초기에는 `Google RSS`로 국가별 트렌드 키워드 수집에만 활용

- **News API 무료 플랜의 한계** (미국(`us`) 단일 국가만 실질적 데이터 반환)로 인해, 다국어·다국가 수집을 RSS 파이프라인으로 전환·확장

- 6시간 주기 스케줄링으로 자동 수집 및 DB 적재

**수집 대상 언론사 및 국가**

| 국가          | 언론사                   | 언어        | 제공 카테고리                              |
| ------------- | ------------------------ | ----------- | ------------------------------------------ |
| 🇬🇧 영국       | BBC                      | 영어        | 일반, 경제, 기술, 과학, 건강, 문화, 스포츠 |
| 🇰🇷 한국       | 연합뉴스                 | 한국어      | 일반, 정치, 기술, 건강, 문화, 스포츠       |
| 🇯🇵 일본       | NHK                      | 일본어      | 일반, 경제, 과학, 스포츠                   |
| 🇫🇷 프랑스     | France24                 | 프랑스어    | 일반, 경제, 스포츠                         |
| 🇩🇪 독일       | Deutsche Welle           | 독일어/영어 | 일반, 경제, 과학, 스포츠, 문화             |
| 🇸🇦 중동       | BBC Arabic               | 아랍어      | 일반, 경제, 스포츠, 기술                   |
| 🇨🇳 중국       | South China Morning Post | 영어        | 일반, 경제, 기술, 스포츠                   |
| 🇪🇸 스페인어권 | BBC Mundo                | 스페인어    | 일반, 경제, 과학, 스포츠                   |


> **⚠️ 근본적 한계**  
> 본 서비스의 핵심 의의는 _"동일한 사건을 각국의 시선으로 비교"_ 하는 것이었으나,  
> 각 RSS 피드는 **갱신 주기·기사 선택 기준이 언론사마다 상이**하여  
> 동일 이슈에 대한 국가별 기사를 정확히 매핑하는 것이 구조적으로 어렵다는 한계가 존재.  
> 국가·카테고리별 데이터셋을 최대한 폭넓게 수집하여 제공하도록 수정정.


### 3️⃣ News API 연동

- `NewsAPI.org`를 사용해 실시간 헤드라인 및 키워드 기반 기사 수집
- 키워드 기반 검색 및 필터링 지원

> **무료 플랜 한계로 인한 운영 전략**  
> News API 무료 플랜은 `us` 단일 국가만 실질적인 데이터를 반환하므로,  
> 다국가 수집은 RSS 파이프라인으로 위임하고 News API는 미국 기사 수집에 집중.

- **`top-headlines` 엔드포인트**: 미국(`us`) 기준 카테고리별 헤드라인 수집 (general, business, entertainment, health, science, sports, technology)
- **`everything` 엔드포인트**: 미국만 유지, 타 국가는 대표 도메인을 직접 지정하여 심층 기사 수집 (RSS 로 전환 -> NEWS.API 는 Free Plan 기반 타 국가 데이터 제공 X)



| 도메인            | 언론사                     |
| ----------------- | -------------------------- |
| `wsj.com`         | Wall Street Journal (미국) |
| `bbc.co.uk`       | BBC (영국)                 |
| `techcrunch.com`  | TechCrunch (미국)          |
| `theguardian.com` | The Guardian (영국)        |
| `reuters.com`     | Reuters (글로벌)           |
| `aljazeera.com`   | Al Jazeera (중동)          |



### 4️⃣ 국가별 시각 비교 — Perspectives

> 서비스의 **핵심 의의**: _"동일한 사건이더라도, 각국의 이해관계에 따라 기사들은 다르게 작성된다."_  
> 특정 기사를 읽을 때, 같은 이슈를 다루는 다른 나라 언론사의 기사를 나란히 보여주는 기능.

** 구현된 동작 방식 (키워드 기반 FULLTEXT 매칭)**


1. 기사 제목을 공백·구두점 기준으로 단어 분리
2. 2글자 이하 단어 제거
3. 사전 정의된 영어 Stop Words 제거 (`the`, `is`, `for`, `and` 등 70여 개)
4. 중복 제거 후 앞에서 최대 4개 키워드 추출
5. 앞 2개는 필수(`+keyword`), 나머지는 선택 조건으로 MySQL `FULLTEXT BOOLEAN MODE` 검색
6. 비영어 기사는 Google Translation API로 제목을 영어로 번역 후 키워드 재추출 → 영어권 기사까지 탐색
7. 국가별로 그룹핑하여 최대 3개 기사 반환, 결과는 Redis에 1시간 캐싱



> **⚠️ 구조적 한계**
>
> **1. 다국어 키워드 매칭 문제**  
> 현재 RSS 파이프라인 확장으로 한국어·일본어·프랑스어·아랍어 등 다국어 기사가 DB에 적재되어 있으나,  
> FULLTEXT 매칭은 **동일 언어의 동일 단어**가 등장해야 히트됨.  
> 완전한 해결을 위해서는 추출한 키워드를 DB에 저장된 모든 국가의 언어(8개 언어)로 각각 번역해 탐색해야 하지만,  
> 번역 API 비용·응답 속도 문제로 현실적으로 어렵다.  
> → **현재 구현**: 비영어 기사의 제목을 **영어로 1회 번역**하여 영미권 기사까지만 크로스 탐색.  
> 한국어 기사에서 perspectives를 조회하면 영어 기사는 찾아주지만, 일본어·아랍어 기사는 여전히 탐색되지 않는다.
>
> 
> **2. 해커톤 제출 / 리팩토링 현황 비교**  
> 해커톤 제출 버전(`43639bf`)에서는 News API 무료 플랜 한계로 사실상 **영어 데이터만 수집**되어 있었기 때문에  
> 다국어 매칭 문제 자체가 존재하지 않음. (당시, 단일 언어였기에 근본적인 관점 비교가 불가능)
> RSS 파이프라인 확장 -> 다국어 데이터셋을 보유하며 이슈가 생기게 됨.
>
>
> **3. 이슈 단위 매핑의 어려움**  
> 표현이 다르거나 국내 한정 이슈일 경우 perspectives가 비어있는 것은 자연스러운 동작.  
> 오히려 *글로벌 이슈일수록 더 풍부한 비교가 가능*한 것이 서비스 특성.  
> 진정한 "이슈 단위" 매핑은 의미적 유사도 기반 검색 엔진 도입이 필요.



**➕ 향후 개선 가능**

- **Elasticsearch** 도입 시 의미적 유사도 기반 검색으로 커버리지 확대
- **벡터 DB 기반 유사 기사 탐색**: 기사 본문을 임베딩 벡터로 변환 후 코사인 유사도로 매칭  
  → `pgvector`(PostgreSQL 확장), `Weaviate`, `Pinecone` 등 벡터 DB 활용 가능
- **MSA 분리 고려**: 기사 수집·저장 / AI 처리 / 검색을 독립 마이크로서비스로 분리하면  
  각 서비스별 언어·프레임워크 최적화 가능 (예: 검색 서비스는 Python + LangChain / FastAPI 조합)

---

### 5️⃣ 사용자 맞춤 언어 설정

- 전역적으로 언어 상태를 관리하는 시스템 구축
- 사용자 요청에 따라 번역 또는 해당 언어 기사만 반환
- _(언어 상태 관리는 FE 전역 상태에서 처리)_

---

> 이하 기능은 해커톤 이후 리팩토링 및 확장 작업 (2026.03 ~)

### 5️⃣ URL 메타데이터 크롤링 및 AI 요약 

- `Jsoup`을 통해 기사 본문, 이미지, 설명, 제목 등 메타데이터 추출
- AI 모델 OpenAI GPT → **Google Gemini (`gemini-2.5-flash`)** 로 마이그레이션
- 최초 요약 후 DB 영속 저장 (이후 재요청 시 캐싱된 결과 반환)

### 6️⃣ AI 기반 기사 질의응답 (SSE 스트리밍) 

- 기사 내용을 기반으로 Gemini API에 전달하여 실시간 스트리밍 질의응답 제공
- AI 모델 OpenAI GPT → **Google Gemini** 로 마이그레이션
- **로그인 유저**: 이전 대화 내역을 슬라이딩 윈도우(최근 10턴) 방식으로 누적 전달 → 맥락 기반 대화
- **비로그인 유저**: 익명 session ID 기준으로 Redis에 최근 대화와 기사 목록을 저장해 제한된 맥락 대화 제공

### 7️⃣ Google OAuth2 + JWT 로그인 

- Google 소셜 로그인으로 간편 인증 (글로벌 서비스 특성에 맞게 Google 단일 제공)
- 로그인 성공 시 JWT 발급 → 이후 모든 인증 필요 API에 Bearer 토큰으로 활용

### 8️⃣ AI 질의 히스토리 저장 및 조회

- 로그인 유저의 질의/응답을 기사별로 DB에 저장
- 기사별 마지막 대화 미리보기 목록 조회 API 제공 (팝업 리스트용)
- 특정 기사의 전체 대화 내역 조회 API 제공 (기사 상세 페이지용)

### 9️⃣ 스크랩 기능 서버 저장 전환 

- **비로그인**: 기존 브라우저 localStorage 기반 스크랩 유지
- **로그인 유저**: 스크랩 데이터를 서버 DB에 영속 저장, 기기 간 동기화 가능
- 스크랩 토글(추가/취소), 내 스크랩 목록 조회, 스크랩 여부 확인 API 제공

---

### 🔟 안정성 개선 및 리팩토링 — 

기능 추가 과정에서 잠재적 장애 요소 개선.

- **RestTemplate 타임아웃 설정**  
  기본 `RestTemplate`은 타임아웃 없이 외부 API 무한 대기 가능  
  → `connectTimeout 3s / readTimeout 5s` 설정 추가

- **news-fetch 플래그 도입**  
  로컬 개발 중 서버 기동 시마다 News API / RSS 수집이 실행되어 API 할당량 낭비  
  → `news-fetch.enabled: false` 플래그로 로컬에서 수집 비활성화 가능

- **중복 기사 사전 필터링**  
  RSS 수집 시 건별 `SELECT`로 중복 확인하던 방식  
  → URL 목록 일괄 조회(`findExistingUrls`) 후 메모리에서 필터링, DB 쿼리 수 감소

- **Perspectives Redis 캐싱**  
  FULLTEXT 검색 + 번역 API 호출이 매 요청마다 발생  
  → 결과를 Redis에 1시간 캐싱하여 응답 속도 개선 및 외부 API 호출 절감

---

## 🔎 백엔드 개선 근거와 학습 경로

해커톤 이후 백엔드 개선은 Issue 단위로 문제를 재현하고, 작은 변경을 적용한 뒤 k6·mock server·Testcontainers·EXPLAIN·Actuator로 검증하는 방식으로 진행했습니다.

| 문서 | 내용 |
| --- | --- |
| [Phase 1 구조·근거 맵](docs/backend-improvement/backend-phase1-evidence-map.md) | 수집, 검색, AI, 채팅, 스크랩, schema·CI의 실제 실행 경로와 관련 코드·Issue·PR·MD를 탑다운으로 연결 |
| [정량 결과 인덱스](docs/backend-improvement/backend-improvement-quantitative-index.md) | 개선 전후 수치, 성능 기준선, 정합성·장애 격리 결과와 해석 한계 |
| [부하 테스트 기준선](docs/backend-improvement/load-test-baseline.md) | VU·arrival-rate·RPS·p95와 시나리오 실행 방법 |
| [로컬 관측 runbook](docs/backend-improvement/local-load-observability-runbook.md) | k6, application latency, Actuator, Docker resource를 같은 구간에서 해석하는 방법 |
| [Flyway schema 기준선](docs/backend-improvement/flyway-schema-baseline.md) | V1~V3 migration과 MySQL Testcontainers 재현 방식 |
| [작업 진척 기록](docs/backend-improvement/WORK_PROGRESS.md) | 전체 Issue/PR 흐름과 의사결정 이력 |

대표적인 검증 결과:

- Gemini 동기 호출을 bounded async executor로 격리해 50 VU에서 동시 popular API p95 `5.97초 → 172.98ms`, RPS `0.91 → 22.40`
- 기사 조회수 lost update를 DB 원자 UPDATE로 변경해 20개 성공 요청의 실제 증가량 `2 → 20`
- 채팅·스크랩 목록 projection query로 SQL `101 → 1`, `201 → 1`
- 스크랩 toggle 동시 요청 20건의 성공 `2 → 20`, 실패 `18 → 0`
- 수집 1,000건 신규 저장과 중복 재실행을 MySQL에서 측정하고 재실행 추가 저장 0건 확인

수치는 로컬·mock·fixture 조건의 결과이며 운영 SLA나 최대 처리량으로 일반화하지 않습니다. 각 수치의 실행 조건과 제한은 연결된 원본 문서를 기준으로 확인합니다.

---

## 🧠 시스템 아키텍처

<img width="876" height="577" alt="Image" src="https://github.com/user-attachments/assets/592241b7-2c04-486f-8239-d817e6ecbbf9" />

---

## 🚀 로컬 실행 가이드

### 0) .env 설정 (필수)

프로젝트 루트에 `.env` 파일을 생성하고, `.env.example`을 복사/수정해서 사용.

```env
# DB
MYSQL_ROOT_PASSWORD=localpw
MYSQL_DATABASE=globaltimes
LOCAL_MYSQL_DATABASE=globaltimes
LOCAL_MYSQL_USER=root
LOCAL_MYSQL_PASSWORD=localpw

# API Keys
NEWS_API_KEY=your_news_api_key
GEMINI_API_KEY=your_gemini_api_key
GOOGLE_API_KEY=your_google_api_key

# OAuth2 (Google)
GOOGLE_OAUTH_CLIENT_ID=your_google_client_id
GOOGLE_OAUTH_CLIENT_SECRET=your_google_client_secret
OAUTH2_REDIRECT_URI=http://localhost:5173/oauth/callback

# JWT
JWT_SECRET=minimum-32-characters-random-string
```

### 1) MySQL / Redis Docker 실행

```bash
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps     # 상태 확인
docker compose -f docker-compose.dev.yml down   # 종료
```

> 데이터 완전 초기화: `docker compose -f docker-compose.dev.yml down -v`

### 2) Spring Boot 실행

```powershell
.\gradlew.bat bootRun
```

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OAuth2 로그인 테스트: `http://localhost:8080/oauth2/authorization/google`

---

## 🌿 Git Branch 전략

```
main        ← 배포용 (직접 push 금지)
  └── develop     ← 통합 브랜치 (PR merge 대상)
        └── feat/#이슈번호-작업명   ← 기능 단위 브랜치
```

### 브랜치 생성 흐름

```bash
git switch develop
git pull
git switch -c feat/#이슈번호-작업명

# 첫 push 시 upstream 설정
git push -u origin HEAD

# 이후
git push
```
