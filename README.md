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

### 3) 초기 적재(@PostConstruct) 데이터 확인
- 애플리케이션 실행 로그에서 `NewsApiService` 관련 `[초기 적재]`, `[스케줄링]`, `[초기화]` 로그를 확인합니다.
- MySQL 컨테이너 접속 후 `article` 등 주요 테이블 row 수를 조회하면 실제 적재 건수를 확인할 수 있습니다.
