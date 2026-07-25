# Perspectives FULLTEXT 정렬 비교

## 목적

이 문서는 `ArticleRepository.findPerspectives`의 정렬 전략을 비교한다.

#156에서 #152의 일반 token 제거가 약한 표본의 생성 키워드 품질을 개선했음을 확인했다.
하지만 표본 8146은 현재 결과가 최신순이므로 상위에 인접한 중동 외교 잡음이 남았다.

```sql
ORDER BY published_at DESC
```

남은 잡음이 정렬 문제로 더 잘 설명되는지 측정한다.
API 동작, DB schema, Redis 정책, crawler/RSS feed 또는 `KeywordExtractor`는 변경하지 않는다.

FULLTEXT는 수집된 로컬 데이터셋에 존재하는 기사만 정렬할 수 있으므로 `perspectives-source-coverage-limit-log.md`와 함께 해석한다.

## 환경

| 항목 | 값 |
| --- | --- |
| 날짜 | 2026-07-08 |
| 데이터셋 | Docker의 로컬 개발 MySQL |
| MySQL | 8.0.45 |
| 기사 행 | 9853 |
| FULLTEXT 인덱스 | `ft_article_title_description(title, description)` |
| 쿼리 경로 | `ArticleRepository.findPerspectives` 형태의 DB-level query |
| 외부 번역 API | 호출하지 않음 |
| Redis cache | 사용하지 않음 |

민감한 로컬 `.env` 값과 외부 API 응답은 의도적으로 제외했다.

## 비교 정렬 전략

모든 전략은 동일한 후보 조건을 사용한다.

```sql
WHERE article_id != :baseArticleId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
LIMIT 50
```

### Latest-first

현재 운영 정렬:

```sql
ORDER BY published_at DESC
```

의미: 매칭 기사 중 최신 기사를 우선한다.

### Relevance-first

FULLTEXT score 정렬:

```sql
ORDER BY MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE) DESC,
         article_id DESC
```

의미: FULLTEXT 키워드가 더 강하게 겹치는 기사를 우선한다.

### Hybrid 후보

탐색용 score와 제한된 최신성 가산점:

```sql
hybrid_score =
  fulltext_score
  + GREATEST(
      0,
      1 - TIMESTAMPDIFF(HOUR, published_at, max_matched_published_at) / (24 * 30)
    ) * 5

ORDER BY hybrid_score DESC,
         published_at DESC
```

의미: 관련 기사를 우선하되 매칭 기사 중 가장 최신 시각에서 30일 이내인 기사에 제한된 가산점을 준다.
최종 ranking 정책이 아닌 측정 후보다.

## 표본 요약

선택한 모든 표본의 매칭 수가 50보다 적어 각 전략은 동일한 후보 집합을 반환한다.
따라서 이 측정은 recall이 아니라 상위 정렬을 비교한다.

| Base article ID | Keyword | Match count | Country/language spread |
| --- | --- | ---: | --- |
| 8146 | `+Iran +talks ends encouraging` | 27 | global/en 13, cn/zh 7, gb/en 3, us/en 2, de/en 1, global/null 1 |
| 9440 | `+Meloni +Trump divorce italienne` | 3 | global/en 2, de/de 1 |
| 8147 | `+Trump +backed political outsider` | 4 | cn/zh 2, gb/en 1, us/null 1 |
| 8149 | `+BTS +fans losing thousands` | 8 | cn/zh 4, gb/en 2, fr/fr 1, us/en 1 |

## 8146: US-Iran 회담

기준 제목:

```text
First round of US-Iran talks ends with 'encouraging progress', mediators say
```

### 상위 결과

| Ordering | Top returned examples |
| --- | --- |
| latest-first | 9652 Blockade/assets to Iran in Swiss talks; 9667 Iran-US talks continue; 8086 Iran war live and Lebanon; 8091 US-Iran talks to kick off; 7939 US-Iran talks begin |
| relevance-first | 9667 Iran-US talks continue; 9652 Blockade/assets to Iran; 5219 Iran denies US talks; 1517 Ukraine peace talks amid Iran war; 4276 Iran World Cup |
| hybrid | 9652 Blockade/assets to Iran; 9667 Iran-US talks continue; 8114 US envoy headed for Switzerland; 8129 Israel-Lebanon talks; 8086 Iran war live and Lebanon |

### 해석

- Latest-first는 최신 Iran/US 회담 기사를 높게 유지하지만 최근 Lebanon/ceasefire 지역 잡음도 상위에 둔다.
- Relevance-first는 강한 `Iran`/`talks` overlap을 올리지만 Ukraine 평화 회담이나 Iran World Cup처럼 오래되거나 넓은 매칭을 높일 수 있다.
- Hybrid 후보는 score를 쓰면서 순수 relevance-first가 오래된 기사로 치우치는 현상을 줄인다.
- Lebanon/ceasefire 결과는 동일 지역 외교 문맥과 text가 겹치므로 정렬만으로 완전히 해결되지 않는다.

## 9440: Meloni/Trump 프랑스어 표본

기준 제목:

```text
Entre Meloni et Trump, divorce à l'italienne
```

### 상위 결과

| Ordering | Top returned examples |
| --- | --- |
| latest-first | 8096 Trump/Meloni photos; 8134 Meloni says Trump fabricated story; 9517 German Meloni/Trump article |
| relevance-first | 9517 German Meloni/Trump article; 8134 Meloni says Trump fabricated story; 8096 Trump/Meloni photos |
| hybrid | 9517 German Meloni/Trump article; 8134 Meloni says Trump fabricated story; 8096 Trump/Meloni photos |

### 해석

- 후보가 3행뿐이므로 정렬로 출처 범위 한계를 해결할 수 없다.
- Relevance-first와 hybrid 모두 FULLTEXT score가 가장 높은 기사를 첫 번째로 옮긴다.
- #152 이후 recall 추적에는 유용하지만 이 표본만으로 ranking 정책을 정당화하기에는 너무 작다.

## 8147: Trump-backed 정치 신인

기준 제목 메모:

```text
Trump-backed political outsider
```

### 상위 결과

| Ordering | Top returned examples |
| --- | --- |
| latest-first | 9658 Trump-backed Colombia election; 4941 Iran awaits Trump threat; 3258 Trump-backed television merger; 778 Trump allies/Iran |
| relevance-first | 4941 Iran awaits Trump threat; 9658 Trump-backed Colombia election; 778 Trump allies/Iran; 3258 Trump-backed television merger |
| hybrid | 9658 Trump-backed Colombia election; 4941 Iran awaits Trump threat; 778 Trump allies/Iran; 3258 Trump-backed television merger |

### 해석

- Latest-first는 동일 이슈일 가능성이 가장 큰 최신 기사를 이미 첫 번째로 둔다.
- Relevance-first는 score가 높지만 문맥이 틀린 Iran/Trump 기사를 지나치게 올린다.
- Hybrid는 score를 ranking 신호로 유지하면서 Colombia 선거 기사를 첫 번째에 둔다.
- 이 표본은 pure relevance-first로 즉시 전환하면 안 된다는 근거다.

## 8149: BTS 팬

기준 제목 메모:

```text
BTS fans losing thousands
```

### 상위 결과

| Ordering | Top returned examples |
| --- | --- |
| latest-first | 6014 BTS fans/Arirang credits; 4523 BTS agency shares; 4935 BTS comeback crowd; 3246 K-pop fans in Seoul; 3008 BTS comeback concert |
| relevance-first | 3008 BTS comeback concert; 6014 BTS fans/Arirang credits; 3246 K-pop fans in Seoul; 2874 BTS first show; 4935 BTS comeback crowd |
| hybrid | 3008 BTS comeback concert; 6014 BTS fans/Arirang credits; 3246 K-pop fans in Seoul; 2874 BTS first show; 4935 BTS comeback crowd |

### 해석

- 모든 전략에서 대부분 BTS 관련 행인 good-match 대조 표본이다.
- Relevance-first와 hybrid가 latest-first보다 강한 BTS 사건 매칭을 상위에 둔다.
- 후보 집합이 이미 일관될 때 정렬로 상위 결과 품질을 개선할 수 있음을 보여준다.

## 관찰 결과

- Pure latest-first는 단순하고 최신성에 유리하지만 인접 주제 잡음을 높게 유지할 수 있다.
- Pure relevance-first는 정확한 키워드 overlap을 개선할 수 있지만 강한 token overlap을 가진 오래되거나 잘못된 문맥의 기사를 지나치게 올릴 수 있다.
- 측정한 hybrid 후보는 최신성을 제한된 신호로 유지하므로 표본 8146과 8147에서 pure relevance-first보다 안전해 보인다.
- 모든 측정 후보 집합이 50행 미만이므로 recall이 아닌 상위 정렬만 평가했다.
- 출처 범위는 명확한 경계다. 수집 DB에 동일 이슈 기사가 없다면 어떤 정렬도 복구할 수 없다.

## 권고

`ORDER BY published_at DESC`를 pure relevance-first로 즉시 교체하지 않는다.

나중에 코드 변경을 시도한다면 테스트와 전후 측정을 포함한 작고 명시적인 hybrid 실험을 우선한다.

1. 대표 표본과 기대 상위 결과 의도를 선정한다.
2. Hybrid ranking용 repository method 또는 query variant를 추가한다.
3. 이 DB 수준 기준선과 API 응답 순서를 비교한다.
4. 출처 범위 한계와 ranking 실패를 별도로 기록한다.
