-- 1.5 权限接线（批 2）：把 ADMIN-only / 粗粒度的类级 @PreAuthorize 换成矩阵码之后的补授。
--
-- 与 V80 的关系：V80 覆盖批 1（核心实体 + 读码），本迁移只覆盖批 2 的七个 ADMIN-only 模块
-- （SLA 策略 / SLA 日历 / 审计日志 / 回收站 / 字段权限 / 部门 / 集成中心）。
--
-- 补授判据——三条，缺一不可，按顺序判：
--   ① 端点改造前**有闸门**（类级/方法级 @PreAuthorize，**或服务层全限定写法的
--      @com.crm.security.RequirePermission**）→ 补授范围 = 那个闸门原先放行的角色集合，一个不多一个不少
--      （保留事实能力）。⚠️ 服务层那种全限定写法 grep "@RequirePermission" 看不见，V80 曾因此把
--      customer:delete 误判为"无闸门"而补授，实测把 RoleIT 的 403 打成了 404。盘点时必须查两种写法。
--   ② 端点改造前**无闸门** → 矩阵说了算：补授给持有对应菜单的角色（菜单就是已发布的承诺）。
--   ③ 既无闸门、菜单也没承诺 → **一个码都不补**，只把端点接到码上，让管理员能在角色页上开口。
--
-- 本批七项按此判据的结论：
--   部门 department:manage      → 判据②（菜单「部门管理」授给了五个管理角色）→ **补授**，见下。
--   SLA 策略/日历 sla:manage    → 该码 V75 已授给 SUPPORT_MANAGER，不需补。
--   审计日志 audit:view         → 判据③（菜单只 ADMIN 持有、码无人持有）→ 不补，保持仅 ADMIN。
--   回收站 recycle:*            → 判据③ → 不补。
--   字段权限 field_permission:manage → 判据③ → 不补。
--   集成中心 integration:manage → 判据③（本批新增的码）→ 不补。

-- ---------- 部门管理 → 持有「部门管理」菜单的五个角色 ----------
-- 这是 1.5 里**唯一一处有实质扩权**的授权，明确记录：V46/V75 把「部门管理」菜单授给了
-- SALES_MANAGER / SUPPORT_MANAGER / MARKETING_MANAGER / FINANCE_MANAGER（+ ADMIN），而这四个角色
-- 同时持有 user:manage 与 role:manage——它们本来就是各自组织单元的管理员，部门管理是同一件事的第三块
-- 拼图。但 DepartmentController 是类级 hasRole('ADMIN')，于是这四个角色看得见菜单、点进去恒 403。
-- 撤门 + 补授 = 矩阵说能就能（用户已确认的口径：接受角色可用范围扩到其已发布矩阵所宣称的范围）。
-- 若评审要收回，正确做法是同时收回它们的「部门管理」菜单，而不是让菜单继续指向 403。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'department:manage'
FROM `role` r
WHERE r.code IN (
  'ADMIN', 'SALES_MANAGER', 'SUPPORT_MANAGER', 'MARKETING_MANAGER', 'FINANCE_MANAGER');
