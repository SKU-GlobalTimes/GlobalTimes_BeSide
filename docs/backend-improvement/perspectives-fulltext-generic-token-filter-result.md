# Perspectives FULLTEXT 일반 token filter 결과

## 목적

이 문서는 #152 이후 DB 수준 FULLTEXT 결과 변화를 기록한다.

#152는 표본에서 확인한 일반 token을 제거하도록 Java `KeywordExtractor` 정책을 변경했다.

- `first`
- `round`
- `entre`

생성 키워드 개선이 로컬 MySQL FULLTEXT 후보도 바꾸는지 측정한다.
API 동작, DB schema, Redis 정책, crawler/RSS feed, ranking 또는 FULLTEXT query 형태는 변경하지 않는다.

FULLTEXT 반환 범위는 수집 데이터에 제한되므로 `perspectives-source-coverage-limit-log.md`와 함께 해석한다.

## 환경

| 항목 | 값 |
| --- | --- |
| 날짜 | 2026-07-04 |
| 데이터셋 | `docker-compose.dev.yml`의 로컬 개발 MySQL |
| 기사 행 | 9853 |
| FULLTEXT 인덱스 | `ft_article_title_description(title, description)` |
| 쿼리 경로 | `ArticleRepository.findPerspectives` 형태의 MySQL FULLTEXT query |
| 외부 번역 API | 호출하지 않음 |
| Redis cache | 사용하지 않음 |

민감한 로컬 `.env` 값과 외부 API 응답은 의도적으로 제외했다.

## 쿼리 형태

```sql
SELECT *
FROM article
WHERE article_id != :baseArticleId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50;
```

이 측정에서 `match count`는 `LIMIT 50` 적용 전 FULLTEXT 조건에 맞는 행 수다.
국가·언어 분포와 반환 예시는 `findPerspectives`와 동일한 최신순을 사용한다.

## 요약

| Base article ID | Base title note | Before keyword | Before count | After keyword | After count | Initial interpretation |
| --- | --- | --- | ---: | --- | ---: | --- |
| 8146 | US-Iran talks | `+First +round Iran talks` | 25 | `+Iran +talks ends encouraging` | 27 | 개선: 상위 결과가 스포츠/넓은 `round` 잡음에서 Iran/US 회담으로 이동했다. |
| 9440 | Meloni/Trump French sample | `+Entre +Meloni Trump divorce` | 0 | `+Meloni +Trump divorce italienne` | 3 | recall이 개선됐지만 가용한 출처 범위에 여전히 제한된다. |

## 8146: US-Iran 회담

기준 제목:

```text
First round of US-Iran talks ends with 'encouraging progress', mediators say
```

### #152 이전

키워드:

```text
+First +round Iran talks
```

국가·언어 분포:

| Country | Language | Count |
| --- | --- | ---: |
| us | en | 14 |
| us | null | 5 |
| cn | zh | 4 |
| gb | en | 2 |

상위 반환 예시:

| Article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 8413 | gb | en | Yamashita denies Woad third LPGA title in play-off |
| 7845 | us | en | USMNT Round of 32 match price |
| 9744 | cn | zh | China AI rivalry funding |
| 6566 | gb | en | PGA Tour round |
| 6247 | us | en | OSU stars and 1st round |
| 7667 | cn | zh | New Zealand players facing Iran |
| 6100 | cn | zh | Miami Open |
| 4427 | us | en | March Madness second round |

해석:

- 필수어 `First`, `round`가 넓은 스포츠와 토너먼트 결과를 요구했다.
- 매칭 결과가 존재하지만 첫 반환 예시 대부분은 약한 매칭이다.
- 출처 범위만의 문제가 아니라 검색 측 키워드 문제로 보였다.

### #152 이후

키워드:

```text
+Iran +talks ends encouraging
```

국가·언어 분포:

| Country | Language | Count |
| --- | --- | ---: |
| global | en | 13 |
| cn | zh | 7 |
| gb | en | 3 |
| us | en | 2 |
| de | en | 1 |
| global | null | 1 |

상위 반환 예시:

| Article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 9652 | cn | zh | Blockade lifted, assets returned to Iran in Swiss talks |
| 9667 | cn | zh | Iran-US talks continue |
| 8086 | global | en | Iran war live and Switzerland |
| 8091 | global | en | US-Iran talks to kick off Sunday |
| 7939 | global | en | US-Iran talks begin in Switzerland |
| 8114 | global | en | US envoy headed for Switzerland |
| 8129 | global | en | Israel-Lebanon talks in Washington |
| 8136 | global | en | Israel attacks Lebanon despite ceasefire |

해석:

- 상위 결과가 Iran/US 회담과 인접한 중동 외교에 훨씬 집중됐다.
- `ORDER BY published_at DESC`는 관련순이 아닌 최신순이므로 더 넓은 지역 외교 잡음은 일부 남는다.
- 이 표본에서는 #152가 유효한 키워드 측 개선임을 뒷받침한다.

## 9440: Meloni/Trump 프랑스어 표본

기준 제목:

```text
Entre Meloni et Trump, divorce à l'italienne
```

### #152 이전

키워드:

```text
+Entre +Meloni Trump divorce
```

결과:

| Metric | Value |
| --- | ---: |
| Match count | 0 |

해석:

- 필수 token인 `Entre`가 이 표본의 recall을 막았다.
- 결과가 없으므로 이 쿼리만으로 키워드 선택과 출처 범위의 영향 비중을 알 수 없다.

### #152 이후

키워드:

```text
+Meloni +Trump divorce italienne
```

국가·언어 분포:

| Country | Language | Count |
| --- | --- | ---: |
| global | en | 2 |
| de | de | 1 |

상위 반환 예시:

| Article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 8096 | global | en | Trump says Italy's Meloni sought photos with him |
| 8134 | global | en | Italy diplomat and Meloni/Trump fabricated story |
| 9517 | de | de | German article about Meloni and Trump |

해석:

- `Entre` 제거로 `0`에서 `3`으로 작지만 구체적인 recall 개선이 생겼다.
- 가용 결과는 Meloni/Trump와 관련 있지만 결과 집합은 작다.
- `perspectives-source-coverage-limit-log.md`에 따라 키워드 개선과 출처 범위·데이터 가용성 한계가 섞인 결과로 해석한다.

## 결과 해석

관찰한 개선:

- #152가 약한 일반 token 표본의 생성 키워드 초점을 개선했다.
- 8146의 DB 수준 결과가 스포츠·토너먼트 잡음에서 Iran/US 회담으로 이동했다.
- 9440의 DB 수준 결과가 없음에서 작은 Meloni/Trump 후보 집합으로 바뀌었다.

남은 한계:

- 결과 정렬은 관련순이 아니라 여전히 최신순이다.
- FULLTEXT는 수집한 `title`/`description`에서 충분한 text overlap을 요구한다.
- 누락되거나 지연된 RSS/News API 원본 데이터는 키워드 조정으로 복구할 수 없다.
- 이 측정은 전체 API 경로, 번역 API 또는 Redis를 호출하지 않는다.

## 후속 후보

1. 같은 표본에서 latest-first와 relevance-first 또는 hybrid 정렬을 비교한다.
2. 외부 API 사용을 명시적으로 승인한 뒤 비영어 기준 기사의 번역을 포함한 전체 API 경로를 측정한다.
3. Elasticsearch, vector search 또는 issue clustering을 선택하기 전에 대표 이슈의 출처 범위·최신성 지표를 추가한다.
4. 일반 token filter는 직관적인 대규모 stop-word 목록이 아니라 추가 측정 표본이 있을 때만 확장한다.
