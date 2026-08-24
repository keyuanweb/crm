-- ============================================================
-- V46__role_permissions.sql — 角色权限管理（028-role-permissions）
-- 说明：role + role_menu + role_permission 三表，seed 内建角色
--       ADMIN（全菜单全权限 ALL）/ SALES（业务菜单+业务操作 SELF）
--       / SUPPORT（客服菜单+客服操作 DEPT）
-- ============================================================

CREATE TABLE `role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(50)  NOT NULL COMMENT '角色编码（唯一）',
  `name`        VARCHAR(50)  NOT NULL COMMENT '角色名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
  `data_scope`  VARCHAR(20)  NOT NULL DEFAULT 'SELF' COMMENT '默认数据范围：ALL/DEPT/SELF',
  `enabled`     TINYINT(1)   NOT NULL DEFAULT 1,
  `built_in`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '内建角色（1 不可删除）',
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `role_menu` (
  `id`       BIGINT      NOT NULL AUTO_INCREMENT,
  `role_id`  BIGINT      NOT NULL COMMENT '角色',
  `menu_key` VARCHAR(50) NOT NULL COMMENT '菜单标识（契约字典）',
  PRIMARY KEY (`id`),
  KEY `idx_role_menu_role` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `role_permission` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT,
  `role_id`         BIGINT      NOT NULL COMMENT '角色',
  `permission_code` VARCHAR(50) NOT NULL COMMENT '操作权限码（实体:动作）',
  PRIMARY KEY (`id`),
  KEY `idx_role_perm_role` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------- Seed：内建角色 ----------
INSERT INTO `role` (`code`, `name`, `description`, `data_scope`, `enabled`, `built_in`) VALUES
('ADMIN',   '系统管理员', '全部菜单与操作权限，数据范围全部', 'ALL',  1, 1),
('SALES',   '销售',       '客户/销售/交易业务菜单与操作',     'SELF', 1, 1),
('SUPPORT', '客服',       '客户与客户服务菜单与操作',         'DEPT', 1, 1);

-- ---------- 菜单字典 ----------
-- stats leads customers contacts opportunities sales-opportunities quotes contracts orders tasks products
-- marketing tickets knowledge exports at-risk leaderboard reports suggestions board users departments workflows
-- sla-policies custom-fields contract-templates audit-logs recycle-bin

INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'leads' UNION SELECT 'customers' UNION SELECT 'contacts'
  UNION SELECT 'opportunities' UNION SELECT 'sales-opportunities' UNION SELECT 'quotes'
  UNION SELECT 'contracts' UNION SELECT 'orders' UNION SELECT 'tasks' UNION SELECT 'products'
  UNION SELECT 'marketing' UNION SELECT 'tickets' UNION SELECT 'knowledge' UNION SELECT 'exports'
  UNION SELECT 'at-risk' UNION SELECT 'leaderboard' UNION SELECT 'reports' UNION SELECT 'suggestions'
  UNION SELECT 'board' UNION SELECT 'users' UNION SELECT 'departments' UNION SELECT 'workflows'
  UNION SELECT 'sla-policies' UNION SELECT 'custom-fields' UNION SELECT 'contract-templates'
  UNION SELECT 'audit-logs' UNION SELECT 'recycle-bin'
) m
WHERE r.code = 'ADMIN';

INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'leads' UNION SELECT 'customers' UNION SELECT 'contacts'
  UNION SELECT 'opportunities' UNION SELECT 'sales-opportunities' UNION SELECT 'quotes'
  UNION SELECT 'contracts' UNION SELECT 'orders' UNION SELECT 'tasks' UNION SELECT 'products'
  UNION SELECT 'marketing' UNION SELECT 'exports' UNION SELECT 'at-risk' UNION SELECT 'leaderboard'
  UNION SELECT 'reports' UNION SELECT 'suggestions' UNION SELECT 'board'
) m
WHERE r.code = 'SALES';

INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'customers' UNION SELECT 'contacts' UNION SELECT 'tickets'
  UNION SELECT 'knowledge' UNION SELECT 'exports'
) m
WHERE r.code = 'SUPPORT';

-- ---------- 权限字典 ----------
-- customer:create/update/delete/transfer/import lead:create/update/delete/convert/assign
-- opportunity:create/update/delete order:create/update/delete/payment
-- contract:create/update/delete/approve quote:create/update/delete/approve
-- ticket:create/update/delete/assign/reply user:manage role:manage workflow:manage report:manage system:manage

INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'customer:create' AS permission_code UNION SELECT 'customer:update' UNION SELECT 'customer:delete'
  UNION SELECT 'customer:transfer' UNION SELECT 'customer:import' UNION SELECT 'lead:create'
  UNION SELECT 'lead:update' UNION SELECT 'lead:delete' UNION SELECT 'lead:convert' UNION SELECT 'lead:assign'
  UNION SELECT 'opportunity:create' UNION SELECT 'opportunity:update' UNION SELECT 'opportunity:delete'
  UNION SELECT 'order:create' UNION SELECT 'order:update' UNION SELECT 'order:delete' UNION SELECT 'order:payment'
  UNION SELECT 'contract:create' UNION SELECT 'contract:update' UNION SELECT 'contract:delete' UNION SELECT 'contract:approve'
  UNION SELECT 'quote:create' UNION SELECT 'quote:update' UNION SELECT 'quote:delete' UNION SELECT 'quote:approve'
  UNION SELECT 'ticket:create' UNION SELECT 'ticket:update' UNION SELECT 'ticket:delete' UNION SELECT 'ticket:assign'
  UNION SELECT 'ticket:reply' UNION SELECT 'user:manage' UNION SELECT 'role:manage' UNION SELECT 'workflow:manage'
  UNION SELECT 'report:manage' UNION SELECT 'system:manage'
) p
WHERE r.code = 'ADMIN';

INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'customer:create' AS permission_code UNION SELECT 'customer:update' UNION SELECT 'customer:transfer'
  UNION SELECT 'lead:create' UNION SELECT 'lead:update' UNION SELECT 'lead:convert' UNION SELECT 'lead:assign'
  UNION SELECT 'opportunity:create' UNION SELECT 'opportunity:update'
  UNION SELECT 'order:create' UNION SELECT 'order:update' UNION SELECT 'order:payment'
  UNION SELECT 'contract:create' UNION SELECT 'contract:update'
  UNION SELECT 'quote:create' UNION SELECT 'quote:update'
) p
WHERE r.code = 'SALES';

INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'customer:create' AS permission_code UNION SELECT 'customer:update'
  UNION SELECT 'ticket:create' UNION SELECT 'ticket:update' UNION SELECT 'ticket:assign' UNION SELECT 'ticket:reply'
) p
WHERE r.code = 'SUPPORT';
