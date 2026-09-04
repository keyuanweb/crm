-- ============================================================
-- V75__role_permissions_update.sql — 角色权限更新（081-role-permissions-update）
-- 说明：基于当前菜单和功能模块，更新角色权限配置
--       新增 8 个预置角色，更新权限矩阵
-- ============================================================

-- ---------- 新增角色 ----------
INSERT INTO `role` (`code`, `name`, `description`, `data_scope`, `enabled`, `built_in`) VALUES
('SALES_MANAGER', '销售总监', '销售团队管理者，查看团队数据', 'DEPT', 1, 1),
('SALES_REP', '销售代表', '一线销售人员，管理自己的客户和商机', 'SELF', 1, 1),
('SUPPORT_MANAGER', '客服主管', '客服团队管理者', 'DEPT', 1, 1),
('SUPPORT_AGENT', '客服专员', '一线客服人员，处理工单和咨询', 'SELF', 1, 1),
('MARKETING_MANAGER', '市场总监', '市场团队管理者', 'DEPT', 1, 1),
('MARKETING_SPECIALIST', '市场专员', '市场活动执行者', 'SELF', 1, 1),
('FINANCE_MANAGER', '财务总监', '财务相关功能管理者', 'DEPT', 1, 1),
('FINANCE_ACCOUNTANT', '财务专员', '财务相关操作者', 'SELF', 1, 1),
('ANALYST', '数据分析师', '数据查看和报表分析', 'ALL', 1, 1),
('VIEWER', '只读用户', '仅查看权限，无编辑权限', 'SELF', 1, 1);

-- ---------- SALES_MANAGER 菜单 ----------
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'leads' UNION SELECT 'customers' UNION SELECT 'contacts'
  UNION SELECT 'opportunities' UNION SELECT 'sales-opportunities' UNION SELECT 'quotes'
  UNION SELECT 'contracts' UNION SELECT 'orders' UNION SELECT 'invoices' UNION SELECT 'tasks'
  UNION SELECT 'products' UNION SELECT 'marketing' UNION SELECT 'exports' UNION SELECT 'at-risk'
  UNION SELECT 'leaderboard' UNION SELECT 'reports' UNION SELECT 'suggestions' UNION SELECT 'users'
  UNION SELECT 'departments' UNION SELECT 'roles' UNION SELECT 'announcements'
) m
WHERE r.code = 'SALES_MANAGER';

-- ---------- SALES_REP 菜单 ----------
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'leads' UNION SELECT 'customers' UNION SELECT 'contacts'
  UNION SELECT 'opportunities' UNION SELECT 'sales-opportunities' UNION SELECT 'quotes'
  UNION SELECT 'contracts' UNION SELECT 'orders' UNION SELECT 'invoices' UNION SELECT 'tasks'
  UNION SELECT 'products' UNION SELECT 'exports' UNION SELECT 'at-risk' UNION SELECT 'leaderboard'
  UNION SELECT 'reports' UNION SELECT 'suggestions'
) m
WHERE r.code = 'SALES_REP';

-- ---------- SUPPORT_MANAGER 菜单 ----------
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'customers' UNION SELECT 'contacts' UNION SELECT 'tickets'
  UNION SELECT 'knowledge' UNION SELECT 'exports' UNION SELECT 'tasks' UNION SELECT 'reports'
  UNION SELECT 'sla-policies' UNION SELECT 'sla-calendar' UNION SELECT 'users' UNION SELECT 'departments'
  UNION SELECT 'roles' UNION SELECT 'announcements'
) m
WHERE r.code = 'SUPPORT_MANAGER';

-- ---------- SUPPORT_AGENT 菜单 ----------
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'customers' UNION SELECT 'contacts' UNION SELECT 'tickets'
  UNION SELECT 'knowledge' UNION SELECT 'exports' UNION SELECT 'tasks' UNION SELECT 'reports'
) m
WHERE r.code = 'SUPPORT_AGENT';

-- ---------- MARKETING_MANAGER 菜单 ----------
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'marketing' UNION SELECT 'products' UNION SELECT 'exports'
  UNION SELECT 'leaderboard' UNION SELECT 'reports' UNION SELECT 'users' UNION SELECT 'departments'
  UNION SELECT 'roles' UNION SELECT 'landing-pages' UNION SELECT 'online-forms' UNION SELECT 'announcements'
) m
WHERE r.code = 'MARKETING_MANAGER';

-- ---------- MARKETING_SPECIALIST 菜单 ----------
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'marketing' UNION SELECT 'products' UNION SELECT 'exports'
  UNION SELECT 'leaderboard' UNION SELECT 'reports' UNION SELECT 'landing-pages' UNION SELECT 'online-forms'
) m
WHERE r.code = 'MARKETING_SPECIALIST';

-- ---------- FINANCE_MANAGER 菜单 ----------
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'orders' UNION SELECT 'invoices' UNION SELECT 'contracts'
  UNION SELECT 'exports' UNION SELECT 'leaderboard' UNION SELECT 'reports' UNION SELECT 'users'
  UNION SELECT 'departments' UNION SELECT 'roles' UNION SELECT 'currencies' UNION SELECT 'announcements'
) m
WHERE r.code = 'FINANCE_MANAGER';

-- ---------- FINANCE_ACCOUNTANT 菜单 ----------
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'orders' UNION SELECT 'invoices' UNION SELECT 'contracts'
  UNION SELECT 'exports' UNION SELECT 'leaderboard' UNION SELECT 'reports'
) m
WHERE r.code = 'FINANCE_ACCOUNTANT';

-- ---------- ANALYST 菜单 ----------
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'exports' UNION SELECT 'reports' UNION SELECT 'data-vision'
  UNION SELECT 'custom-fields' UNION SELECT 'custom-objects' UNION SELECT 'usage-map'
) m
WHERE r.code = 'ANALYST';

-- ---------- VIEWER 菜单 ----------
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key
FROM `role` r
JOIN (
  SELECT 'stats' AS menu_key UNION SELECT 'customers' UNION SELECT 'contacts' UNION SELECT 'opportunities'
  UNION SELECT 'contracts' UNION SELECT 'orders' UNION SELECT 'invoices' UNION SELECT 'tickets'
  UNION SELECT 'tasks' UNION SELECT 'leaderboard' UNION SELECT 'reports'
) m
WHERE r.code = 'VIEWER';

-- ---------- SALES_MANAGER 权限 ----------
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
  UNION SELECT 'user:manage' UNION SELECT 'role:manage' UNION SELECT 'workflow:manage'
  UNION SELECT 'report:manage' UNION SELECT 'system:manage'
) p
WHERE r.code = 'SALES_MANAGER';

-- ---------- SALES_REP 权限 ----------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'customer:create' AS permission_code UNION SELECT 'customer:update' UNION SELECT 'customer:delete'
  UNION SELECT 'lead:create' UNION SELECT 'lead:update' UNION SELECT 'lead:delete' UNION SELECT 'lead:convert'
  UNION SELECT 'opportunity:create' UNION SELECT 'opportunity:update' UNION SELECT 'opportunity:delete'
  UNION SELECT 'order:create' UNION SELECT 'order:update' UNION SELECT 'order:delete'
  UNION SELECT 'contract:create' UNION SELECT 'contract:update' UNION SELECT 'contract:delete'
  UNION SELECT 'quote:create' UNION SELECT 'quote:update' UNION SELECT 'quote:delete'
  UNION SELECT 'task:create' UNION SELECT 'task:update' UNION SELECT 'task:delete'
  UNION SELECT 'follow_up:create' UNION SELECT 'follow_up:update' UNION SELECT 'follow_up:delete'
  UNION SELECT 'call_record:create' UNION SELECT 'call_record:update' UNION SELECT 'call_record:delete'
) p
WHERE r.code = 'SALES_REP';

-- ---------- SUPPORT_MANAGER 权限 ----------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'ticket:create' AS permission_code UNION SELECT 'ticket:update' UNION SELECT 'ticket:delete'
  UNION SELECT 'ticket:assign' UNION SELECT 'ticket:reply' UNION SELECT 'ticket:approve'
  UNION SELECT 'knowledge:create' UNION SELECT 'knowledge:update' UNION SELECT 'knowledge:delete'
  UNION SELECT 'task:create' UNION SELECT 'task:update' UNION SELECT 'task:delete'
  UNION SELECT 'follow_up:create' UNION SELECT 'follow_up:update' UNION SELECT 'follow_up:delete'
  UNION SELECT 'call_record:create' UNION SELECT 'call_record:update' UNION SELECT 'call_record:delete'
  UNION SELECT 'user:manage' UNION SELECT 'role:manage' UNION SELECT 'sla:manage'
) p
WHERE r.code = 'SUPPORT_MANAGER';

-- ---------- SUPPORT_AGENT 权限 ----------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'ticket:create' AS permission_code UNION SELECT 'ticket:update' UNION SELECT 'ticket:delete'
  UNION SELECT 'ticket:reply' UNION SELECT 'knowledge:create' UNION SELECT 'knowledge:update'
  UNION SELECT 'knowledge:delete' UNION SELECT 'task:create' UNION SELECT 'task:update'
  UNION SELECT 'task:delete' UNION SELECT 'follow_up:create' UNION SELECT 'follow_up:update'
  UNION SELECT 'follow_up:delete' UNION SELECT 'call_record:create' UNION SELECT 'call_record:update'
  UNION SELECT 'call_record:delete'
) p
WHERE r.code = 'SUPPORT_AGENT';

-- ---------- MARKETING_MANAGER 权限 ----------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'campaign:create' AS permission_code UNION SELECT 'campaign:update' UNION SELECT 'campaign:delete'
  UNION SELECT 'landing_page:create' UNION SELECT 'landing_page:update' UNION SELECT 'landing_page:delete'
  UNION SELECT 'online_form:create' UNION SELECT 'online_form:update' UNION SELECT 'online_form:delete'
  UNION SELECT 'product:create' UNION SELECT 'product:update' UNION SELECT 'product:delete'
  UNION SELECT 'user:manage' UNION SELECT 'role:manage' UNION SELECT 'workflow:manage'
  UNION SELECT 'report:manage' UNION SELECT 'system:manage'
) p
WHERE r.code = 'MARKETING_MANAGER';

-- ---------- MARKETING_SPECIALIST 权限 ----------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'campaign:create' AS permission_code UNION SELECT 'campaign:update' UNION SELECT 'campaign:delete'
  UNION SELECT 'landing_page:create' UNION SELECT 'landing_page:update' UNION SELECT 'landing_page:delete'
  UNION SELECT 'online_form:create' UNION SELECT 'online_form:update' UNION SELECT 'online_form:delete'
) p
WHERE r.code = 'MARKETING_SPECIALIST';

-- ---------- FINANCE_MANAGER 权限 ----------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'order:create' AS permission_code UNION SELECT 'order:update' UNION SELECT 'order:delete'
  UNION SELECT 'invoice:create' UNION SELECT 'invoice:update' UNION SELECT 'invoice:delete'
  UNION SELECT 'contract:create' UNION SELECT 'contract:update' UNION SELECT 'contract:delete'
  UNION SELECT 'contract:approve' UNION SELECT 'currency:manage'
  UNION SELECT 'user:manage' UNION SELECT 'role:manage' UNION SELECT 'workflow:manage'
  UNION SELECT 'report:manage' UNION SELECT 'system:manage'
) p
WHERE r.code = 'FINANCE_MANAGER';

-- ---------- FINANCE_ACCOUNTANT 权限 ----------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'order:create' AS permission_code UNION SELECT 'order:update' UNION SELECT 'order:delete'
  UNION SELECT 'invoice:create' UNION SELECT 'invoice:update' UNION SELECT 'invoice:delete'
  UNION SELECT 'contract:create' UNION SELECT 'contract:update' UNION SELECT 'contract:delete'
) p
WHERE r.code = 'FINANCE_ACCOUNTANT';

-- ---------- ANALYST 权限 ----------
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'report:create' AS permission_code UNION SELECT 'report:update' UNION SELECT 'report:delete'
  UNION SELECT 'custom_field:create' UNION SELECT 'custom_field:update' UNION SELECT 'custom_field:delete'
  UNION SELECT 'custom_object:create' UNION SELECT 'custom_object:update' UNION SELECT 'custom_object:delete'
) p
WHERE r.code = 'ANALYST';

-- ---------- VIEWER 权限（仅查看） ----------
-- VIEWER 角色不插入任何操作权限，仅通过菜单权限控制查看
