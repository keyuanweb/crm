-- ============================================================
-- V83__permission_matrix_alignment_batch2c.sql — 权限矩阵对齐（1.5）批 2·数据侧
--
-- 本批 = StatsController / ReportController / ExportController / SignatureController /
--        SalesOpportunityController / ProductPriceController 六个控制器。
-- 判据同 V80 / V81 / V82：
--   ① 改造前有粗粒度门 → 补到门原先放行的集合（保能力，防"把能用的打成 403"）
--   ② 无闸门 → 按菜单承诺补
--   ③ 既无闸门也无菜单 → 一个码都不补（只把端点接上码，让管理员能勾）
--   ④ 已被任何端点校验过的码，绝不补授（那是扩权）
--
-- 下界说明：以下 (角色, 码) 组合都已核对为当前不存在（V46/V75/V80/V81/V82 全查），故无需防重
-- ——role_permission 上没有唯一键，重复插入不会报错、只会留下一行垃圾。
-- ============================================================

-- 1. kpi:view（本迁移新增读码）→ 数据大屏。
--    「数据大屏」菜单（data-vision）只有 ANALYST 持有，而 kpi-board 此前是方法级 hasRole('ADMIN')：
--    ANALYST 点得进大屏、接口恒 403。只授 ANALYST + ADMIN，不授 SALES —— KpiBoardIT 钉着
--    "SALES 访问 kpi-board → 403"，那条断言原先由 hasRole('ADMIN') 满足，撤掉之后改由
--    "码存在且未授给 SALES" 满足。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'kpi:view' FROM `role` r WHERE r.code IN ('ADMIN', 'ANALYST');

-- 2. report:view（新增读码）→ 恰好是「自定义报表」菜单的全部持有者：V46 给 ADMIN/SALES，V75 给其余 11 个。
--    看上去等于"发给所有人"，但仍然建码：报表查询是**任意维度**的聚合面（PRODUCT/STAGE/TIME 三个维度没有
--    任何数据范围过滤），而角色页允许管理员自建角色 —— 留空等于"任何新建角色自动拿到全量数据报表"。
--    ADMIN 列出只为角色页的勾选状态与实际一致（切面里 ADMIN 是直通的）。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'report:view' FROM `role` r
WHERE r.code IN (
  'ADMIN', 'SALES', 'SALES_MANAGER', 'SALES_REP', 'SUPPORT', 'SUPPORT_MANAGER', 'SUPPORT_AGENT',
  'MARKETING_MANAGER', 'MARKETING_SPECIALIST', 'FINANCE_MANAGER', 'FINANCE_ACCOUNTANT',
  'ANALYST', 'VIEWER');

-- 3. export:create → **只**补改造前那道门放行的三个角色，不按「导出中心」菜单扩（该菜单有 12 个持有者）。
--    理由：ExportExecutor.writeOpportunities / writeTickets 两条导出**没有范围过滤**，是整表导出。
--    与 V80 里 lead:export 的判断同一条原则——把一份无过滤的全量导出顺手扩给 9 个角色，不该由"权限接线"
--    完成；等这两条导出补上范围过滤，再按菜单扩。已知代价：那 9 个角色打开导出中心点"新建导出"会 403，
--    与 CommentController 同类的一处**待裁决的锁死**（见 1.5 报告）。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'export:create' FROM `role` r WHERE r.code IN ('ADMIN', 'SALES', 'SUPPORT');

-- 4. product:update → 产品定价。授予范围 = 改造前 hasAnyRole('ADMIN','SALES') 放行的两个角色
--    ∪「产品」菜单的持有者（SALES_MANAGER / SALES_REP / MARKETING_SPECIALIST）。
--    MARKETING_MANAGER 在 V75 里已持有（它也是唯一持有 product:create/update/delete 的角色），故不重复插入。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'product:update' FROM `role` r
WHERE r.code IN ('SALES', 'SALES_MANAGER', 'SALES_REP', 'MARKETING_SPECIALIST');
