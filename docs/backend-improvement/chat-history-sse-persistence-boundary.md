# SSE 완료 후 채팅 이력 저장 실패 경계

## 문제

로그인 사용자의 Gemini 질의는 답변 stream이 완료된 뒤 질문과 답변을 MySQL `chat_history`에 저장한다.

기존 `ChatHistoryService.save()`는 `@Transactional` 메서드 내부에서 모든 예외를 잡고 로그만 남겼다.

```text
transaction proxy 시작
→ ChatHistoryService.save() 본문
   → 내부 try-catch
→ JPA flush 및 transaction commit
```

사용자·기사 누락처럼 메서드 본문에서 발생한 예외는 내부 catch가 숨긴다. 반면 JPA flush 또는 commit이 대상 메서드 반환 후 실패하면 내부 catch 범위 밖이므로 호출자까지 예외가 전달될 수 있다.

이 저장은 Gemini stream의 `doOnComplete` callback에서 실행된다. 사용자가 답변을 이미 전달받은 뒤 이력 저장 실패가 callback 오류가 되면 답변 성공과 부가 저장 실패의 경계가 불명확해진다.

## 정책

| 작업 | 정책 |
| --- | --- |
| Gemini 답변 생성·SSE 전달 | 주 기능 |
| 로그인 채팅 이력 저장 | 답변 완료 후 best-effort 부가 기능 |
| 이력 저장 실패 | 현재 답변 stream은 정상 완료하고 구조화 로그 기록 |
| 다음 대화 context | 저장에 실패한 turn은 포함되지 않음 |
| 익명 Redis 이력 저장 | 기존 경로 유지 |

이 정책은 저장 실패를 성공으로 위장한다는 의미가 아니다. 사용자에게 이미 전달한 답변의 성공 상태와, 이후 MySQL에 남기는 부가 이력의 성공 상태를 분리한다.

## Transaction 경계 변경

### 기존

```text
AiSseService
→ transactional save()
   → try
      → repository.save()
   → catch
→ commit
```

메서드 내부 catch는 commit 전체를 감싸지 못하고 누락 리소스 예외를 호출자에게 전달하지 않았다. 또한 transactional 메서드 내부의 저장 성공 로그는 실제 commit 성공 전에 기록될 수 있었다.

### 변경

```text
AiSseService try
→ transactional proxy
   → ChatHistoryService.save()
   → flush
   → commit
→ 정상 반환 후 성공 로그
catch
→ userId, articleId, 예외 유형과 stack trace 기록
→ SseEmitter.complete()
```

`ChatHistoryService.save()`는 저장 책임만 수행하고 예외를 숨기지 않는다. `AiSseService`는 proxy 호출 전체를 감싸므로 사용자·기사 조회, INSERT, flush, commit에서 전달된 실패를 같은 경계에서 처리한다.

## 검증

### Mock SSE 완료 callback

외부 Gemini API를 호출하지 않고 mock `WebClient`와 mock `SseEmitter`를 사용했다.

| 시나리오 | 결과 |
| --- | --- |
| 로그인 이력 저장 성공 | save 호출 후 emitter complete |
| 이력 저장 예외 | 예외를 stream error로 전파하지 않고 emitter complete |
| 저장 실패 로그 | user ID, article ID, `DataIntegrityViolationException` 기록 |
| 익명 요청 | 기존 Redis `appendTurn` 호출 유지 |
| mock Gemini SSE `data:` 완료 | 누적 답변을 로그인 이력 save에 전달 |

### MySQL 8 Testcontainers

| 시나리오 | 결과 |
| --- | --- |
| 정상 사용자·기사 | chat_history 1건 저장 |
| 존재하지 않는 사용자 | `IllegalArgumentException`이 호출자까지 전달 |
| 존재하지 않는 기사 | `IllegalArgumentException`이 호출자까지 전달 |
| TEXT 한도를 넘는 70,000자 답변 | DB 쓰기 예외가 호출자까지 전달되고 0건 rollback |

mock SSE 테스트 4개와 MySQL 저장 테스트 3개가 통과했다. MySQL·Redis Testcontainers를 포함한 전체 87개 테스트도 실패 없이 통과했다.

## 비용과 범위

- 실제 Gemini 호출은 0회다.
- 실제 Cloud Translation 호출도 없다.
- schema, API 응답 형식, SSE event 형식, 익명 Redis 저장은 변경하지 않는다.
- retry, outbox, Kafka, 별도 저장 executor·queue는 추가하지 않는다.

## 해석 경계

- 이력 저장 실패 시 해당 turn은 다음 Gemini context에 포함되지 않는다.
- 현재 정책은 답변 가용성을 우선하는 best-effort 결정이며, 이력 보존을 결제·감사 데이터처럼 반드시 보장하는 정책이 아니다.
- 저장 실패가 실제 운영에서 반복 관찰될 때 retry 또는 outbox를 별도 근거와 함께 검토한다.
