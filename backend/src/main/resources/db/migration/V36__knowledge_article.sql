-- ============================================================
-- V36__knowledge_article.sql — 知识库文章表（015-customer-service）
-- ============================================================

CREATE TABLE `knowledge_article` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `category`   VARCHAR(40)  NOT NULL COMMENT 'PRODUCT_USAGE/FAULT_TROUBLESHOOTING/PROCESS_CONSULT/AFTER_SALES_POLICY/OTHER',
  `title`      VARCHAR(200) NOT NULL COMMENT '标题',
  `content`    MEDIUMTEXT   COMMENT '内容',
  `keywords`   VARCHAR(500) DEFAULT NULL COMMENT '关键词（逗号分隔）',
  `status`     VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
  `author_id`  BIGINT       DEFAULT NULL COMMENT '作者',
  `deleted`    TINYINT(1)   NOT NULL DEFAULT 0,
  `version`    INT          NOT NULL DEFAULT 0,
  `created_by` BIGINT       DEFAULT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_article_status` (`status`),
  KEY `idx_article_category` (`category`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
