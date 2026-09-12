-- 1.5 权限接线（批 2 · 协作/工作台）：把类级 @PreAuthorize 换成权限码之后的补授。
--
-- 补授判据与 V80/V81 同一套（详见 V81 开头的三条）。本迁移覆盖六个协作/工作台控制器：
--   NotificationController   → 撤门，**不设码**（自限范围：四个方法都只操作本人的通知）
--   SuggestionController     → 撤门，**不设码**（数据来自已按范围过滤的 Service；ignore 是每用户 Redis 键）
--   CommentController        → **不动**（无码可接：撤门会让只读角色获得写能力，详见该类的 Javadoc）
--   FollowUpController       → 四码：follow_up:read(新) / create / update
--   CallRecordController     → 四码：call_record:read(新) / create / update / delete
--   TaskController           → 三码：task:create / update / delete（读不设码：按 owner 自限）
--
-- 共同形态：这三个控制器的类级门都是 hasAnyRole('ADMIN','SALES','SUPPORT')——只认三个字面量角色名，
-- 于是 081 新增的 SALES_MANAGER / SALES_REP / SUPPORT_MANAGER / SUPPORT_AGENT 整体被挡在门外，
-- 而 V75 恰恰把 follow_up:* / call_record:* / task:* 授给了其中三个角色：矩阵说能、端点说 403。
--
-- 两个新码（follow_up:read / call_record:read）的由来：这两个模块的**读路径没有数据范围**——
-- FollowUpService.page 只在传了实体 id 时才逐条校验可见性（三个都不传就是全表分页），
-- CallRecordService 干脆一条范围过滤都没有。类级门一撤，读就必须设码，否则任何登录用户都能拉走
-- 全部跟进记录与通话记录。字典里原先只有写码，所以读码是新增的，与 invoice:read / ticket:read 同一口径。

-- ---------- 跟进记录 ----------
-- 读：门事实放行的 ADMIN/SALES/SUPPORT ∪ 已持有 follow_up:* 的三个 081 角色
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'follow_up:read'
FROM `role` r
WHERE r.code IN (
  'ADMIN', 'SALES', 'SUPPORT', 'SALES_REP', 'SUPPORT_MANAGER', 'SUPPORT_AGENT');

-- 写：SALES / SUPPORT 原先靠那道门能写却没码（补授 = 保留事实能力），ADMIN 照例一并列出；
-- SALES_REP / SUPPORT_MANAGER / SUPPORT_AGENT 在 V75 里本就有 create/update，无需重复。
-- ⚠️ SALES_MANAGER **刻意不补**：V75 给它的授权清单里逐条列了 customer/lead/opportunity/order/contract
-- 的动作码，却唯独没有 follow_up:* / call_record:* —— 与 task:* 的取舍不同（见下），
-- 视为"经理审阅、执行者记录"的有意分工。若评审认为经理也该记录跟进，正确做法是在角色页勾选，
-- 而不是在这里替它决定。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'follow_up:create' AS permission_code UNION SELECT 'follow_up:update'
) p
WHERE r.code IN ('ADMIN', 'SALES', 'SUPPORT');

-- ---------- 通话记录 ----------
-- 读：同上（本模块的读是全量的，所以这是四码里最关键的一个）
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'call_record:read'
FROM `role` r
WHERE r.code IN (
  'ADMIN', 'SALES', 'SUPPORT', 'SALES_REP', 'SUPPORT_MANAGER', 'SUPPORT_AGENT');

-- 写：SALES / SUPPORT 补授（保留事实能力），与跟进记录同一范围
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'call_record:create' AS permission_code UNION SELECT 'call_record:update'
  UNION SELECT 'call_record:delete'
) p
WHERE r.code IN ('ADMIN', 'SALES', 'SUPPORT');

-- ---------- 任务 ----------
-- 这里比上两个模块多一个 SALES_MANAGER，理由是菜单：它持有「任务管理」菜单（V75），任务页读不设码，
-- 若不给写码就会出现"页面能开、新建按钮 403"的新版错配——正是 1.5 要消灭的形态。
-- 同理 ADMIN/SALES/SUPPORT/SUPPORT_MANAGER/SUPPORT_AGENT/SALES_REP 全给。
-- VIEWER 也持有该菜单，但它是种子数据里唯一"不插任何操作权限"的角色（V46 注释原话），故排除；
-- 它仍能打开任务页看到自己的任务，只是不能新建/编辑/删除。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code
FROM `role` r
JOIN (
  SELECT 'task:create' AS permission_code UNION SELECT 'task:update' UNION SELECT 'task:delete'
) p
WHERE r.code IN (
  'ADMIN', 'SALES', 'SUPPORT', 'SALES_MANAGER', 'SALES_REP', 'SUPPORT_MANAGER', 'SUPPORT_AGENT');
