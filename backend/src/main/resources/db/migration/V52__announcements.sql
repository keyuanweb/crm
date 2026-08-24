-- ============================================================
-- V52__announcements.sql — 公告与内部协作（037-announcements）
-- 说明：announcement（公告）+ announcement_read（已读）+ comment（多实体评论）
-- ============================================================

CREATE TABLE `announcement` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `title`      VARCHAR(100) NOT NULL,
  `content`    TEXT         NOT NULL COMMENT '富文本正文',
  `pinned`     TINYINT(1)   NOT NULL DEFAULT 0,
  `expires_at` DATETIME     DEFAULT NULL COMMENT '过期时间（空=永久）',
  `created_by` BIGINT       DEFAULT NULL,
  `deleted`    TINYINT(1)   NOT NULL DEFAULT 0,
  `version`    INT          NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_announce_pinned` (`pinned`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `announcement_read` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT,
  `announcement_id` BIGINT   NOT NULL,
  `user_id`         BIGINT   NOT NULL,
  `read_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_announce_read` (`announcement_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `comment` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `entity_type` VARCHAR(20)   NOT NULL COMMENT 'CUSTOMER/LEAD/OPPORTUNITY/TICKET',
  `entity_id`   BIGINT        NOT NULL,
  `content`     VARCHAR(1000) NOT NULL,
  `author_id`   BIGINT        NOT NULL,
  `deleted`     TINYINT(1)    NOT NULL DEFAULT 0,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comment_entity` (`entity_type`, `entity_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
