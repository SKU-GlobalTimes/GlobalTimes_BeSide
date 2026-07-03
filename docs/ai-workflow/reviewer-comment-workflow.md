# AI Reviewer PR Comment Workflow

## 목적

Implementer 세션과 Reviewer 세션을 분리해 운영할 때 사용자가 리뷰 결과를 매번 복사해 전달하는 부담을 줄인다.
GitHub PR comment를 AI 리뷰 결과, 반영 계획, 반영 완료 내역의 단일 기록 저장소로 사용한다.

## AS-IS

- Implementer 세션이 PR을 생성한 뒤, 사용자가 Reviewer 세션에 PR 링크와 검토 기준을 전달한다.
- Reviewer 세션은 코드 수정 없이 리뷰 결과를 생성한다.
- 사용자가 Reviewer 결과를 다시 Implementer 세션으로 복사해 전달한다.
- Implementer 세션이 리뷰 결과를 PR comment로 옮기고 수정 계획을 제안한다.

이 방식은 안전하지만 세션 사이에서 사용자가 수동으로 중개해야 하므로 누락과 반복 작업이 발생한다.

## TO-BE

- Reviewer 세션이 PR diff를 검토한 뒤 GitHub PR comment에 직접 리뷰 결과를 남긴다.
- Implementer 세션은 PR comment를 조회해 Blocking과 Non-blocking을 분리하고 수정 계획을 제안한다.
- 사용자는 수정 계획을 승인하거나 후속 이슈 분리를 지시한다.
- 승인된 수정만 같은 브랜치에 추가 커밋으로 반영한다.

## 역할과 권한

| 역할 | 책임 | 허용 작업 | 금지 작업 |
| --- | --- | --- | --- |
| Implementer | 구현, 테스트, PR 생성, 승인된 리뷰 반영 | 코드 수정, 테스트 실행, 커밋, push, PR 생성·갱신 | 승인되지 않은 범위 변경 |
| Reviewer | PR diff 검토, Blocking/Non-blocking 분류, PR comment 작성 | PR 조회, diff 검토, comment 작성 | 코드 수정, 커밋, push, merge |
| User | 승인 게이트, 범위 판단, 최종 merge 승인 | 리뷰 반영 승인, 후속 이슈 분리 결정, merge 승인 | 없음 |

Reviewer에게 PR comment 작성 권한은 줄 수 있지만, 코드 수정 권한은 주지 않는다.

## 기본 흐름

```text
Issue 생성
→ Implementer 작업 브랜치 생성
→ 조사 및 계획
→ 사용자 승인
→ 구현 및 테스트
→ PR 생성
→ Reviewer 세션이 PR diff 검토
→ Reviewer가 PR comment 작성
→ Implementer가 PR comment 조회
→ 수정 계획 제안
→ 사용자 승인
→ 추가 커밋 push
→ 필요 시 Reviewer 재검토
→ Blocking 없음 확인
→ 사용자 승인 후 merge
→ merge 결과와 다음 상태를 WORK_PROGRESS.md에 기록
```

## Reviewer comment 형식

```md
## AI Reviewer 검토 결과

### Blocking
- merge 전에 반드시 해결해야 하는 문제

### Non-blocking
- 이번 PR에서 선택적으로 반영하거나 후속 이슈로 분리할 개선점

### 범위/구조 확인
- Issue 범위 준수 여부
- API 응답, DB, 설정, 외부 동작 변경 여부

### 결론
- Blocking 있음 / Blocking 없음
- 코드 수정 없이 리뷰만 수행했는지 여부
```

## Blocking / Non-blocking 처리 기준

### Blocking

아래 항목은 merge 전에 같은 PR에서 해결한다.

- 테스트 실패 또는 컴파일 오류
- 보안, 인증, 권한, 민감 정보 노출 위험
- Issue 요구사항과 충돌하는 구현
- API 응답 구조의 의도치 않은 변경
- DB 데이터 손상 가능성
- 문서에 명시한 운영 원칙과 코드의 충돌

### Non-blocking

아래 항목은 같은 PR에서 반영하거나 후속 이슈로 분리할 수 있다.

- 로그 필드 추가 개선
- requestId, tracing, metrics 등 관측성 고도화
- 테스트 케이스 확장
- 네이밍, 구조, 중복 제거
- 운영 정책 문서화

반영하지 않는 Non-blocking 의견은 PR comment 또는 PR 본문에 사유를 남긴다.

## Reviewer 세션 표준 프롬프트

```text
너는 Reviewer Agent다.
코드 수정은 하지 말고 PR diff만 검토해줘.

Repo: SKU-GlobalTimes/GlobalTimes_BeSide
PR: <PR URL>
Issue: #<ISSUE_NUMBER>

검토 관점:
1. Issue 범위를 벗어난 변경이 있는가?
2. 테스트 또는 검증 계획에 누락이 있는가?
3. 보안, 민감 정보 로그, DB 영향, API 응답 변경 위험이 있는가?
4. Blocking / Non-blocking을 구분해줘.
5. 검토 결과를 GitHub PR comment로 남겨줘.

제약:
- 코드 직접 수정 금지
- 커밋, push, merge 금지
- 리뷰 결과만 PR comment로 기록
```

Implementer는 새 Issue/PR을 생성한 뒤 사용자에게 Reviewer 세션에 전달할 검토 요청 예시를 함께 안내한다.
검토 요청 예시는 PR URL, Issue 번호, 이번 PR의 핵심 검토 관점, Reviewer 제약, `## AI Reviewer 검토 결과` comment 작성 요구를 포함한다.

## Implementer 세션 표준 프롬프트

```text
PR #<PR_NUMBER>의 AI Reviewer comment를 읽고 수정 계획을 세워줘.

원칙:
- Blocking은 같은 PR에서 수정한다.
- Non-blocking은 같은 PR 반영 / 후속 이슈 분리 / 미반영 사유 기록 중 하나로 분류한다.
- 코드 변경 전 사용자 승인을 먼저 받는다.
```

## MERGE_READY 이후 처리

`scripts/ai-workflow/check-review-blocking.ps1` 또는 동일한 수동 확인으로 최신 `## AI Reviewer 검토 결과` comment의 결정이 `MERGE_READY`이고 Blocking이 없음을 확인하면, Implementer는 아래 기준에 따라 merge를 진행한다.

1. PR이 승인된 Issue 범위 안에 머문다.
2. Reviewer comment 이후 PR diff에 추가 변경이 없다. 추가 변경이 있다면 최신 diff 기준으로 Reviewer 재검토를 받아야 한다.
3. PR이 GitHub 기준 mergeable 상태다.
4. 사용자가 해당 PR에 대해 명시적으로 merge 진행을 승인했다.

위 조건이 충족되면 Implementer는 별도 대기 없이 PR을 merge할 수 있다.
merge 후에는 로컬 `develop`을 최신화하고 `WORK_PROGRESS.md`에 PR 상태, Reviewer 결정, merge 시각, 다음 작업 상태를 기록한다.

## 기록 기준

- Reviewer 검토 결과는 PR comment에 남긴다.
- Implementer 반영 계획과 반영 완료 내역도 PR comment에 남긴다.
- 최종 Reviewer 확인 결과는 “Blocking 없음” comment로 남긴다.
- merge는 `MERGE_READY`와 사용자 승인 기준을 충족한 뒤 수행한다.
- 새 Issue/PR 생성 후에는 Reviewer Agent에게 전달할 검토 요청 예시를 사용자에게 안내한다.
- merge 완료 후에는 `WORK_PROGRESS.md`를 기준 문서로 갱신한다.

## 후속 자동화 후보

- PR comment에서 Blocking만 추출하는 스크립트
- Reviewer comment 템플릿 자동 생성
- Issue 범위와 PR 변경 파일 비교
- 위험 파일 변경 감지
- 승인 상태와 tool 실행 내역을 저장하는 audit log
- 반복 패턴이 충분히 쌓인 뒤 MCP 서버로 도구화

PR comment에서 Blocking 여부를 확인하는 보조 스크립트는
[AI Reviewer Blocking 확인 스크립트](./review-blocking-check-script.md)를 참고한다.
