-- 082-two-factor-auth：TOTP 双因素认证

-- ① user 表 2FA 扩展列。
--    四列全部由本迁移引入，故在 H2 镜像的对应行尾标 `-- V89`（约定见 schema-h2.sql 头部）。
--    totp_secret_encrypted 只存 AES-256-GCM 密文（格式 base64(iv):base64(ct||tag)），
--    明文密钥只在 setup 响应里一次性返回；VARCHAR(512) 相对实际长度（约 81 字符）有 6 倍余量。
--    历史行由 DEFAULT 填 0/NULL：two_factor_enabled=0 即"未启用"，与既有行为一致，不构成状态迁移。
ALTER TABLE `user`
  ADD COLUMN `two_factor_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 TOTP 双因素认证（0=未启用，1=已启用）',
  ADD COLUMN `totp_secret_encrypted` VARCHAR(512) DEFAULT NULL COMMENT 'AES-256-GCM 密文，格式 base64(iv):base64(ct||tag)，明文不落库',
  ADD COLUMN `two_factor_enabled_at` DATETIME DEFAULT NULL COMMENT '2FA 绑定时间（启用时刻）',
  ADD COLUMN `last_2fa_verified_at` DATETIME DEFAULT NULL COMMENT '最近一次二次验证通过时间';

-- ② 一次性恢复码表。
--    code_hash 存「每码独立随机盐 + SHA-256」：base64(salt):hex(sha256(salt||code))，约 89 字符。
--    used/used_at 支撑"一次性"语义，消费走条件 UPDATE（SET used=1 WHERE id=? AND used=0）而非
--    SELECT 后再 UPDATE —— 后者是 TOCTOU，并发下同一个码会被消费两次。
--
--    ⚠️ 三列 deleted/version/updated_at 是 BaseEntity 声明的列，**不可省**：
--    本表的实体 UserRecoveryCode extends BaseEntity，而 @TableLogic 的 deleted 会进每条
--    MyBatis-Plus 生成的 SELECT 的 WHERE、@Version 的 version 与 @TableField(fill=INSERT_UPDATE)
--    的 updated_at 会进每条 INSERT 的列清单。缺列的直接后果是写入路径报 Unknown column 而非
--    "缺列"——V88 修的正是同一类缺陷（那次的用户可见形态是 078 的两个端点在生产库上 500）。
--    形制与另外 50 张 BaseEntity 表对齐：deleted/version 为 INT NOT NULL DEFAULT 0，
--    updated_at 带 ON UPDATE CURRENT_TIMESTAMP。
CREATE TABLE IF NOT EXISTS `user_recovery_code` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `code_hash` VARCHAR(128) NOT NULL,
  `used` TINYINT(1) NOT NULL DEFAULT 0,
  `used_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` INT NOT NULL DEFAULT 0,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_urc_user` (`user_id`),
  INDEX `idx_urc_user_used` (`user_id`, `used`),
  CONSTRAINT `fk_urc_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ⚠️ 关于上面那条 ON DELETE CASCADE（如实记，避免被读成"孤儿已有人管"）：
-- User 的 deleted 带 @TableLogic，用户的"删除"是**软删除**（UPDATE ... SET deleted=1），
-- 从不产生物理 DELETE，故该级联**在本仓没有触发的机会**。恢复码真正的清理路径在应用层
-- （关闭 2FA / 管理员重置时调 RecoveryCodeService.revokeAll）。外键在此的角色是防御性的：
-- 若有人手工跑物理 DELETE，不至于留下孤儿行。
