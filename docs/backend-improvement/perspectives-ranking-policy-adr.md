# ADR: Perspectives ranking policy 도입 보류와 hybrid 후보 적용 기준

## Status

- Date: 2026-07-08
- Issue: [#164](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/164)
- Decision: `ORDER BY published_at DESC`를 지금 바로 교체하지 않는다.
- Scope: 문서/ADR 작업만 수행한다. 코드, API 응답, DB schema, Redis 정책, FULLTEXT query는 변경하지 않는다.

## Context

`GET /api/news/{id}/perspectives`는 기준 기사와 유사한 이슈의 다른 국가 기사를 보여주기 위한 API다.
현재 구현은 기준 기사 제목에서 키워드를 추출하고, 필요하면 영어 번역 키워드를 추가한 뒤 MySQL FULLTEXT로 후보를 찾는다.

현재 repository query shape는 다음과 같다.

```sql
WHERE article_id != :baseArticleId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50
```

#156에서는 #152의 generic-token filtering 이후 대표 샘플의 DB-level FULLTEXT 결과가 개선되는지 측정했다.
8146 샘플은 `+First +round Iran talks`에서 `+Iran +talks ends encouraging`로 바뀌며 스포츠/라운드 노이즈가 줄고 Iran/US talks 중심 결과로 이동했다.
다만 최신순 정렬 때문에 Lebanon/ceasefire 같은 인접 외교 노이즈가 여전히 상위에 남았다.

#160에서는 같은 후보 predicate에서 세 가지 ordering을 비교했다.

- latest-first: 현재 운영 방식인 `ORDER BY published_at DESC`
- relevance-first: FULLTEXT score 중심 정렬
- hybrid candidate: FULLTEXT score에 제한된 recency boost를 더한 후보

측정 결과 pure relevance-first는 일부 샘플에서 잘 맞는 기사도 올리지만, 8147처럼 wrong-context Iran/Trump 기사를 1위로 올릴 수 있었다.
hybrid 후보는 pure relevance-first보다 안전해 보였지만, 아직 운영 응답을 바꿀 만큼 충분한 샘플과 회귀 기준이 없다.

또한 source coverage 한계가 있다.
수집된 DB에 같은 이슈 기사가 없거나 특정 국가/source feed가 늦게 들어오면, ranking policy만으로는 결과를 복구할 수 없다.
따라서 ranking 개선 효과와 RSS/News API 수집 편차를 분리해서 해석해야 한다.

## Decision

현재 단계에서는 Perspectives ranking policy를 구현으로 바꾸지 않고 보류한다.

구체적으로는 다음을 결정한다.

- pure relevance-first를 현재 latest-first의 직접 대체안으로 적용하지 않는다.
- hybrid ranking은 폐기하지 않고, 후속 code experiment 후보로 남긴다.
- 운영 API 응답 순서를 바꾸기 전에 대표 샘플, 기대 top intent, 회귀 테스트, before/after 측정 기준을 먼저 정의한다.
- source coverage 문제를 ranking 실패로 단정하지 않는다.

## Why Not Implement Now

### 현재 데이터 규모와 사용자 흐름

현재 측정은 local development MySQL 9,853 rows 기준이며, #160 샘플들은 모두 candidate set이 50건 미만이었다.
즉 이번 비교는 recall 개선이 아니라 같은 후보군 안에서 top ordering이 어떻게 달라지는지를 본 것이다.

이 정도 근거만으로 운영 API 응답 순서를 바꾸면, 일부 샘플에서는 좋아 보이지만 다른 샘플에서는 wrong-context 기사가 더 위로 올라갈 수 있다.

### 포트폴리오 설명 가능성

신입 백엔드 포트폴리오 관점에서는 "복잡한 ranking query를 넣었다"보다 "측정 결과 pure relevance-first의 위험을 확인했고, hybrid 적용 기준을 ADR로 남겼다"가 더 설명 가능하다.
구현하지 않는 판단도 근거가 있으면 좋은 엔지니어링 결과다.

### 더 단순한 대안

지금은 코드 변경 없이 다음을 먼저 정리하는 편이 적절하다.

- 대표 샘플과 기대 top-result intent
- source coverage 한계와 ranking 한계의 구분
- hybrid 실험이 필요한 조건
- API 응답 변경 전 필요한 회귀 테스트

## Future Hybrid Experiment Criteria

다음 조건이 충족되면 hybrid ranking code experiment를 별도 Issue로 분리해 검토한다.

1. 대표 샘플을 최소 6개 이상 정의한다.
   - good-match control
   - partial-match sample
   - noisy recency-biased sample
   - non-English base article
   - low-match or zero-match sample
   - multi-country breaking/news topic
2. 각 샘플에 기대 top-result intent를 적는다.
   - 특정 article ID 고정보다 "같은 사건", "같은 인물/국가/이슈", "wrong-context 제외"처럼 설명 가능한 기준을 우선한다.
3. latest-first, relevance-first, hybrid의 top 5 결과를 같은 local dataset에서 비교한다.
4. API response order 변경이 필요한지 service-level 테스트 또는 repository-level 테스트로 고정한다.
5. source coverage 한계로 인한 실패를 ranking 실패와 분리해서 기록한다.
6. hybrid formula의 recency window와 boost weight를 임의값으로 숨기지 않고 문서화한다.

## Non-Goals

- Elasticsearch, vector DB, RAG, issue clustering 도입을 결정하지 않는다.
- `ArticleRepository.findPerspectives` query를 변경하지 않는다.
- Redis cache key, TTL, stale policy를 변경하지 않는다.
- crawler/RSS feed 수집 정책을 변경하지 않는다.
- #110 장기 troubleshooting 이슈를 정리하거나 구현하지 않는다.

## Consequences

좋은 점:

- 운영 API 응답 순서를 성급하게 바꾸지 않는다.
- #160 측정에서 드러난 pure relevance-first 위험을 후속 작업자가 빠르게 이해할 수 있다.
- hybrid ranking을 폐기하지 않고, 적용 조건을 가진 후보로 보존한다.
- source coverage와 ranking 품질을 분리해서 해석하는 기준이 남는다.

감수하는 점:

- 최신순 정렬의 알려진 노이즈는 당장 해결하지 않는다.
- 일부 good-match 샘플에서 relevance-first/hybrid가 더 나은 top ordering을 보였더라도, 사용자는 아직 그 개선을 체감하지 못한다.
- 후속 실험 전까지 ranking policy는 보수적으로 유지된다.

## References

- `docs/backend-improvement/perspectives-matching-quality-baseline.md`
- `docs/backend-improvement/perspectives-matching-sample-snapshot.md`
- `docs/backend-improvement/perspectives-fulltext-generic-token-filter-result.md`
- `docs/backend-improvement/perspectives-fulltext-ordering-comparison.md`
- `docs/backend-improvement/perspectives-source-coverage-limit-log.md`
