DELIMITER $$

DROP PROCEDURE IF EXISTS enforce_article_url_uniqueness$$

CREATE PROCEDURE enforce_article_url_uniqueness()
BEGIN
    DECLARE v_unsafe_duplicate_count INT DEFAULT 0;
    DECLARE v_column_count INT DEFAULT 0;
    DECLARE v_matching_column_count INT DEFAULT 0;
    DECLARE v_index_count INT DEFAULT 0;
    DECLARE v_matching_index_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO v_column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'article'
       AND column_name = 'url_hash';

    SELECT COUNT(*)
      INTO v_matching_column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'article'
       AND column_name = 'url_hash'
       AND data_type = 'binary'
       AND character_maximum_length = 32
       AND extra LIKE '%STORED GENERATED%'
       AND LOWER(REPLACE(REPLACE(generation_expression, '`', ''), ' ', ''))
           = 'unhex(sha2(url,256))';

    IF v_column_count > 0 AND v_matching_column_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'article.url_hash has an unexpected definition';
    END IF;

    IF v_column_count = 0 THEN
        ALTER TABLE article
            ADD COLUMN url_hash BINARY(32)
                GENERATED ALWAYS AS (UNHEX(SHA2(url, 256))) STORED;
    END IF;

    SELECT COUNT(DISTINCT index_name)
      INTO v_index_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'article'
       AND index_name = 'uk_article_url_hash';

    SELECT COUNT(*)
      INTO v_matching_index_count
      FROM (
            SELECT index_name,
                   non_unique,
                   index_type,
                   GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
              FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = 'article'
               AND index_name = 'uk_article_url_hash'
             GROUP BY index_name, non_unique, index_type
           ) url_hash_index
     WHERE non_unique = 0
       AND index_type = 'BTREE'
       AND indexed_columns = 'url_hash';

    IF v_index_count > 0 AND v_matching_index_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'article.uk_article_url_hash has an unexpected definition';
    END IF;

    SELECT COUNT(*)
      INTO v_unsafe_duplicate_count
      FROM article duplicate_article
      JOIN (
            SELECT url_hash, MAX(article_id) AS survivor_id
              FROM article
             GROUP BY url_hash
            HAVING COUNT(*) > 1
           ) duplicate_group
        ON duplicate_group.url_hash = duplicate_article.url_hash
       AND duplicate_group.survivor_id <> duplicate_article.article_id
      LEFT JOIN scrap ON scrap.article_id = duplicate_article.article_id
      LEFT JOIN chat_history ON chat_history.article_id = duplicate_article.article_id
     WHERE scrap.scrap_id IS NOT NULL
        OR chat_history.chat_id IS NOT NULL
        OR duplicate_article.summary IS NOT NULL
        OR duplicate_article.crawled_content IS NOT NULL
        OR COALESCE(duplicate_article.view_count, 0) <> 0;

    IF v_unsafe_duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Unsafe duplicate article data must be resolved before URL uniqueness migration';
    END IF;

    DELETE duplicate_article
      FROM article duplicate_article
      JOIN (
            SELECT url_hash, MAX(article_id) AS survivor_id
              FROM article
             GROUP BY url_hash
            HAVING COUNT(*) > 1
           ) duplicate_group
        ON duplicate_group.url_hash = duplicate_article.url_hash
       AND duplicate_group.survivor_id <> duplicate_article.article_id;

    IF v_index_count = 0 THEN
        ALTER TABLE article
            ADD UNIQUE INDEX uk_article_url_hash (url_hash);
    END IF;
END$$

DELIMITER ;

CALL enforce_article_url_uniqueness();

DROP PROCEDURE enforce_article_url_uniqueness;
