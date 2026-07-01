# AI Reviewer Blocking 확인 스크립트

## 목적

AI Reviewer가 GitHub PR comment에 남긴 검토 결과를 사람이 매번 눈으로 읽고 해석하는 반복 작업을 줄인다.
이 스크립트는 최신 AI Reviewer comment에서 `Blocking`, `Non-blocking`, `결론` 섹션을 추출하고 merge 전 상태 판단을 보조한다.

이 도구는 리뷰 자체를 대체하지 않는다.
Reviewer가 남긴 comment를 읽기 쉬운 상태값으로 요약하는 보조 도구다.

## 사용 방법

```powershell
.\scripts\ai-workflow\check-review-blocking.ps1 -PrNumber 118
```

Windows PowerShell 실행 정책으로 스크립트 실행이 막히는 경우에는 아래처럼 실행한다.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ai-workflow\check-review-blocking.ps1 -PrNumber 118
```

다른 저장소를 명시하려면 `-Repo`를 사용한다.

```powershell
.\scripts\ai-workflow\check-review-blocking.ps1 -PrNumber 118 -Repo "SKU-GlobalTimes/GlobalTimes_BeSide"
```

## 출력 상태

| 상태 | 의미 |
| --- | --- |
| `MERGE_READY` | 최신 AI Reviewer comment의 Blocking 섹션이 `없음` 등 명시적 비어 있음 표현으로 판단된다. |
| `CHANGES_REQUIRED` | Blocking 섹션에 수정이 필요한 항목이 있다. |
| `REVIEW_NOT_FOUND` | AI Reviewer comment를 찾지 못했다. |

## 종료 코드

| Exit code | 의미 |
| --- | --- |
| `0` | Blocking 없음 |
| `1` | Blocking 있음 |
| `2` | Reviewer comment 없음 |

## 전제 조건

- GitHub CLI(`gh`)가 설치되어 있어야 한다.
- `gh auth login`으로 대상 저장소를 읽을 권한이 있어야 한다.
- Reviewer comment는 `### Blocking`, `### Non-blocking`, `### 결론` 섹션을 사용하는 형식을 권장한다.
- 스크립트는 제목 줄이 `AI Reviewer 검토 결과` 형식을 포함하는 최신 comment를 Reviewer comment 후보로 사용한다.
- GitHub comment 앞에 BOM 또는 공백이 붙는 경우를 고려해 제목 앞 숨은 문자를 허용한다.
- Windows PowerShell 5.1의 UTF-8 파싱 차이를 줄이기 위해, 스크립트 내부의 일부 한글 판정은 유니코드 코드포인트 기반으로 처리한다.

## 한계

- 자연어 comment를 완벽하게 해석하지 않는다.
- 최신 AI Reviewer comment를 기준으로 판단하므로, 사람이 최종 merge 승인 전에 결과를 확인해야 한다.
- `Blocking 없음`처럼 명시적으로 작성된 comment에 가장 안정적으로 동작한다.
- `### Blocking` 섹션을 찾지 못하면 안전하게 `REVIEW_NOT_FOUND`로 처리한다.
- PR comment 작성, 코드 수정, merge는 수행하지 않는다.

## 후속 자동화 후보

- Reviewer comment 템플릿 강제화
- Blocking 항목을 GitHub check 또는 label로 반영
- 승인 상태와 결합한 merge gate 구현
- 반복 패턴이 쌓인 뒤 MCP tool로 전환
