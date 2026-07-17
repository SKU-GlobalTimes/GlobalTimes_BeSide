CREATE TABLE `source` (
    `source_id` BIGINT NOT NULL AUTO_INCREMENT,
    `source_api_id` VARCHAR(255) DEFAULT NULL,
    `source_name` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`source_id`),
    CONSTRAINT `uk_source_name` UNIQUE (`source_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
    `user_id` BIGINT NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6) NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    `nickname` VARCHAR(255) NOT NULL,
    `provider` VARCHAR(255) NOT NULL,
    `provider_id` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `article` (
    `article_id` BIGINT NOT NULL AUTO_INCREMENT,
    `author` VARCHAR(255) NOT NULL,
    `category` VARCHAR(255) DEFAULT NULL,
    `content` TEXT NOT NULL,
    `country` VARCHAR(255) NOT NULL,
    `crawled_content` TEXT DEFAULT NULL,
    `description` TEXT NOT NULL,
    `published_at` DATETIME(6) NOT NULL,
    `summary` TEXT DEFAULT NULL,
    `title` VARCHAR(255) NOT NULL,
    `url` TEXT NOT NULL,
    `url_to_image` TEXT NOT NULL,
    `view_count` BIGINT DEFAULT NULL,
    `source_id` BIGINT DEFAULT NULL,
    `language` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`article_id`),
    KEY `idx_article_source_id` (`source_id`),
    KEY `idx_article_country` (`country`),
    KEY `idx_article_category` (`category`),
    KEY `idx_article_published_at` (`published_at`),
    KEY `idx_article_country_category_date` (`country`, `category`, `published_at`),
    FULLTEXT KEY `ft_article_title_description` (`title`, `description`),
    CONSTRAINT `fk_article_source` FOREIGN KEY (`source_id`) REFERENCES `source` (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `scrap` (
    `scrap_id` BIGINT NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6) NOT NULL,
    `article_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    PRIMARY KEY (`scrap_id`),
    UNIQUE KEY `idx_scrap_user_article` (`user_id`, `article_id`),
    KEY `idx_scrap_user_id` (`user_id`),
    KEY `idx_scrap_article_id` (`article_id`),
    CONSTRAINT `fk_scrap_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT `fk_scrap_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_history` (
    `chat_id` BIGINT NOT NULL AUTO_INCREMENT,
    `answer` TEXT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `question` TEXT NOT NULL,
    `article_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    PRIMARY KEY (`chat_id`),
    KEY `idx_chat_user_id` (`user_id`),
    KEY `idx_chat_user_article` (`user_id`, `article_id`),
    KEY `idx_chat_article_id` (`article_id`),
    CONSTRAINT `fk_chat_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT `fk_chat_article` FOREIGN KEY (`article_id`) REFERENCES `article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
