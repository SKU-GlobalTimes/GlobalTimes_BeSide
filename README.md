# 깃모지 설명
| 아이콘   | 코드        | 설명                                  | 원문                             |
|----------|-------------|---------------------------------------|----------------------------------|
| 🎨       | `:art:`     | 코드의 구조/형태 개선                   | Improve structure / format of the code. |
| ⚡️       | `:zap:`     | 성능 개선                              | Improve performance.              |
| 🔥       | `:fire:`    | 코드/파일 삭제                         | Remove code or files.            |
| 🐛       | `:bug:`     | 버그 수정                              | Fix a bug.                        |
| 🚑       | `:ambulance:` | 긴급 수정                          | Critical hotfix.                  |
| ✨       | `:sparkles:` | 새 기능                                | Introduce new features.           |
| 💄       | `:lipstick:` | UI/스타일 파일 추가/수정               | Add or update the UI and style files. |
| 🎉       | `:tada:`    | 프로젝트 시작                          | Begin a project.                  |
|🚀        | `:rocket:`    | CI/CD                         | Deploying stuff                 |
| ✅       | `:white_check_mark:` | 테스트 추가/수정                  | Add or update tests.              |
| 💚       | `:green_heart:` | CI 빌드 수정                         | Fix CI Build.                     |
| ♻️       | `:recycle:` | 코드 리팩토링                           | Refactor code.                    |
| 🔨       | `:hammer:`  | 개발 스크립트 추가/수정                | Add or update development scripts. |
| 🔀       | `:twisted_rightwards_arrows:` | 브랜치 합병                   | Merge branches.                  |

---

## 🧪 로컬 실행 (개발용)
로컬 개발 시, **앱(Spring Boot)은 로컬에서 실행**하고  
**MySQL / Redis는 Docker 컨테이너로만 실행**하는 구성을 사용합니다.

### 0) .env 설정 (필수)
프로젝트 루트에 `.env` 파일을 생성하고, `.env.example`을 복사/수정해서 사용합니다.

예시:

```env
MYSQL_ROOT_PASSWORD=localpw
MYSQL_DATABASE=globaltimes

LOCAL_MYSQL_DATABASE=globaltimes
LOCAL_MYSQL_USER=root
LOCAL_MYSQL_PASSWORD=localpw

NEWS_API_KEY=your_news_api_key
OPENAI_API_KEY=your_openai_api_key
GOOGLE_API_KEY=your_google_api_key
```

`application.yml` 에서 `spring.config.import: "optional:file:.env[.properties]"` 설정을 통해  
Spring Boot가 `.env` 값을 자동으로 읽어옵니다.

### 1) MySQL/Redis만 Docker로 실행 (추천)
프로젝트 루트의 `docker-compose.dev.yml`로 **의존 서비스(MySQL, Redis)만** 올립니다.  
앱 서버(Spring Boot)는 로컬에서 `gradlew bootRun`으로 실행합니다.

컨테이너(MySQL)에서 사용하는 값:
- `MYSQL_ROOT_PASSWORD` (`.env`에서 읽음)
- `MYSQL_DATABASE` (`.env`에서 읽음)

#### 실행/종료

```bash
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps
docker compose -f docker-compose.dev.yml down
```

> 데이터를 완전히 초기화하려면 `docker compose -f docker-compose.dev.yml down -v`를 사용하세요(볼륨 삭제).

### 2) Spring Boot 로컬 실행
`src/main/resources/application.yml`은 **로컬 DB/Redis 및 외부 API**를  
`.env`에 정의된 아래 값들로 참조합니다.

- `LOCAL_MYSQL_DATABASE`
- `LOCAL_MYSQL_USER`
- `LOCAL_MYSQL_PASSWORD`
- `NEWS_API_KEY`
- `OPENAI_API_KEY`
- `GOOGLE_API_KEY`

PowerShell 기준 실행 예시는 아래와 같습니다.

```powershell
.\gradlew.bat bootRun
```

git branch flow 

git switch develop
git pull
git switch -c "chore/#새번호-news-fetch-metrics"

이후 생성된 branch 에서 처음 push 하는 경우 upstream 이 없기에, 
git push -u origin HEAD 

-> git push 로 작업 이어가기. 



### 3) 초기 적재(@PostConstruct) 데이터 확인
- 애플리케이션 실행 로그에서 `NewsApiService` 관련 `[초기 적재]`, `[스케줄링]`, `[초기화]` 로그를 확인합니다.
- MySQL 컨테이너 접속 후 `article` 등 주요 테이블 row 수를 조회하면 실제 적재 건수를 확인할 수 있습니다.
# 2025 Asia Impact Hackathon - GlobalTimes 

<img width="1257" height="400" alt="Image" src="https://github.com/user-attachments/assets/9c05e9eb-def0-4a6e-8d2d-77d58ed240e7" />  


[dchallenge_개요](https://www.dchallenge.org/hackathons/hackathons-2025) 

`2025 Asia Impact Hackathon` :  디지털 기술과 AI 기술을 활용하여 공공 과제를 해결하는 것에 주 목적을 둔다.


### 주요 발제

> "동일한 사건이더라도, 각국의 이해관계에 따라 기사들은 다르게 작성된다."  
(1) 정보 편향 (격차), (2) 정보 접근성, (3) 관점의 다양성  

[서비스 소개 및 시연 보러가기](https://www.dchallenge.org/hackathon-2025/globaltimes)  
[백엔드 회고록 (velog.io)](https://velog.io/@oyisbe/2025-%EC%95%84%EC%8B%9C%EC%95%84-%EC%9E%84%ED%8C%A9%ED%8A%B8-%ED%95%B4%EC%BB%A4%ED%86%A4-%ED%9A%8C%EA%B3%A0)  

### Backend 팀원 소개 
| <img src="https://github.com/Oyisbe.png" width="100"/><br>컴퓨터공학과<br>[김온유](https://github.com/Oyisbe) | <img src="https://github.com/kimjy0117.png" width="100"/><br>소프트웨어학과<br>[김주영](https://github.com/kimjy0117) |
|:-:|:-:|


## 🧱 프로젝트 개요
개발 기간 : 2025 3/17 ~ 4/13 

전 세계 다양한 국가의 뉴스 데이터를 자동으로 수집하고,  
이를 기반으로 AI가 요약 및 질의응답 기능을 제공하는 글로벌 뉴스 플랫폼입니다.

<img width="1375" height="840" alt="Image" src="https://github.com/user-attachments/assets/8f0e9f71-b623-4b0c-9c60-23be0744b33b" />  

---

## ⚙️ Backend Skills 

| 분류 | 기술 |
|------|------|
| **Backend** | Spring Boot (Java 17), JPA, Lombok |
| **Infra (CI / CD)** | GitHub Actions, Docker, DockerHub, EC2 |
| **Database** | MySQL, Redis  |
| **AI / API** | OpenAI API, Google RSS & Cloud Translation, News API  |
| **Etc.** | Jsoup (crawling), Swagger |

---

## 🧩 주요 기능



### 1️⃣ **CI/CD 자동 배포 파이프라인**
- GitHub Actions를 이용해 빌드 → Docker 이미지 생성 → DockerHub Push 자동화  
- EC2 서버에서 `docker pull`을 통해 최신 버전으로 자동 배포  

### 2️⃣ **구글 RSS 기반 국가별 실시간 트렌드**
- `Google RSS`를 활용해 국가별 최신 트렌드 키워드 및 주요 기사 실시간 수집
- 각 국가별로 `Feed URL`이 다르게 구성되어, 다양한 지역의 뉴스 자동화 가능

### 3️⃣ **News API 연동**
- `NewsAPI.org`를 사용해 국가별 실시간 헤드라인 수집  
- 키워드 기반 검색 및 필터링 지원  

### 4️⃣ **URL 메타데이터 크롤링 및 요약**
- `Jsoup`을 통해 기사 본문, 이미지, 설명, 제목 등 메타데이터 추출  
- 추출된 본문을 OpenAI API에 전달하여 요약


### 5️⃣ **AI 기반 기사 질의응답**
- 기사 내용을 prompt 기반으로 OpenAI API에 전달하여 질의응답 기능 제공  
- 사용자는 기사별로 “요약 외의 정보”를 자유롭게 질문 가능  

### 6️⃣ **사용자 맞춤 언어 설정**
- 전역적으로 언어 상태를 관리하는 시스템 구축  
- 사용자 요청에 따라 번역 또는 해당 언어 기사만 반환  

### 7️⃣ **스크랩 / 검색 기능**
- 기사 ID 기반으로 LocalStorage에서 스크랩 목록 관리 (비로그인 환경)
- keyword Caching 을 통해 어떠한 언어로 검색해도 동일한 기사를 조회하도록 구현 
---

## 🧠 시스템 아키텍처
<img width="876" height="577" alt="Image" src="https://github.com/user-attachments/assets/592241b7-2c04-486f-8239-d817e6ecbbf9" />

