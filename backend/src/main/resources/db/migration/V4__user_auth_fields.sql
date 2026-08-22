-- ============================================================
-- V4__user_auth_fields.sql — 用户管理（002-user-management）
-- last_login_at：最后登录时间（FR-002）
-- token_version：密码变更/重置后递增，用于使旧访问令牌失效（FR-005/FR-006、SC-004）
-- ============================================================

ALTER TABLE `user`
  ADD COLUMN `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间' AFTER `enabled`,
  ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT '令牌版本（密码变更后递增）' AFTER `last_login_at`;
