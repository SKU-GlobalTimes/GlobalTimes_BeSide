DELIMITER $$

DROP PROCEDURE IF EXISTS normalize_foreign_key$$
DROP PROCEDURE IF EXISTS normalize_index$$

CREATE PROCEDURE normalize_index(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_columns VARCHAR(255),
    IN p_column_sql VARCHAR(255),
    IN p_non_unique INT,
    IN p_index_type VARCHAR(16)
)
BEGIN
    DECLARE v_named_count INT DEFAULT 0;
    DECLARE v_matching_named_count INT DEFAULT 0;
    DECLARE v_existing_name VARCHAR(64) DEFAULT NULL;
    DECLARE v_index_keyword VARCHAR(16) DEFAULT 'INDEX';
    DECLARE v_sql TEXT;
    DECLARE v_message TEXT;

    SELECT COUNT(DISTINCT index_name)
      INTO v_named_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = p_table_name
       AND index_name = p_index_name;

    SELECT COUNT(*)
      INTO v_matching_named_count
      FROM (
            SELECT index_name,
                   non_unique AS actual_non_unique,
                   index_type AS actual_index_type,
                   GROUP_CONCAT(column_name ORDER BY seq_in_index) AS actual_columns
              FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = p_table_name
               AND index_name = p_index_name
             GROUP BY index_name, non_unique, index_type
           ) matching_named
     WHERE actual_columns = p_columns
       AND actual_non_unique = p_non_unique
       AND actual_index_type = p_index_type;

    IF v_named_count > 0 AND v_matching_named_count = 0 THEN
        SET v_message = CONCAT('Index ', p_table_name, '.', p_index_name, ' has an unexpected definition');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
    END IF;

    IF v_named_count = 0 THEN
        SELECT MIN(index_name)
          INTO v_existing_name
          FROM (
                SELECT index_name,
                       non_unique AS actual_non_unique,
                       index_type AS actual_index_type,
                       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS actual_columns
                  FROM information_schema.statistics
                 WHERE table_schema = DATABASE()
                   AND table_name = p_table_name
                   AND index_name <> 'PRIMARY'
                 GROUP BY index_name, non_unique, index_type
               ) matching_index
         WHERE actual_columns = p_columns
           AND actual_non_unique = p_non_unique
           AND actual_index_type = p_index_type;

        IF v_existing_name IS NOT NULL THEN
            SET v_sql = CONCAT(
                    'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
                    '` RENAME INDEX `', REPLACE(v_existing_name, '`', '``'),
                    '` TO `', REPLACE(p_index_name, '`', '``'), '`'
            );
        ELSE
            IF p_index_type = 'FULLTEXT' THEN
                SET v_index_keyword = 'FULLTEXT INDEX';
            ELSEIF p_non_unique = 0 THEN
                SET v_index_keyword = 'UNIQUE INDEX';
            END IF;

            SET v_sql = CONCAT(
                    'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
                    '` ADD ', v_index_keyword, ' `', REPLACE(p_index_name, '`', '``'),
                    '` (', p_column_sql, ')'
            );
        END IF;

        SET @migration_sql = v_sql;
        PREPARE migration_statement FROM @migration_sql;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    END IF;
END$$

CREATE PROCEDURE normalize_foreign_key(
    IN p_table_name VARCHAR(64),
    IN p_constraint_name VARCHAR(64),
    IN p_columns VARCHAR(255),
    IN p_column_sql VARCHAR(255),
    IN p_referenced_table VARCHAR(64),
    IN p_referenced_columns VARCHAR(255),
    IN p_referenced_column_sql VARCHAR(255),
    IN p_match_option VARCHAR(16),
    IN p_update_rule VARCHAR(16),
    IN p_delete_rule VARCHAR(16)
)
BEGIN
    DECLARE v_named_count INT DEFAULT 0;
    DECLARE v_matching_named_count INT DEFAULT 0;
    DECLARE v_related_count INT DEFAULT 0;
    DECLARE v_existing_name VARCHAR(64) DEFAULT NULL;
    DECLARE v_sql TEXT;
    DECLARE v_message TEXT;

    SELECT COUNT(*)
      INTO v_named_count
      FROM information_schema.referential_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = p_table_name
       AND constraint_name = p_constraint_name;

    SELECT COUNT(*)
      INTO v_matching_named_count
      FROM (
            SELECT k.constraint_name,
                   k.referenced_table_name AS actual_referenced_table,
                   GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position) AS actual_columns,
                   GROUP_CONCAT(k.referenced_column_name ORDER BY k.ordinal_position) AS actual_referenced_columns,
                   r.match_option AS actual_match_option,
                   r.update_rule AS actual_update_rule,
                   r.delete_rule AS actual_delete_rule
              FROM information_schema.key_column_usage k
              JOIN information_schema.referential_constraints r
                ON r.constraint_schema = k.constraint_schema
               AND r.table_name = k.table_name
               AND r.constraint_name = k.constraint_name
             WHERE k.constraint_schema = DATABASE()
               AND k.table_name = p_table_name
               AND k.constraint_name = p_constraint_name
             GROUP BY k.constraint_name, k.referenced_table_name,
                      r.match_option, r.update_rule, r.delete_rule
           ) matching_named
     WHERE actual_columns = p_columns
       AND actual_referenced_table = p_referenced_table
       AND actual_referenced_columns = p_referenced_columns
       AND actual_match_option = p_match_option
       AND actual_update_rule = p_update_rule
       AND actual_delete_rule = p_delete_rule;

    IF v_named_count > 0 AND v_matching_named_count = 0 THEN
        SET v_message = CONCAT('Foreign key ', p_table_name, '.', p_constraint_name, ' has an unexpected definition');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
    END IF;

    IF v_named_count = 0 THEN
        SELECT MIN(constraint_name)
          INTO v_existing_name
          FROM (
                SELECT k.constraint_name,
                       k.referenced_table_name AS actual_referenced_table,
                       GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position) AS actual_columns,
                       GROUP_CONCAT(k.referenced_column_name ORDER BY k.ordinal_position) AS actual_referenced_columns,
                       r.match_option AS actual_match_option,
                       r.update_rule AS actual_update_rule,
                       r.delete_rule AS actual_delete_rule
                  FROM information_schema.key_column_usage k
                  JOIN information_schema.referential_constraints r
                    ON r.constraint_schema = k.constraint_schema
                   AND r.table_name = k.table_name
                   AND r.constraint_name = k.constraint_name
                 WHERE k.constraint_schema = DATABASE()
                   AND k.table_name = p_table_name
                 GROUP BY k.constraint_name, k.referenced_table_name,
                          r.match_option, r.update_rule, r.delete_rule
               ) matching_legacy
         WHERE actual_columns = p_columns
           AND actual_referenced_table = p_referenced_table
           AND actual_referenced_columns = p_referenced_columns
           AND actual_match_option = p_match_option
           AND actual_update_rule = p_update_rule
           AND actual_delete_rule = p_delete_rule;

        SELECT COUNT(*)
          INTO v_related_count
          FROM information_schema.key_column_usage
         WHERE constraint_schema = DATABASE()
           AND table_name = p_table_name
           AND FIND_IN_SET(column_name, p_columns) > 0
           AND referenced_table_name IS NOT NULL;

        IF v_existing_name IS NULL AND v_related_count > 0 THEN
            SET v_message = CONCAT('Foreign key for ', p_table_name, '.', p_columns, ' has an unexpected definition');
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
        END IF;

        IF v_existing_name IS NOT NULL THEN
            SET v_sql = CONCAT(
                    'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
                    '` DROP FOREIGN KEY `', REPLACE(v_existing_name, '`', '``'), '`'
            );
            SET @migration_sql = v_sql;
            PREPARE migration_statement FROM @migration_sql;
            EXECUTE migration_statement;
            DEALLOCATE PREPARE migration_statement;
        END IF;

        SET v_sql = CONCAT(
                'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
                '` ADD CONSTRAINT `', REPLACE(p_constraint_name, '`', '``'),
                '` FOREIGN KEY (', p_column_sql,
                ') REFERENCES `', REPLACE(p_referenced_table, '`', '``'),
                '` (', p_referenced_column_sql, ')'
        );
        SET @migration_sql = v_sql;
        PREPARE migration_statement FROM @migration_sql;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    END IF;
END$$

DELIMITER ;

CALL normalize_index('source', 'uk_source_name', 'source_name', '`source_name`', 0, 'BTREE');
CALL normalize_index('article', 'idx_article_source_id', 'source_id', '`source_id`', 1, 'BTREE');
CALL normalize_index('scrap', 'idx_scrap_user_id', 'user_id', '`user_id`', 1, 'BTREE');
CALL normalize_index('scrap', 'idx_scrap_article_id', 'article_id', '`article_id`', 1, 'BTREE');
CALL normalize_index('chat_history', 'idx_chat_user_id', 'user_id', '`user_id`', 1, 'BTREE');
CALL normalize_index('chat_history', 'idx_chat_article_id', 'article_id', '`article_id`', 1, 'BTREE');
CALL normalize_index('article', 'ft_article_title_description', 'title,description', '`title`, `description`', 1, 'FULLTEXT');

CALL normalize_foreign_key('article', 'fk_article_source', 'source_id', '`source_id`', 'source', 'source_id', '`source_id`', 'NONE', 'NO ACTION', 'NO ACTION');
CALL normalize_foreign_key('scrap', 'fk_scrap_user', 'user_id', '`user_id`', 'users', 'user_id', '`user_id`', 'NONE', 'NO ACTION', 'NO ACTION');
CALL normalize_foreign_key('scrap', 'fk_scrap_article', 'article_id', '`article_id`', 'article', 'article_id', '`article_id`', 'NONE', 'NO ACTION', 'NO ACTION');
CALL normalize_foreign_key('chat_history', 'fk_chat_user', 'user_id', '`user_id`', 'users', 'user_id', '`user_id`', 'NONE', 'NO ACTION', 'NO ACTION');
CALL normalize_foreign_key('chat_history', 'fk_chat_article', 'article_id', '`article_id`', 'article', 'article_id', '`article_id`', 'NONE', 'NO ACTION', 'NO ACTION');

DROP PROCEDURE normalize_foreign_key;
DROP PROCEDURE normalize_index;
