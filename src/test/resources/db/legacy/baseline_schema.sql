CREATE TABLE `source` (
    `source_id` BIGINT NOT NULL AUTO_INCREMENT,
    `source_api_id` VARCHAR(255) DEFAULT NULL,
    `source_name` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`source_id`),
    CONSTRAINT `UK3pganbnum82xyg852kf3qonwu` UNIQUE (`source_name`)
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
    KEY `FK3ltucw25icv8f9x6ek63mmtkj` (`source_id`),
    KEY `idx_article_country` (`country`),
    KEY `idx_article_category` (`category`),
    KEY `idx_article_published_at` (`published_at`),
    KEY `idx_article_country_category_date` (`country`, `category`, `published_at`),
    CONSTRAINT `FK3ltucw25icv8f9x6ek63mmtkj` FOREIGN KEY (`source_id`) REFERENCES `source` (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `scrap` (
    `scrap_id` BIGINT NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6) NOT NULL,
    `article_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    PRIMARY KEY (`scrap_id`),
    UNIQUE KEY `idx_scrap_user_article` (`user_id`, `article_id`),
    KEY `idx_scrap_user_id` (`user_id`),
    KEY `FKt4r68omyf9mxqbkvxcejkg1kq` (`article_id`),
    CONSTRAINT `FK6jcivlvmiwrx9hd1d7h2tkije` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT `FKt4r68omyf9mxqbkvxcejkg1kq` FOREIGN KEY (`article_id`) REFERENCES `article` (`article_id`)
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
    KEY `FKtc9bwcspg3koh08xqx9otnp33` (`article_id`),
    CONSTRAINT `FKqw6yblcx0hqkn34q6jg03bj8` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT `FKtc9bwcspg3koh08xqx9otnp33` FOREIGN KEY (`article_id`) REFERENCES `article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
