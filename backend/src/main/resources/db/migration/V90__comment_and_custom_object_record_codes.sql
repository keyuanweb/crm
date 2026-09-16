-- ============================================================
-- V90__comment_and_custom_object_record_codes.sql — 096 两族补授
--
-- 本批 = CommentController（3 个端点）+ CustomObjectController 的 5 个记录端点，
--        是 V87 头注释点名的**两处「各自立项」中的「建码族」这一支**：
--
--   V87 原文：「· CommentController 类级 hasAnyRole('ADMIN','SALES','SUPPORT')——字典里没有 comment:* 码族，
--              撤门又不设码等于单向扩权（见其类注释，批 2 的唯一一处刻意保留，属待裁决的锁死）。
--              · CustomObjectController 的 /{id}/records 五个记录端点 hasAnyRole('ADMIN','SALES')——084 显式排除在
--              范围外：记录面是业务数据、由数据范围过滤，只有对象定义面按码（见其类注释）。
--             这两处若日后要动，应各自立项（建码族 / 明确授予名单），不要混进权限接线批次里顺手改。」
--
--   096 就是那次立项，选的是「建码族」。⚠️ 其中第二处给的理由（「由数据范围过滤」）**实测不成立**
--   ——CustomObjectRecordService 里没有任何数据范围过滤（详见 CustomObjectController 类注释的【096 订正】）。
--   也就是说记录面改造前**只有那道方法级角色门**这一个闸门，撤门而不设码就是单向扩权，建码是必须而非可选。
--   该不实表述在本仓有三个落点：CustomObjectController 类注释（已订正）、RoleConstants 的 custom_object
--   组注释（已订正）、以及**本 V87 头注释（不可改——checksum 冻结，只在 096 的 research.md 里登记）**。
--
-- 判据（沿用 V80/V81/V87，本批两族**都走判据①**）：
--   ① 改造前有粗粒度门 ⇒ 补授范围 = 那道门**原先放行的角色集合**，一个不多一个不少（保能力，防"把能用的打成 403"）
--   ② 无闸门但有菜单承诺 ⇒ 按菜单持有者
--   ③ 两者皆无 ⇒ 一个都不补，只把端点接到码上（让管理员从此勾得出来）
--   ④ 已被任何端点校验过的码，绝不补授
--   **本批两族都是「换码」（门 → 码），不是「撤门」（撤门而不设码）** ⇒ 判据① 给出的是**零扩权、零收窄**，
--   逐角色行为逐字不变，新增的只是**可勾选性**。
--   ⚠️ 判据② 在本批两族都**不适用**：评论没有菜单（评论挂在客户/线索/商机/工单详情里），
--      自定义对象记录面也没有独立菜单——故不存在"按菜单推导范围"这条路。
--
-- 本迁移新增 7 个码（字典侧见 RoleConstants 的「评论协作」组与「自定义对象」组），
-- 无一个码留给任何角色空转：7 码全部在本文件内授出。
--
-- 下界说明：以下 (角色, 码) 组合**全部**为本次新增，无需防重 —— role_permission 上没有唯一键，
-- 重复插入不会报错、只会留下一行垃圾。核对方式：这 7 个码在本次立项之前**全仓零命中**
-- （字典、迁移、前端、注解都没有），故任何 (角色, 码) 组合在迁移前必然不存在。
-- ============================================================

-- ---------- 1. 评论：按判据①补三码给 ADMIN + SALES + SUPPORT ----------
-- 改造前是**类级** @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")，方法级无注解。
-- 补授范围严格等于那个类级门原先放行的集合：{ADMIN, SALES, SUPPORT}。
--
-- 读码（comment:read）为什么必须存在：撤门又不设码时，CommentService.checkEntityVisible 只保证
-- "看得见就能评"，于是 VIEWER / ANALYST / MARKETING_* 这些**一个写码都没有**的角色会获得在可见实体下
-- 发言、并删除自己评论的能力——矩阵从未承诺过这件事。故读与写都设码，而不是撤门了事。
--   （此段理由的原文出处即 CommentController 类注释里那处「批 2 刻意保留」的书面裁决。）
--
-- ⚠️ 为什么**不**补 SALES_REP / SUPPORT_AGENT（081 新增的两个一线角色）：
--   hasAnyRole 匹配的是**角色 code**，SALES_REP ≠ 'SALES'、SUPPORT_AGENT ≠ 'SUPPORT'，
--   所以这两个角色改造前、改造后**都进不来**评论接口——不是"被收窄"，是"从来如此"。
--   判据① 要求保留事实能力，事实能力就是「进不来」。
--   本批新增的是**可勾选性**：管理员从此能在角色页上把 comment:* 勾给它们（这正是原先那处
--   「已知的、待裁决的锁死」的兑现途径），而不是替它们做决定。
--
-- ⚠️ 也不补 VIEWER / ANALYST / MARKETING_* / FINANCE_* / *_MANAGER 等其余 8 个角色：
--   那个类级门从未放行它们，补授即扩权。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code FROM `role` r
JOIN (
  SELECT 'comment:read' AS permission_code
  UNION SELECT 'comment:create'
  UNION SELECT 'comment:delete'
) p
WHERE r.code IN ('ADMIN', 'SALES', 'SUPPORT');

-- ---------- 2. 自定义对象记录：按判据①补四码给 ADMIN + SALES ----------
-- 改造前是五个记录端点**各自**的 @PreAuthorize("hasAnyRole('ADMIN','SALES')")（不是类级——本控制器
-- 的定义面六个端点在 084 已按码）。
-- 补授范围严格等于那个门原先放行的集合：{ADMIN, SALES}。
--   read 覆盖两个读端点（/{id}/records 列表、/{id}/records/{recordId} 详情），
--   create / update / delete 各对应一个写端点。
--
-- ⚠️ 为什么**不**补 ANALYST：它持有 custom_object:read/create/update/delete（对象**定义**面，084 授予）
--   并持有「自定义对象」菜单，看着像"该有"。但记录面的门改造前就没放行它（hasAnyRole('ADMIN','SALES')），
--   而记录是**业务数据**、且如前述**没有任何数据范围过滤**——放它进来就是全量可见。判据① 要求保留事实
--   能力，故本批不补；要开通须由管理员在角色页勾选该码（本批只做到"勾得出来"）。
--
-- ⚠️ 同样不补 SALES_REP：理由同第 1 节（hasAnyRole 匹配角色 code，SALES_REP ≠ 'SALES'）。
--
-- 前端半场已同批接线（CommentSection / CustomObjectRecordPage 按本批的码渲染按钮）——**但顺序是先后端**：
-- 前端先改、后端没挂码就是真收窄。仅隐藏 UI 元素绝不构成访问控制。
INSERT INTO `role_permission` (`role_id`, `permission_code`)
SELECT r.id, p.permission_code FROM `role` r
JOIN (
  SELECT 'custom_object_record:read' AS permission_code
  UNION SELECT 'custom_object_record:create'
  UNION SELECT 'custom_object_record:update'
  UNION SELECT 'custom_object_record:delete'
) p
WHERE r.code IN ('ADMIN', 'SALES');
