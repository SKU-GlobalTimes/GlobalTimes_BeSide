-- Read-only snapshot for Issue #223.
-- published_at is stored as a timezone-less DATETIME. The application treats it
-- as UTC, so UTC_TIMESTAMP() is used consistently for this diagnostic snapshot.

SELECT
    COUNT(*) AS article_count,
    MIN(published_at) AS oldest_published_at,
    MAX(published_at) AS latest_published_at,
    TIMESTAMPDIFF(SECOND, MAX(published_at), UTC_TIMESTAMP()) AS latest_age_seconds
FROM article;

SELECT
    COALESCE(s.source_name, '(no source)') AS source_name,
    COUNT(*) AS article_count,
    MIN(a.published_at) AS oldest_published_at,
    MAX(a.published_at) AS latest_published_at,
    TIMESTAMPDIFF(SECOND, MAX(a.published_at), UTC_TIMESTAMP()) AS latest_age_seconds
FROM article a
LEFT JOIN source s ON s.source_id = a.source_id
GROUP BY s.source_id, s.source_name
ORDER BY article_count DESC, source_name;

SELECT
    country,
    language,
    category,
    COUNT(*) AS article_count,
    MIN(published_at) AS oldest_published_at,
    MAX(published_at) AS latest_published_at,
    TIMESTAMPDIFF(SECOND, MAX(published_at), UTC_TIMESTAMP()) AS latest_age_seconds
FROM article
GROUP BY country, language, category
ORDER BY article_count DESC, country, language, category;

SELECT
    country,
    language,
    COUNT(*) AS article_count,
    SUM(published_at >= UTC_TIMESTAMP() - INTERVAL 24 HOUR) AS articles_last_24h,
    SUM(published_at >= UTC_TIMESTAMP() - INTERVAL 7 DAY) AS articles_last_7d,
    SUM(published_at >= UTC_TIMESTAMP() - INTERVAL 30 DAY) AS articles_last_30d,
    MAX(published_at) AS latest_published_at
FROM article
GROUP BY country, language
ORDER BY article_count DESC, country, language;
