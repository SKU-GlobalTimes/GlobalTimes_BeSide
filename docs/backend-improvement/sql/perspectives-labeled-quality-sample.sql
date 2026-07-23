-- Issue #227: 읽기 전용 Perspectives 정답 표본 품질 측정.
-- 현재 KeywordExtractor 출력과 ArticleRepository.findPerspectives의
-- latest-first 조회 형태를 재현한다.
-- description과 source_name은 수동 라벨 판정 근거를 재검증하기 위해 함께 조회한다.

SELECT a.article_id, a.country, a.language, a.published_at,
       s.source_name, a.title, a.description
FROM article a
LEFT JOIN source s ON s.source_id = a.source_id
WHERE a.article_id != 8149
  AND MATCH(a.title, a.description)
      AGAINST('+BTS +fans losing thousands' IN BOOLEAN MODE)
ORDER BY a.published_at DESC
LIMIT 5;

SELECT a.article_id, a.country, a.language, a.published_at,
       s.source_name, a.title, a.description
FROM article a
LEFT JOIN source s ON s.source_id = a.source_id
WHERE a.article_id != 8146
  AND MATCH(a.title, a.description)
      AGAINST('+Iran +talks ends encouraging' IN BOOLEAN MODE)
ORDER BY a.published_at DESC
LIMIT 5;

SELECT a.article_id, a.country, a.language, a.published_at,
       s.source_name, a.title, a.description
FROM article a
LEFT JOIN source s ON s.source_id = a.source_id
WHERE a.article_id != 8148
  AND MATCH(a.title, a.description)
      AGAINST('+Russian +troop build threatens' IN BOOLEAN MODE)
ORDER BY a.published_at DESC
LIMIT 5;

SELECT a.article_id, a.country, a.language, a.published_at,
       s.source_name, a.title, a.description
FROM article a
LEFT JOIN source s ON s.source_id = a.source_id
WHERE a.article_id != 8147
  AND MATCH(a.title, a.description)
      AGAINST('+Trump +backed political outsider' IN BOOLEAN MODE)
ORDER BY a.published_at DESC
LIMIT 5;

SELECT a.article_id, a.country, a.language, a.published_at,
       s.source_name, a.title, a.description
FROM article a
LEFT JOIN source s ON s.source_id = a.source_id
WHERE a.article_id != 8468
  AND MATCH(a.title, a.description)
      AGAINST('+반도체 +디스플레이칩도 쑥쑥…대만 하이맥스' IN BOOLEAN MODE)
ORDER BY a.published_at DESC
LIMIT 5;

SELECT a.article_id, a.country, a.language, a.published_at,
       s.source_name, a.title, a.description
FROM article a
LEFT JOIN source s ON s.source_id = a.source_id
WHERE a.article_id != 9440
  AND MATCH(a.title, a.description)
      AGAINST('+Meloni +Trump divorce italienne' IN BOOLEAN MODE)
ORDER BY a.published_at DESC
LIMIT 5;

-- 동일 사건 행이 없을 수 있는 표본을 더 넓은 키워드로 탐색한다.
-- 이 조회에서도 결과가 없으면 현재 검색어에서 candidate가 없다는 근거로만 사용한다.
-- 독립적인 row 존재 확인 없이는 source coverage와 retrieval failure를 구분하지 않는다.
SELECT a.article_id, a.country, a.language, a.published_at,
       s.source_name, a.title, a.description
FROM article a
LEFT JOIN source s ON s.source_id = a.source_id
WHERE a.article_id != 8149
  AND (
      MATCH(a.title, a.description) AGAINST('+BTS +ticket' IN BOOLEAN MODE)
      OR MATCH(a.title, a.description) AGAINST('+BTS +scam' IN BOOLEAN MODE)
  )
ORDER BY a.published_at DESC
LIMIT 20;

SELECT a.article_id, a.country, a.language, a.published_at,
       s.source_name, a.title, a.description
FROM article a
LEFT JOIN source s ON s.source_id = a.source_id
WHERE a.article_id != 8148
  AND (
      MATCH(a.title, a.description) AGAINST('+Donbas +Russian' IN BOOLEAN MODE)
      OR MATCH(a.title, a.description) AGAINST('+Kostyantynivka' IN BOOLEAN MODE)
  )
ORDER BY a.published_at DESC
LIMIT 20;

SELECT a.article_id, a.country, a.language, a.published_at,
       s.source_name, a.title, a.description
FROM article a
LEFT JOIN source s ON s.source_id = a.source_id
WHERE a.article_id != 8468
  AND (
      MATCH(a.title, a.description) AGAINST('+Himax' IN BOOLEAN MODE)
      OR MATCH(a.title, a.description) AGAINST('+하이맥스' IN BOOLEAN MODE)
  )
ORDER BY a.published_at DESC
LIMIT 20;
