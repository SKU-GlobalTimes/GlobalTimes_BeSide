# 2025 Asia Impact Hackathon - GlobalTimes 

<img width="1257" height="400" alt="Image" src="https://github.com/user-attachments/assets/9c05e9eb-def0-4a6e-8d2d-77d58ed240e7" />
`2025 Asia Impact Hackathon` :  디지털 기술과 AI 기술을 활용하여 공공 과제를 해결한다. 

> "동일한 사건이더라도, 각국의 이해관계에 따라 기사들은 다르게 작성된다."  

(1) 정보 격차, (2) 정보 접근성, (3) 관점의 다양성
해결을 위한 아이디어를 제시하였습니다. 

[dchallenge_개요](https://www.dchallenge.org/hackathons/hackathons-2025)  
[서비스 소개 및 시연 영상](https://www.youtube.com/watch?v=nT2tRXkR7pI)

## 팀원 소개
### Backend
|컴퓨터공학과 김온유 [@Oyisbe](https://github.com/Oyisbe)|소프트웨어학과 김주영 [@kimjy0117](https://github.com/kimjy0117) | 
|:-:|:-:|

---
## 🧱 프로젝트 개요
개발 기간 : 2025 3/17 ~ 4/13 

전 세계 다양한 국가의 뉴스 데이터를 자동으로 수집하고,  
이를 기반으로 AI가 요약 및 질의응답 기능을 제공하는 글로벌 뉴스 플랫폼입니다.




<img width="1375" height="840" alt="Image" src="https://github.com/user-attachments/assets/8f0e9f71-b623-4b0c-9c60-23be0744b33b" />  

[백엔드 회고록 (velog.io)](https://velog.io/@oyisbe/2025-%EC%95%84%EC%8B%9C%EC%95%84-%EC%9E%84%ED%8C%A9%ED%8A%B8-%ED%95%B4%EC%BB%A4%ED%86%A4-%ED%9A%8C%EA%B3%A0)  


**핵심 목표**
- 국가별 최신 뉴스 실시간 수집
- AI 기반 뉴스 요약 및 질의응답
- 사용자 맞춤형 언어 지원
- 지속적 배포(CI/CD) 자동화 및 컨테이너 운영

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

