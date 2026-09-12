-- ============================================================
-- V86__currency_read_and_gate.sql — 多币种读写分码（084 T020）
--
-- 这是 T015 报出的最后一条 C1 红项，用户裁决为「接线到权限码」而非「收回菜单授权」（2026-09-12）：
--
--   FINANCE_MANAGER 看得到「多币种」菜单（V75 授的 role_menu），点开却恒 403 ——
--   CurrencyRateController 是角色字面量 hasAnyRole('ADMIN','SALES')，而菜单的持有者从来不在这个集合里。
--   更刺眼的是：**字典里早就有 currency:manage，V75 也把它授给了 FINANCE_MANAGER**，却没有任何端点
--   校验它——「矩阵上写了、实际拿不到」的典型，正是 1.5（V80~V84）一直在消灭的那类。
--
-- 改法：读写分码。读（GET /currencies、POST /currencies/convert）用**新增**读码 currency:read；
-- 写（POST / PUT / DELETE）用既有的 currency:manage。「能看汇率」与「能改汇率」不应是同一个集合——
-- 否则只读角色要么看不到、要么连带拿到改汇率的权限（RequirePermission 的类注释里写的就是这条判据）。
--
-- 扩权范围（FR-N24，需批准的偏差，已批准）：
--   · FINANCE_MANAGER：从"看得见、打不开"变为真的能读**也能改**汇率。改的能力来自它 V75 就已持有的
--     currency:manage——按用户确认的口径「接受角色可用范围扩到其已发布矩阵所宣称的范围」，这不是新授，
--     而是把已发布矩阵兑现。它是唯一持有该码的角色（ADMIN 靠切面直通，另有菜单兜底）。
--   · SALES：改造前靠角色字面量能读汇率与做折算，故补授 currency:read 以**保持现状不变**（它的
--     currencies 菜单为空，这份访问是历史遗留）。写码不授——SALES 改造前也改不了汇率。
--   · 除上述外无任何角色获得任何新能力；删除/新增币种仍只有 ADMIN 与 FINANCE_MANAGER 能做。
-- ============================================================

INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'currency:read' FROM `role` r
WHERE r.code IN ('ADMIN', 'SALES', 'FINANCE_MANAGER');
