# Perspectives 매칭 표본 snapshot

## 목적

이 문서는 Perspectives 매칭 품질 기준선의 첫 로컬 snapshot을 기록한다.
`perspectives-matching-quality-baseline.md`를 따르며, 더 무거운 검색 기술을 도입하기 전에 로컬 개발 MySQL 데이터에서 대표 표본을 찾는다.

이 snapshot은 API 동작, DB schema, Redis 정책 또는 검색 로직을 변경하지 않는다.
매칭이 적은 사례 일부는 FULLTEXT뿐 아니라 출처 범위나 feed 최신성에서 비롯될 수 있으므로 `perspectives-source-coverage-limit-log.md`와 함께 해석한다.

## 환경

| 항목 | 값 |
| --- | --- |
| 날짜 | 2026-07-03 |
| 데이터셋 | 로컬 개발 MySQL |
| 기사 행 | 9853 |
| 쿼리 경로 | `ArticleRepository.findPerspectives` 형태의 MySQL FULLTEXT query |
| 외부 번역 API | 호출하지 않음 |
| Redis cache | 이 snapshot에서 사용하지 않음 |

민감한 로컬 `.env` 값과 외부 API 응답은 의도적으로 제외했다.

## 데이터셋 분포

로컬 기사 상위 그룹:

| Country | Language | Category | Count |
| --- | --- | --- | ---: |
| kr | ko | general | 1088 |
| global | en | general | 742 |
| kr | ko | entertainment | 590 |
| kr | ko | business | 504 |
| kr | ko | sports | 417 |
| cn | zh | general | 394 |
| us | en | sports | 319 |
| us | en | health | 305 |
| gb | en | sports | 284 |
| us | en | technology | 282 |
| de | de | general | 272 |
| jp | ja | sports | 248 |

영어와 비영어 후보 동작을 확인하기에 충분하지만 로컬 개발 표본이므로 운영 품질 근거로 해석하면 안 된다.
또한 모든 국가와 출처가 동일 이슈를 게시하고 수집될 기회를 비슷하게 가졌음을 증명하지 않는다.

## 방법

각 표본에 다음 쿼리를 사용했다.

```sql
SELECT *
FROM article
WHERE article_id != :baseArticleId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50;
```

`KeywordExtractor`와 같은 넓은 규칙으로 제목에서 키워드를 수동 도출했다.

- 공백과 문장부호로 분리
- 길이가 2 이하인 token 제거
- 설정된 영어 stop word 제거
- 중복 없는 token 최대 4개 유지
- 앞의 2개 token에 `+`를 붙여 필수화

비영어 표본은 원문 키워드 동작만 기록한다.
전체 API 경로에서는 비영어 plain keyword를 영어로 번역할 수 있지만 외부 API 호출이 필요하므로 별도 측정으로 남겼다.

## 표본 요약

| Base article ID | Country | Language | Category | Keyword used | Match count | Country/language spread | Initial classification | Notes |
| --- | --- | --- | --- | --- | ---: | --- | --- | --- |
| 8146 | gb | en | general | `+First +round Iran talks` | 25 | us/en 14, us/null 5, cn/zh 4, gb/en 2 | `weak_match` | 이란 외교 기사지만 필수어 `First`, `round`가 스포츠 등 관련 없는 결과를 만든다. |
| 8148 | gb | en | general | `+Russian +troop build threatens` | 0 | none | `no_match` | Ukraine/Donbas 주제가 추출 키워드로 결과를 얻지 못한다. |
| 8149 | gb | en | general | `+BTS +fans losing thousands` | 8 | cn/zh 4, gb/en 2, us/en 1, fr/fr 1 | `good_match` | 여러 국가와 언어에서 BTS 관련 결과가 나타난다. |
| 8147 | gb | en | general | `+Trump +backed political outsider` | 4 | cn/zh 2, us/null 1, gb/en 1 | `partial_match` | 직접 관련된 Colombia 선거 기사와 넓은 Trump/Iran 잡음이 함께 있다. |
| 9638 | cn | zh | general | `+China +defends role global` | 0 | none | `no_match` | 영어 제목이 있지만 필수어가 너무 구체적이어서 현재 FULLTEXT recall이 없다. |
| 8468 | kr | ko | general | `+AI +반도체 붐에` | 0 | none | `no_match` | 한국어 FULLTEXT 동작과 짧은 token 처리 때문에 원문 매칭이 유효하지 않다. |
| 8461 | kr | ko | general | `+이란 +원유시장 복귀 합의에` | 0 | none | `no_match` | 한국어 원문 키워드로 관련 Iran/oil 결과가 나오지 않는다. |
| 9440 | fr | fr | general | `+Entre +Meloni Trump divorce` | 0 | none | `no_match` | 일반적인 프랑스어 token이 필수어가 되어 관련 Trump/Meloni 결과를 막는다. |

## 반환 예시

### 8146: `+First +round Iran talks`

기준 기사는 US-Iran 회담을 다루지만 최신 반환 행에는 관련 없는 스포츠나 넓은 주제 결과가 포함된다.

| Returned article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 8413 | gb | en | LPGA play-off |
| 7845 | us | en | USMNT round of 32 price |
| 9744 | cn | zh | China AI rivalry funding |
| 7667 | cn | zh | New Zealand players facing Iran |

매칭 결과가 있어도 이슈 품질이 약한 유용한 사례다.

### 8149: `+BTS +fans losing thousands`

여러 국가와 언어에서 BTS 관련 결과가 반환된다.

| Returned article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 6014 | cn | zh | BTS fans and Arirang credits |
| 4523 | gb | en | BTS comeback show turnout |
| 4935 | cn | zh | BTS comeback crowd and stock sell-off |
| 3008 | us | en | BTS comeback concert |
| 2104 | fr | fr | BTS album comeback |

현재 FULLTEXT가 일관된 주제를 찾는 긍정 표본이다.

### 8147: `+Trump +backed political outsider`

관련 결과와 잡음이 소수 섞여 반환된다.

| Returned article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 9658 | cn | zh | Trump-backed Colombia presidential election |
| 4941 | cn | zh | Trump and Iran threat |
| 3258 | gb | en | Trump-backed television merger |
| 778 | us | null | Trump allies and Iran |

향후 순위 또는 entity filter를 평가하기 좋은 partial-match 표본이다.

## 관찰 결과

- 현재 FULLTEXT 매칭 수만으로 품질을 판단할 수 없다.
- `First`, `round`처럼 앞쪽 필수 token이 너무 일반적일 수 있다.
- 영어 표본도 제목 앞 token이 핵심 entity가 아니면 약한 결과가 나올 수 있다.
- BTS는 추출 키워드가 강한 entity이므로 좋은 긍정 표본이다.
- 이 로컬 쿼리 snapshot에서 한국어와 프랑스어 원문 표본은 자주 결과를 반환하지 않는다.
- 일부 `language='zh'` 행은 영어 제목을 가지므로 `language` field만으로 검색 text 언어를 완전히 설명할 수 없다.
- 이 snapshot의 수동 키워드 예시는 Java 정책을 대체하지 않는 측정 보조 자료다.
  `KeywordExtractorTest`가 token 정책 변경 전의 Java 동작을 고정한다.
- 회귀 테스트에서 붙어 있는 `+first+second` BOOLEAN MODE 출력과 Unicode ellipsis로 연결된 한국어 token 같은 형식·tokenization 특성이 드러났다.
  붙어 있는 BOOLEAN MODE 형식은 #150에서 정규화했으며 token 정책 변경은 별도 후속 작업이다.

## Java 정책 후속 결과

위 snapshot 표는 최초 로컬 측정 키워드와 매칭 수를 보존한다.
#150과 #152 이후 Java `KeywordExtractor` 정책은 가장 약한 일반 token 표본의 생성 키워드를 다음과 같이 변경한다.

| Base article ID | Previous keyword | #152 Java keyword | Reason |
| --- | --- | --- | --- |
| 8146 | `+First +round Iran talks` | `+Iran +talks ends encouraging` | 표본 기반 일반 token인 `First`, `round`를 제거한다. |
| 9440 | `+Entre +Meloni Trump divorce` | `+Meloni +Trump divorce italienne` | 표본 기반 일반 token인 `Entre`를 제거한다. |

이 절은 생성 키워드 변경만 기록한다.
DB 수준 FULLTEXT 결과 품질은 코드 변경 merge 후 별도로 측정해야 하므로 최초 매칭 수를 대체하지 않는다.
일반 token filter로 추출 후보가 모두 제거되면 원래 제목으로 fallback하지 않고 `extract()`와 `extractPlain()` 모두 빈 문자열을 반환한다.
#152 이후 DB 수준 FULLTEXT 결과 변화는 `perspectives-fulltext-generic-token-filter-result.md`에 기록한다.

## 후속 후보

1. 외부 API 사용을 명시적으로 승인한 뒤 동일 표본으로 번역을 포함한 전체 API 경로를 측정한다.
2. 대표 표본에서 #152 일반 token filtering의 DB 수준 결과 변화를 측정한다.
3. 고정된 회귀 테스트와 비교한 뒤에만 entity-aware keyword extraction을 추가한다.
4. 현재 최신순 정렬과 relevance-first 또는 hybrid ranking을 비교한다.
5. Vector search나 Elasticsearch를 시도하기 전에 표본별 기대 국가를 정의한다.
6. 구체적인 표본 실패가 추가 모델을 정당화할 때까지 `issue_id` 또는 clustering은 ADR 선택지로 유지한다.
