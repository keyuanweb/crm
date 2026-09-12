-- ============================================================
-- V84__permission_matrix_alignment_batch2d.sql — 权限矩阵对齐（1.5）批 2·合同/客户/字段
--
-- 本批 = ContractRenewalController / ContractAttachmentController / CustomerShareController /
--        CustomFieldController 四个控制器。判据同 V80 ~ V83，下界同样已逐条核对。
--
-- 附件与客户共享**一个码都不补**（附件复用 contract:read/contract:update，共享复用 customer:update）：
-- 改造前放行的 ADMIN/SALES/SUPPORT 本来就有这些码，顺带受益的是 SALES_MANAGER / SALES_REP —— 他们
-- 持有对应菜单与这些码，此前却被类级门挡在外面（"让矩阵成真"的正向情形）。
-- ============================================================

-- 1. contract:renewal（此前**无人持有、也无人引用**——"矩阵上有、实际没有"的典型）→ 续约漏斗。
--    授予范围 = 改造前 hasAnyRole('ADMIN','SALES') 放行的两个角色 ∪ 销售侧持有「合同」菜单的
--    SALES_MANAGER / SALES_REP。财务与 VIEWER 不授：他们能读合同（contract:read），但续约漏斗是销售的
--    作战视图，不是一个更宽的合同读面。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'contract:renewal' FROM `role` r
WHERE r.code IN ('ADMIN', 'SALES', 'SALES_MANAGER', 'SALES_REP');

-- 2. 同一批给这四个角色补「合同续约」菜单。
--    前端路由（ContractRenewalPage）、接口（GET /contracts/renewal-overview）、MENU_TREE 里的条目都齐全，
--    却**没有任何角色**持有这个菜单键 —— 于是"合同续约"这个功能对所有人不可见（只有 ADMIN 靠菜单兜底能看见）。
--    这与 V80 给三个客服角色补「满意度调查」菜单同一形态：把已经做好的功能还给它的角色。
--    本迁移唯一一条"给无人持有的菜单"的授权，单列于此便于复核。
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, 'contract-renewal' FROM `role` r
WHERE r.code IN ('ADMIN', 'SALES', 'SALES_MANAGER', 'SALES_REP');

-- 3. custom_field:read（本迁移新增读码）→ 字段定义**配置面**（全量列表，含未启用项与校验规则）。
--    授予范围 = 持有「自定义字段」菜单的角色（V75 给 ANALYST；ADMIN 靠菜单兜底 + 切面直通）。
--    注意 /definitions（表单渲染用的元数据读）**不设码**：客户/线索/商机/工单四个列表页都要它。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'custom_field:read' FROM `role` r WHERE r.code IN ('ADMIN', 'ANALYST');

-- 4. custom_field:create/update/delete → ADMIN。
--    ANALYST 在 V75 里已持有这三个码（V80 才把它们补进字典，此前它在角色页上根本勾不出来），
--    而该 Controller 一直是类级 hasRole('ADMIN')：撤门接线后 ANALYST 真的能配字段了。
--    ADMIN 列出只为角色页勾选状态与实际一致；SUPPORT 等角色不授——SystemEnhancementIT 钉着
--    "SUPPORT 建字段 → 403"，那条断言改由 custom_field:create 满足。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code FROM `role` r
JOIN (
  SELECT 'custom_field:create' AS permission_code UNION SELECT 'custom_field:update'
  UNION SELECT 'custom_field:delete'
) p
WHERE r.code = 'ADMIN';
