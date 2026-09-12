-- ============================================================
-- V87__permission_matrix_alignment_batch3.sql — 权限矩阵对齐（1.5）批 3
--
-- 本批 = ProductController / CustomerPoolController / ContractTemplateController / WorkflowController /
--        MailAccountController / MarketingController / PlaybookController / OpenPlatformController /
--        quota.SalesQuotaController 九个控制器（1.5 的第三批，也是最后一批**可按码接线**的控制器）。
--
-- 接线至此，全仓只剩两处角色字面量，都是"看清之后决定不动"、已有书面理由的，不在本批范围：
--   · CommentController 类级 hasAnyRole('ADMIN','SALES','SUPPORT')——字典里没有 comment:* 码族，
--     撤门又不设码等于单向扩权（见其类注释，批 2 的唯一一处刻意保留，属待裁决的锁死）。
--   · CustomObjectController 的 /{id}/records 五个记录端点 hasAnyRole('ADMIN','SALES')——084 显式排除在
--     范围外：记录面是业务数据、由数据范围过滤，只有对象定义面按码（见其类注释）。
-- 这两处若日后要动，应各自立项（建码族 / 明确授予名单），不要混进权限接线批次里顺手改。
-- 判据同 V80~V84：① 改造前有粗粒度门 → 补到门原先放行的集合（保能力，防"把能用的打成 403"）
--                  ② 无闸门 → 按矩阵补
--                  ③ 既无闸门也无菜单 → 一个码都不补（只把端点接上码，让管理员能勾）
--                  ④ 已被任何端点校验过的码，绝不补授
-- 外加本批显式化的一条，用来管住"顺手扩权"：
--   **接线只让"已经存在的授权"成真，不为"菜单承诺"额外授权。** 持有菜单却没有码的角色（例如
--   SALES_MANAGER 持有「营销活动」菜单而不持有 campaign:*）保持原状并逐条登记，交裁决处理，
--   不在接线迁移里顺带扩大权限。因此本批只有三处补授，且每一处都是判据①（不补 = 打成 403）。
--
-- 本批新增 9 个码，其中 6 个**不授给任何角色**（保持改造前的"仅 ADMIN"，只是从此勾得出来）：
--   customer:pool_manage / contract_template:manage / workflow:read / mail_account:manage /
--   playbook:manage / open_platform:manage —— 详见第 4 节。
--
-- 下界说明：以下 (角色, 码) 组合均已核对为当前不存在（V46/V75/V80~V86 全查），故无需防重
-- ——role_permission 上没有唯一键，重复插入不会报错、只会留下一行垃圾。
-- ============================================================

-- ---------- 1. 公海：领取（本批唯一"无闸门的写"）----------
-- claim 改造前**一条校验都没有**：任何登录用户（含 VIEWER / ANALYST / FINANCE_*）都能把公海客户领成自己的。
-- 授予范围 = 销售三角色（公海本来就是销售的作业池）；不含 SUPPORT / VIEWER / ANALYST ——它们持有「客户」菜单，
-- 但领取不是它们的职能。
-- 前端半场已同批接线：CustomerListPage 的「领取」按钮改按本码渲染（此前按 view === 'pool' 无条件渲染）。
-- 这一步是必需的——本端点与本批其余端点不同，它是"改造前谁都点得动、改造后只有销售点得动"，
-- 不跟着收口就会让其余角色看到一个必然 403 的死按钮。同批新增的
-- FrontendPermissionCodeAlignmentTest 钉住"前端登记的码必须真的被某个端点校验"，
-- 防的是这类"登记表与端点各说一套"的漂移再次出现。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'customer:claim' FROM `role` r
WHERE r.code IN ('ADMIN', 'SALES', 'SALES_MANAGER', 'SALES_REP');

-- ---------- 2. 营销活动：按判据①补 campaign:* 给 SALES ----------
-- 三个码此前只授给 MARKETING_MANAGER / MARKETING_SPECIALIST（V75），**SALES 并不持有**，而旧门是
-- hasAnyRole('ADMIN','SALES') ——照原样接线会把 SALES 从"能建活动"打成 403。补授范围严格等于旧门放行的集合。
-- 副产物：MARKETING_MANAGER / MARKETING_SPECIALIST 那三个早已存在的授权从此成真（改造前被角色字面量挡着）。
-- 不动 SALES_MANAGER：它持有「营销活动」菜单但不持有 campaign:*，改造前后都不能建活动，属登记项而非接线项。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code FROM `role` r
JOIN (
  SELECT 'campaign:create' AS permission_code UNION SELECT 'campaign:update'
  UNION SELECT 'campaign:delete'
) p
WHERE r.code IN ('ADMIN', 'SALES');

-- ---------- 3. 邮件同步：同步记录面按判据①补 SALES；账户配置面不授 ----------
-- 旧门是两半两制：账户配置 hasRole('ADMIN')、同步记录 hasAnyRole('ADMIN','SALES')。两个码因此不能合并
-- ——合并后 SALES 要么能改平台级账户配置（扩权），要么丢掉模拟同步（打成 403）。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, 'mail_sync:manage' FROM `role` r WHERE r.code IN ('ADMIN', 'SALES');

-- ---------- 3b. 销售配额：按判据①补五个码给 SALES_MANAGER ----------
-- SalesQuotaController 改造前是**类级** hasAnyRole('ADMIN','SALES_MANAGER')，撤门后十个端点全靠这五个码
-- （quota:read 是本迁移新增的读码——原字典里只有四个写向码，五个读端点无处可挂）。不补 = 销售经理被打成 403。
-- quota:delete 仍留在字典里无人引用（本类没有删除动作），与 quote:delete 同一形态。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code FROM `role` r
JOIN (
  SELECT 'quota:read' AS permission_code UNION SELECT 'quota:create' UNION SELECT 'quota:update'
  UNION SELECT 'quota:breakdown' UNION SELECT 'quota:achievement'
) p
WHERE r.code IN ('ADMIN', 'SALES_MANAGER');

-- ---------- 4. 无补授的六个新码（保持"仅 ADMIN"，只是从此勾得出来）----------
-- 依 V85 的约定，零授权必须在注释里记明，而不是留一条空跑的 INSERT：
--   · customer:pool_manage     公海批量分配与扫描。刻意不复用 customer:transfer（其持有者含销售角色）：
--                              batchTransfer 直接 set(ownerId)，既不校验调用者是否拥有这些客户、也无数据范围
--                              过滤，扩权等于让一个销售代表把全队的客户一次性改成自己的。
--   · contract_template:manage 合同模板增删改。刻意不复用 contract:create/update/delete（授给销售四角色与
--                              FINANCE_*）——复用的后果是"能签合同的人顺便能改合同的法定文本"。
--   · workflow:read            工作流规则列表与执行日志。刻意不复用 workflow:manage（授给 SALES_MANAGER /
--                              MARKETING_MANAGER / FINANCE_MANAGER）：那三个角色并不持有「工作流」菜单，
--                              而规则能发通知、写字段、触发外部通道，不该被"管理者礼包"顺手带出来。
--   · mail_account:manage      平台级 SMTP/IMAP 账户配置，改错一次全公司邮件链路哑掉。
--   · playbook:manage          动作模板配置面（「销售 Playbook」菜单无人持有）。
--   · open_platform:manage     API Key 与 Webhook 密钥管理（「开放平台」菜单无人持有）。
-- 六个码的可访问范围都与改造前完全一致（仅 ADMIN 经切面直通）。
