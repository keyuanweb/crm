-- ============================================================
-- V80__permission_matrix_alignment.sql — 权限矩阵对齐（1.5）
--
-- 背景：菜单键与权限码在 MENU_TREE（角色页勾选项的唯一来源）/ 前端 menuKeyOf / 种子数据
--       三处各写一份，净效果是「矩阵上写了、实际拿不到」，而且 RoleService.replacePermissions
--       是先删后插——管理员在角色页保存一次，那些「字典里没有」的授权就被静默删除。
--
-- 本迁移只做对齐，不新增功能：
--   1) 菜单键统一到 MENU_TREE 的拼写
--   2) 删除指向已不存在功能的死授权
--   3) 把 seed 承诺的能力对齐到「注解实际校验的码」（能力不变，只换码）
--   4) 补授：菜单已承诺、但码从未授出的功能
--
-- 下界说明：所有 INSERT 的 (角色, 码/菜单) 组合都已核对为当前不存在，故无需防重。
-- ============================================================

-- ---------- 1. 菜单键统一 ----------
-- V46/V75 用的是 'leaderboard' / 'custom-fields'；MENU_TREE 里是 'stats/leaderboard' /
-- 'settings/custom-fields'，前端 menuKeyOf 本次也一并改为取 MENU_TREE 的拼写。改这三处而不改
-- MENU_TREE：MENU_TREE 是角色页勾选项的来源，也是 resolve 后下发给前端的权威名单。
UPDATE `role_menu` SET `menu_key` = 'stats/leaderboard' WHERE `menu_key` = 'leaderboard';
UPDATE `role_menu` SET `menu_key` = 'settings/custom-fields' WHERE `menu_key` = 'custom-fields';

-- 'board'（V46 给 ADMIN/SALES）与 'usage-map'（V75 给 ANALYST）指向的菜单已不存在：前者没有任何路由，
-- 后者是顶栏按钮（直接走 /usage-map，不经菜单过滤）。留着只会在下次保存该角色时被静默删除。
DELETE FROM `role_menu` WHERE `menu_key` IN ('board', 'usage-map');

-- ---------- 2. 权限码对齐（能力不变，只换码）----------
-- ANALYST 持有 report:create/update/delete，而 ReportController 校验的是 report:manage。
-- 归并到后者：管理角色的 report:manage 与分析师的三件套本就描述同一件事。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'report:manage' FROM `role` r WHERE r.code = 'ANALYST';
DELETE FROM `role_permission`
WHERE `permission_code` IN ('report:create', 'report:update', 'report:delete');

-- MARKETING_* 持有 landing_page:* / online_form:*，而 LandingPageController / FormController 校验的是
-- marketing:manage / form:manage。同样归并：营销角色对落地页与在线表单的增删改能力保持不变。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'marketing:manage' FROM `role` r
WHERE r.code IN ('MARKETING_MANAGER', 'MARKETING_SPECIALIST');
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'form:manage' FROM `role` r
WHERE r.code IN ('MARKETING_MANAGER', 'MARKETING_SPECIALIST');
DELETE FROM `role_permission`
WHERE `permission_code` IN (
  'landing_page:create', 'landing_page:update', 'landing_page:delete',
  'online_form:create', 'online_form:update', 'online_form:delete');

-- ---------- 3. 补授：菜单承诺了、码却没授出的功能 ----------
-- 邮件营销：EmailController 的写操作与「含收件人邮箱」的退订名单读接口统一校验 email:manage，
-- 而 V75 只给 MARKETING_* 授了 campaign:* —— 营销角色连自己的邮件模板都动不了。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'email:manage' FROM `role` r
WHERE r.code IN ('MARKETING_MANAGER', 'MARKETING_SPECIALIST');

-- 发票：InvoiceController 的写操作校验 invoice:manage，而 FINANCE_* 持有的是
-- invoice:create/update/delete。两个码都保留（三件套描述细分动作，manage 是控制器的闸门），
-- 使「财务能开发票」这件事真正成立。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'invoice:manage' FROM `role` r
WHERE r.code IN ('FINANCE_MANAGER', 'FINANCE_ACCOUNTANT');

-- 发票的读接口校验 invoice:read（1.5 新拆出的码）。授予范围**恰好**是下一节里持有 'invoices' 菜单的
-- 全部角色（含本文件稍后才给 SALES 补的那一条），这样「菜单上有发票」与「打得开发票列表」是同一件事。
-- 给 VIEWER 的是读码而不是 invoice:manage：只读角色在种子里被明确描述为「无编辑权限」，
-- 复用写码会连开票与作废一起给出去。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'invoice:read' FROM `role` r
WHERE r.code IN (
  'ADMIN', 'SALES', 'SALES_MANAGER', 'SALES_REP', 'FINANCE_MANAGER', 'FINANCE_ACCOUNTANT', 'VIEWER');

-- 外勤拜访：FieldVisitController 校验 visit:manage，字典里没有更细的码，此前无人持有
-- → 所有非 ADMIN 角色连「记录一次拜访」都做不到，而 SALES_* 的菜单里本就有这一项。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'visit:manage' FROM `role` r
WHERE r.code IN ('SALES_MANAGER', 'SALES_REP', 'SALES');

-- 查重合并：CustomerMergeController 校验 customer:merge，此前无人持有
-- → 页面看得见（挂在「客户」菜单下）、按钮点不动。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'customer:merge' FROM `role` r
WHERE r.code IN ('SALES_MANAGER', 'SALES_REP', 'SALES');

-- 公告管理：AnnouncementController 的增改删校验 announcement:manage。公告是客服/行政职能，
-- 归客服主管；SALES_MANAGER 的菜单里也有「公告管理」，但只读——发布公告不该是销售经理的权限。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'announcement:manage' FROM `role` r WHERE r.code = 'SUPPORT_MANAGER';

-- 联系人：contact:create/update/delete 在 V46 与 V75 里**没有任何角色持有**（客户和线索都发了三件套，
-- 联系人漏了）。ContactController 从类级 hasAnyRole 换成动作码之后，这个空集合会把 SALES/SUPPORT
-- 从「能改联系人」直接打成 403。授予范围 = 原先那个粗粒度门放行的角色（SALES、SUPPORT，ADMIN 直通）
-- ∪ 矩阵里已在写客户/线索的角色（SALES_MANAGER、SALES_REP）——联系人从来跟客户是同一摊活。
-- 刻意不给 SUPPORT_AGENT/SUPPORT_MANAGER：它们的矩阵里连 customer:* 都没有，按同一口径不该开这个口子；
-- 也不给 VIEWER（只读角色只拿读权限，而联系人的读接口按 1.5 的口径不设码）。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code FROM `role` r
JOIN (
  SELECT 'contact:create' AS permission_code UNION SELECT 'contact:update'
  UNION SELECT 'contact:delete'
) p
WHERE r.code IN ('SALES', 'SUPPORT', 'SALES_MANAGER', 'SALES_REP');

-- 工单：TicketController 从类级 hasAnyRole('ADMIN','SALES','SUPPORT') 换成动作码。两个码要补：
--
-- (a) ticket:read（本迁移新增的读码）。工单的数据范围只覆盖 SALES 一个角色（TicketService.page 里
--     一句字面量 "SALES".equals(role)，detail() 则没有范围校验），其余角色一旦放进来就是全量可见，
--     故读必须设码，授予范围**恰好**是持有 'tickets' 菜单的角色（ADMIN 走切面直通）：
--     V46 给 SALES/SUPPORT、V75 给 SUPPORT_MANAGER/SUPPORT_AGENT/VIEWER 都发了这张菜单。
--     注意 SALES_MANAGER / SALES_REP **不在**名单里——它们的权限列表与菜单里都没有工单，
--     所以"销售总监看得到全部工单"这件事在矩阵里本来就不成立。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'ticket:read' FROM `role` r
WHERE r.code IN ('ADMIN', 'SALES', 'SUPPORT', 'SUPPORT_MANAGER', 'SUPPORT_AGENT', 'VIEWER');

-- (b) ticket:delete → SUPPORT。V46 给 SUPPORT 发了 create/update/assign/reply 却漏了 delete，
--     而它原先靠方法级 hasAnyRole('ADMIN','SUPPORT') 是**能删工单的**。不补这一条，
--     "把注解补齐"就会把客服已经能用的删除变成 403——正是本方案风险清单里的头号风险。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'ticket:delete' FROM `role` r WHERE r.code = 'SUPPORT';

-- 客户：**一个码都不补**。CustomerController 原本没有写注解（只有 import/export 两条挂 hasRole('ADMIN')），
-- 接上 customer:create/update/delete/import/export 后仍然一个码都不补，原因分两类：
--   · create / update / import / export：原先只有数据范围一层（import/export 另有 hasRole('ADMIN')），
--     所以"今天能改"是**没有闸门**，不是一项被授予的能力——接上注解即被收窄到持有该码的角色。
--     这正是 1.5 想要的收窄，不属于风险清单里的"把能用的打成 403"。
--   · delete：**本来就有闸门，且闸门就是同一个码**——`CustomerService.delete` 上挂着
--     `@com.crm.security.RequirePermission("customer:delete")`（服务层，全限定名写法）。
--     SALES 与 SUPPORT 在 V46/V75 里都没有这个码，所以它们在过去、现在、以后都是 403；
--     补授不是"保留能力"而是**扩权**，并且会立刻打红 `RoleIT.noPermissionReturns403`
--     （该用例的 403 正是由这条服务层注解产生的，而非控制器）。
--
-- ⚠️ 给后来者（本迁移踩过的坑）：`grep "@RequirePermission"` **看不见**全限定写法
-- `@com.crm.security.RequirePermission`。本仓现存 4 处这样的服务层注解——本类的 delete 与
-- `UserService` 的 3 处 `user:manage`。盘点"某端点改造前有没有闸门"时必须同时查两种写法，
-- 否则会把"已按码校验"误判成"无闸门"，进而补授出一次静默扩权（实测：RoleIT 由 403 变 404）。

-- ---------- 4. 菜单可见性 ----------
-- menuKeyOf 改取 MENU_TREE 拼写后，原先靠「粗粒度别名」蹭到菜单的角色需要显式授权。
-- 下面每条都只补「种子数据里确实没有」的组合（V46/V75 已核）。

-- 外勤拜访、查重合并 → 销售角色。这两项此前**无人真正持有**：/visits 走的是 MENU_TREE 里
-- 根本不存在的 'sales'，查重合并蹭的是 'customers' 别名。后果是 /visits 对所有非 ADMIN 恒不可见，
-- 而 SUPPORT / VIEWER 这类从不做合并的角色反而长期看着一个点不动按钮的页面——本次一并收窄到销售角色。
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key FROM `role` r
JOIN (SELECT 'visits' AS menu_key UNION SELECT 'customer-merge') m
WHERE r.code IN ('SALES_MANAGER', 'SALES_REP', 'SALES');

-- 发票：SALES 原先靠 /invoices → 'orders' 的别名看到这一项，逐字对齐后要显式补回，
-- 以免「改个拼写就悄悄少一个菜单」。其余角色（SALES_MANAGER / SALES_REP / FINANCE_* / VIEWER）
-- 在 V75 种子里本就持有 'invoices'，无需重复插入。
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, 'invoices' FROM `role` r WHERE r.code = 'SALES';

-- 邮件营销、邮件退订 → 营销角色。种子里营销角色只有 'landing-pages' / 'online-forms'，
-- 这两个页面从未授权过（「有权限却看不到」）。落地页与在线表单不重复插入。
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, m.menu_key FROM `role` r
JOIN (SELECT 'marketing/email' AS menu_key UNION SELECT 'email-unsubscribes') m
WHERE r.code IN ('MARKETING_MANAGER', 'MARKETING_SPECIALIST');

-- 「我的审批」是每个人的待办队列，之前挂在 'workflows' 上（只有持有工作流配置权限的人能看见自己的待办），
-- 现在拆成独立菜单项，授给所有角色（ADMIN 走菜单兜底）。
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, 'approvals' FROM `role` r WHERE r.code <> 'ADMIN';

-- 满意度调查：**本迁移中唯一一条"给一个此前无人持有的菜单"的授权**，单列于此以便复核。
-- 与上面几条不同（那几条是"改拼写会把已有可见性弄丢"的补救），这一条是补一个从未成立过的承诺：
-- MENU_TREE 里有 item("satisfaction","满意度调查")、前端有 /satisfaction 路由、后端有完整的工单满意度
-- 接口，但 role_menu 里**没有任何角色**持有它——页面属于"客户服务"分组，而客服角色从来看不见它。
-- 只授给三个客服角色，不含 VIEWER/SALES：满意度是客服的职能视图，而它在矩阵里没有被任何角色承诺过，
-- 所以这一条的边界是"把客服自己的功能还给客服"，不是"开放一个新页面"。
INSERT INTO `role_menu` (`role_id`, `menu_key`)
SELECT r.id, 'satisfaction' FROM `role` r
WHERE r.code IN ('SUPPORT', 'SUPPORT_MANAGER', 'SUPPORT_AGENT');

-- ============================================================
-- 5. 第二批接线：合同 / 订单 / 报价单 / 商机 / 线索 / 知识库
--
-- 与第 3 节同一形态、同一口径，只是文件不同。六条读码是本迁移新增的（字典里此前没有）：
-- 这些 Controller 的类级门被换成动作码之后，读接口如果留空，**任何登录用户**都能拉走全表
-- ——它们的 Service 里都没有数据范围过滤。授予范围一律是
--   「改造前那个类级门放行的角色」 ∪ 「持有对应菜单的角色（矩阵承诺了但被门挡住）」
-- 两条并集；ADMIN 在 RoleService 里对权限码是直通的，列出它只为让角色页的勾选状态与实际一致。
-- ============================================================

-- 线索：类级门原文是 hasAnyRole('ADMIN','SALES','SUPPORT')。
-- (a) SUPPORT 今天能建/改/删/转化/分配线索（它过了那个门，而 V46/V75 没给它任何一个 lead:*）。
--     不补这几条，接线就把客服已经能用的线索操作打成 403——方案风险清单里的头号风险。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code FROM `role` r
JOIN (
  SELECT 'lead:create' AS permission_code UNION SELECT 'lead:update'
  UNION SELECT 'lead:delete' UNION SELECT 'lead:convert' UNION SELECT 'lead:assign'
) p
WHERE r.code = 'SUPPORT';

-- (b) lead:delete → SALES：V46 给它发了 create/update/convert/assign 却漏了 delete，而接口
--     此前无方法级注解、类级门也放行，所以它今天**能删线索**。同第 3 节的 customer:delete 那条。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'lead:delete' FROM `role` r WHERE r.code = 'SALES';

-- (c) lead:assign → SALES_REP：同族规则（它持有其余四个 lead 写码），且「线索池」菜单下的领取
--     与分配是同一个动作——claim 端点校验的也是 lead:assign。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'lead:assign' FROM `role` r WHERE r.code = 'SALES_REP';

-- (d) lead:export：新增读码，授予范围＝改造前类级门放行的三个角色。
--     刻意**不**顺带扩给 SALES_MANAGER/SALES_REP：那个门从未放行它们，而这条导出是
--     LeadExcelService 自拼查询的**无范围过滤全量导出**（含手机号/邮箱），扩大它不该由
--     「接线」顺手完成；管理员若认为销售总监该有，可在角色页勾选——这正是把这个码写进字典的意义。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'lead:export' FROM `role` r
WHERE r.code IN ('ADMIN', 'SALES', 'SUPPORT');

-- 知识库：类级门是 hasAnyRole('ADMIN','SALES','SUPPORT')，五个写方法上是 hasAnyRole('ADMIN','SUPPORT')。
-- SUPPORT 因此**能维护文章**却一个 knowledge:* 都没有 → 三个写码补授给它；
-- SUPPORT_MANAGER/SUPPORT_AGENT 本来就持有 knowledge:create/delete/update（V75），无需补。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code FROM `role` r
JOIN (
  SELECT 'knowledge:create' AS permission_code UNION SELECT 'knowledge:update'
  UNION SELECT 'knowledge:delete'
) p
WHERE r.code = 'SUPPORT';

-- 知识库读码：门放行的 ADMIN/SALES/SUPPORT ∪ 持有 'knowledge' 菜单的客服角色。
-- 这一条比别处更必要：文章没有 owner 维度，Service 里也没有任何数据范围过滤，
-- 留空读接口等于把知识库对全体登录用户开放。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'knowledge:read' FROM `role` r
WHERE r.code IN ('ADMIN', 'SALES', 'SUPPORT', 'SUPPORT_MANAGER', 'SUPPORT_AGENT');

-- 商机：类级门是 hasAnyRole('ADMIN','SALES')。
-- (a) opportunity:delete → SALES：V46 给了 create/update 却漏了 delete（列表页的删除按钮今天可用）。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'opportunity:delete' FROM `role` r WHERE r.code = 'SALES';

-- (b) opportunity:read：授予门放行的 ADMIN/SALES ∪ 持有 'opportunities' 菜单的角色。
--     **刻意不含 SUPPORT**——OpportunityIT.supportRoleForbidden 断言客服读不到商机，
--     那条断言此前是被类级门顺手满足的；撤门后只有"码存在且不授给 SUPPORT"能继续满足它。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'opportunity:read' FROM `role` r
WHERE r.code IN ('ADMIN', 'SALES', 'SALES_MANAGER', 'SALES_REP', 'VIEWER');

-- 合同 / 订单：门都是 hasAnyRole('ADMIN','SALES')，菜单由 V46 发给 SALES、V75 发给
-- SALES_MANAGER/SALES_REP/FINANCE_*/VIEWER（订单的金额与回款本就是财务的日常输入）。
-- 与第 3 节的 invoice:read 同为「菜单即读权」口径。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code FROM `role` r
JOIN (
  SELECT 'contract:read' AS permission_code UNION SELECT 'order:read'
) p
WHERE r.code IN (
  'ADMIN', 'SALES', 'SALES_MANAGER', 'SALES_REP', 'FINANCE_MANAGER', 'FINANCE_ACCOUNTANT', 'VIEWER');

-- 报价单：门是 hasAnyRole('ADMIN','SALES')，菜单只有这两个角色加 V75 的两个销售角色持有
-- （FINANCE_*/VIEWER 没有 'quotes' 菜单，报价单在他们那里从来不可见，故不扩）。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'quote:read' FROM `role` r
WHERE r.code IN ('ADMIN', 'SALES', 'SALES_MANAGER', 'SALES_REP');
