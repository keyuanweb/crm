package com.crm.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色权限字典（081-role-permissions-update，与 design.md 一致）。 菜单 key 与前端路由对齐；权限码格式 实体:动作。单源定义，前端角色页树/勾选复用。
 */
public final class RoleConstants {

  private RoleConstants() {}

  /** 菜单分组 → 菜单项（menuKey, 中文名）。 */
  public static final List<Map<String, Object>> MENU_TREE =
      List.of(
          group("首页", item("stats", "首页")),
          group(
              "客户管理",
              item("leads", "线索"),
              item("customers", "客户"),
              item("contacts", "联系人"),
              item("customer-merge", "查重合并"),
              item("at-risk", "流失预警"),
              // 084 FR-N16：标签与细分是给客户打标、做客户分群用的，归客户管理域
              item("tags", "标签与细分")),
          group(
              "销售管理",
              item("opportunities", "商机"),
              item("sales-opportunities", "销售机会"),
              item("quotes", "报价单"),
              item("visits", "外勤拜访"),
              item("products", "产品"),
              item("playbook", "销售 Playbook"),
              // 084 FR-N17：销售配额是销售目标的达成度视图，归销售管理域
              item("quotas", "销售配额")),
          group(
              "交易管理",
              item("contracts", "合同"),
              item("contract-renewal", "合同续约"),
              item("orders", "订单"),
              item("invoices", "发票")),
          group(
              "营销管理",
              item("marketing", "营销活动"),
              item("marketing/email", "邮件营销"),
              item("email-unsubscribes", "邮件退订"),
              item("online-forms", "在线表单"),
              item("landing-pages", "落地页")),
          group(
              "客户服务",
              item("tickets", "工单管理"),
              item("knowledge", "知识库"),
              item("announcements", "公告管理"),
              item("portal", "客户门户"),
              item("satisfaction", "满意度调查"),
              item("sla-calendar", "SLA 日历")),
          group(
              "工作台",
              item("tasks", "任务管理"),
              item("suggestions", "智能建议"),
              item("call-records", "通话记录"),
              item("mail-sync", "邮件同步"),
              // 084 FR-N15：我的审批是个人待办队列，与任务/建议同类，归工作台域
              item("approvals", "我的审批")),
          group(
              "数据分析",
              item("reports", "自定义报表"),
              item("stats/leaderboard", "团队排行"),
              item("exports", "导出中心"),
              item("exports/scheduled", "定时导出"),
              // 084 FR-N14：数据大屏是可视化分析视图，归数据分析域
              item("data-vision", "数据大屏")),
          group(
              "系统管理",
              item("users", "用户管理"),
              item("roles", "角色权限"),
              item("departments", "部门管理"),
              item("currencies", "多币种")),
          group(
              "流程配置",
              item("workflows", "工作流"),
              item("approval-flows", "审批流"),
              item("sla-policies", "SLA 策略"),
              // 1.2：菜单 key 与前端路由都是扁平的 opportunity-stages。刻意不写成 settings/opportunity-stages
              // ——前端 menuKeyOf 把 /settings/custom-fields 映射成 custom-fields，与本表里的
              // settings/custom-fields 对不上，后果是「授权了却看不到菜单」。扁平命名不踩这个坑。
              item("opportunity-stages", "商机阶段"),
              item("contract-templates", "合同模板"),
              // 084 FR-N18：字段权限与自定义字段/自定义对象同属「数据模型配置面」，故与本组并列。
              // 另一方向（把 custom-fields 挪进系统管理）已评估并否决：见 plan 的规划期决策记录 P1。
              item("settings/custom-fields", "自定义字段"),
              item("field-permissions", "字段权限"),
              item("custom-objects", "自定义对象"),
              item("open-platform", "开放平台"),
              item("integration-hub", "集成中心")),
          group(
              "审计维护",
              item("audit-logs", "审计日志"),
              item("recycle-bin", "回收站"),
              // 084 FR-N13：数据保留策略是合规留存与销毁，属审计维护域（此前错挂在数据分析）
              item("data-retention", "数据保留策略")));

  /** 权限点分组 → 权限码（code, 中文 label）。 */
  public static final List<Map<String, Object>> PERMISSION_DEFS =
      List.of(
          permGroup(
              "客户管理",
              perm("customer:create", "创建客户"),
              perm("customer:update", "编辑客户"),
              perm("customer:delete", "删除客户"),
              perm("customer:transfer", "转移客户"),
              // 1.5 批 3：公海领取。`POST /customers/pool/{id}/claim` 改造前**无任何闸门**——
              // 任何登录用户（含 VIEWER / ANALYST）都能把公海客户领成自己的。判据②（无闸门 → 按矩阵补）
              // 在这里第一次用于**写**接口，故单列一个码而不是复用 customer:transfer：领取与"把别人的客户
              // 改派给自己"不是一件事，复用等于顺手把改派权也扩出去（customer:transfer 已被
              // CustomerController 的改派端点校验，属"已被校验的码"，扩授即扩权）。
              perm("customer:claim", "领取公海客户"),
              // 1.5 批 3：公海的**批量**运维——{@code POST /customers/pool/scan}（扫全表、把超期未跟进的客户
              // 退回公海）与 {@code POST /customers/batch-transfer}（单次最多 100 个客户直接改归属）。
              // 改造前两者都是 hasRole('ADMIN')，本码**不授给任何角色** ⇒ 范围不变。
              // 这里刻意**没有**复用 customer:transfer（它的持有者含 SALES/SALES_MANAGER）：
              // batchTransfer 直接 `set(ownerId)`，既不校验调用者是否拥有这些客户，也没有任何数据范围过滤
              // ——它是 064 数据范围体系的一处绕行，扩权等于让一个销售代表把全队的客户一次性改成自己的。
              // scan 同理（它动的是全公司所有已归属客户）。故两者合并成一个"运维级"码，保持仅 ADMIN。
              perm("customer:pool_manage", "公海批量运维"),
              perm("customer:import", "导入客户"),
              perm("customer:export", "导出客户"),
              perm("customer:merge", "查重合并")),
          permGroup(
              "线索管理",
              perm("lead:create", "创建线索"),
              perm("lead:update", "编辑线索"),
              perm("lead:delete", "删除线索"),
              perm("lead:convert", "转化为客户"),
              perm("lead:assign", "分配线索"),
              // 1.5：导出。线索的**列表**与**详情**有真实的数据范围过滤（LeadService 的
              // resolveVisibleOwnerIds 与 checkLeadPermission），所以那两条读不设码；导出走的是
              // LeadExcelService 自己拼的查询，既不过 Service 也没有范围过滤，任何登录用户都能
              // 拉走全部线索（含手机号/邮箱）——所以它必须设码。授予范围＝改造前类级
              // hasAnyRole('ADMIN','SALES','SUPPORT') 放行的那三个角色，一个不多一个不少。
              perm("lead:export", "导出线索")),
          permGroup(
              "联系人管理",
              perm("contact:create", "创建联系人"),
              perm("contact:update", "编辑联系人"),
              perm("contact:delete", "删除联系人")),
          permGroup(
              "商机管理",
              // 1.5：读码。商机的列表与详情都没有数据范围过滤，类级 hasAnyRole('ADMIN','SALES')
              // 一撤，任何登录用户都能读走全部商机及其金额。授予范围 = 持有 'opportunities' 菜单的
              // 角色（ADMIN 走切面直通），**刻意不含 SUPPORT**：OpportunityIT.supportRoleForbidden
              // 断言客服看不到商机，那条断言此前是被那个粗粒度门顺手满足的，撤门之后只有靠这张码表
              // 才继续成立（不加码 → 该测试红；加码但把它授给 SUPPORT → 同样红）。
              perm("opportunity:read", "查看商机"),
              perm("opportunity:create", "创建商机"),
              perm("opportunity:update", "编辑商机"),
              perm("opportunity:delete", "删除商机"),
              perm("opportunity:export", "导出商机"),
              // 1.2：阶段字典的增删改（读接口不需要权限——看板列头与表单下拉都要它）。
              perm("stage:manage", "商机阶段配置")),
          permGroup(
              "销售配额",
              // 1.5 批 3：读码。SalesQuotaController 改造前是**类级** hasAnyRole('ADMIN','SALES_MANAGER')，
              // 而字典里 quota:* 五个码没有一个读码——五个读端点无处可挂。撤掉类级门却不设读码，
              // 等于把"个人/团队配额与达成率"对全体登录用户开放（Service 里没有数据范围过滤），
              // 所以必须新增本码。授予范围 = 改造前那道门放行的 ADMIN + SALES_MANAGER（判据①）。
              perm("quota:read", "查看配额"),
              perm("quota:create", "创建配额"),
              perm("quota:update", "编辑配额"),
              perm("quota:delete", "删除配额"),
              perm("quota:breakdown", "配额分解"),
              perm("quota:achievement", "查看达成率")),
          permGroup(
              "合同管理",
              // 1.5：读码。合同列表与详情没有数据范围过滤，类级 hasAnyRole('ADMIN','SALES') 撤除后
              // 必须设码，否则合同金额与条款对全体登录用户可见。授予范围 = 持有 'contracts' 菜单的
              // 角色，与 invoice:read / ticket:read 同口径。
              perm("contract:read", "查看合同"),
              perm("contract:create", "创建合同"),
              perm("contract:update", "编辑合同"),
              perm("contract:delete", "删除合同"),
              perm("contract:approve", "审批合同"),
              perm("contract:renewal", "合同续约"),
              // 1.5 批 3：合同**模板**的增删改。不复用 contract:create/update/delete —— 那三个码
              // 授给了销售四角色与 FINANCE_*（V46/V75），复用的后果是"能签合同的人顺便能改合同法定文本"。
              // 模板是配置面，改造前是 hasRole('ADMIN')，本码不授给任何角色 ⇒ 可访问范围与改造前一致，
              // 只是管理员从此能在角色页上勾（与 integration:manage 同一处置）。
              perm("contract_template:manage", "合同模板管理")),
          permGroup(
              "订单管理",
              // 1.5：读码。订单列表、详情、回款提醒三处都没有数据范围过滤，类级
              // hasAnyRole('ADMIN','SALES') 撤除后必须设码。授予范围 = 持有 'orders' 菜单的角色
              // （含 FINANCE_*——订单金额本来就是财务的日常输入）。
              perm("order:read", "查看订单"),
              perm("order:create", "创建订单"),
              perm("order:update", "编辑订单"),
              perm("order:delete", "删除订单"),
              perm("order:payment", "登记回款")),
          permGroup(
              "发票管理",
              // 1.5：读与写拆成两个码。发票列表/统计此前与写操作共用一个 invoice:manage，
              // 而「只读用户」这类角色按定义不该拿到写码——不拆的话，要么它们的发票菜单指向 403，
              // 要么为了让它能看就得把写权限一起给它。拆开后「能看」与「能开」互不牵连。
              perm("invoice:read", "查看发票"),
              perm("invoice:create", "创建发票"),
              perm("invoice:update", "编辑发票"),
              perm("invoice:delete", "删除发票"),
              // 1.5：InvoiceController 的 create / voidInvoice 标的就是这个码。字典里原先只有
              // create/update/delete 三件套，注解却写 invoice:manage → 该码在角色页勾不出来，
              // 于是这两个端点对非 ADMIN 恒 403，且管理员**无法**通过授权解开。
              perm("invoice:manage", "发票管理")),
          permGroup(
              "报价单管理",
              // 1.5：读码。列表、详情、{id}/pdf 三处都没有数据范围过滤（QuoteService 里没有任何
              // resolveVisibleOwnerIds / EntityAccessService 调用，detail 是按主键直取），类级
              // hasAnyRole('ADMIN','SALES') 撤除后必须设码。授予范围 = 持有 'quotes' 菜单的角色。
              perm("quote:read", "查看报价单"),
              perm("quote:create", "创建报价单"),
              perm("quote:update", "编辑报价单"),
              // 至今没有任何端点校验它：报价单没有删除接口（QuoteController 只有状态机上的
              // approve/reject）。与 ticket:approve 同一形态——保留是为了不动已发布的权限语义，
              // 但它在角色页上是可勾的。
              perm("quote:delete", "删除报价单"),
              perm("quote:approve", "审批报价单")),
          permGroup(
              "产品管理",
              // 1.5 批 3：三个码此前只被 ProductPriceController（定价，V83）用过一个 update，产品**本身**
              // 的增删改一直是 hasRole('ADMIN')。接线后各按各的授予范围放行：
              // create/delete → ADMIN + MARKETING_MANAGER（V75 唯一持有者）；update → 再加 V83 为定价
              // 授出的 SALES/SALES_MANAGER/SALES_REP/MARKETING_SPECIALIST。
              // 已知的不一致：持有「产品」菜单的销售角色能编辑但不能新建/删除产品。这是矩阵的答案
              // （create/delete 从未授给它们），不是接线失误——是否扩给菜单持有者留作裁决。
              perm("product:create", "创建产品"),
              perm("product:update", "编辑产品"),
              perm("product:delete", "删除产品")),
          // 1.5 批 3：销售 Playbook 的动作**模板**管理（/stage-actions 四个端点，改造前 hasRole('ADMIN')）。
          // 「销售 Playbook」菜单（playbook）在种子里无人持有，故本码也不授给任何角色——范围不变。
          // 读端点（GET /stage-actions）与写共用本码：没有只读消费者（菜单无人持有，销售实际用的
          // 是 /sales-opportunities/{id}/actions，那条走 opportunity:* 家族，已按码放行）。
          // 若将来把 Playbook 页授给只读角色，此处应拆出 playbook:read（同 invoice:read 的处置）。
          permGroup("销售 Playbook", perm("playbook:manage", "Playbook 动作模板")),
          permGroup(
              "营销活动",
              perm("campaign:create", "创建营销活动"),
              perm("campaign:update", "编辑营销活动"),
              perm("campaign:delete", "删除营销活动"),
              perm("campaign:export", "导出活动数据"),
              // 1.5：LandingPageController 的写操作标的是 marketing:manage。与上一条同理——
              // 补字典而不是改注解，免得已发布的营销权限语义被换掉。
              perm("marketing:manage", "营销活动管理")),
          permGroup(
              "邮件营销",
              perm("email:create", "创建邮件"),
              perm("email:update", "编辑邮件"),
              perm("email:delete", "删除邮件"),
              perm("email:send", "发送邮件"),
              perm("email:manage", "邮件营销管理")),
          // 1.5：FormController 的写操作标的是 form:manage。字典里原先没有这个码，也就没有任何角色
          // 能拿到它——其写接口对非 ADMIN 恒 403。单独成组是因为菜单里 在线表单/落地页 本就自成一档。
          permGroup("在线表单", perm("form:manage", "在线表单管理")),
          permGroup(
              "工单管理",
              // 1.5：读码。工单的数据范围是个**半成品**：TicketService.page 只在
              // `"SALES".equals(principal.role())` 这个字面量判断下过滤（硬编码角色名，同样认不出
              // 081 的 SALES_MANAGER/SALES_REP），detail() 则完全没有范围校验（SALES 按 id 直取
              // 列表外工单）。也就是说除 SALES 外的角色一旦放进来就是全量可见。类级 @PreAuthorize
              // 一撤，读就必须设码，否则任何登录用户都能拉走全部工单及其客户信息。
              // 授予范围 = 持有 'tickets' 菜单的全部角色，与 invoice:read 同一口径。
              perm("ticket:read", "查看工单"),
              perm("ticket:create", "创建工单"),
              perm("ticket:update", "编辑工单"),
              perm("ticket:delete", "删除工单"),
              perm("ticket:assign", "分配工单"),
              perm("ticket:reply", "回复工单"),
              // 至今没有被任何注解引用：本项目的工单状态机是 OPEN→IN_PROGRESS→RESOLVED→CLOSED，
              // 没有"审批"这个动作，transition 标的是 ticket:update（改状态即改工单）。
              // 保留该码是为了不动已发布的权限语义，但它在角色页上是可以勾的——见 1.5 报告。
              perm("ticket:approve", "审批工单")),
          // 1.3-sla-escalation：SLA 策略管理的权限码。V75 已把该码授给 SUPPORT_MANAGER，
          // 但字典里一直没有它——SlaPolicyController 一旦挂 @RequirePermission("sla:manage")，
          // 非 ADMIN 就会因「码不在字典里」恒 403（1.5 要消灭的同一形态）。
          permGroup("SLA 管理", perm("sla:manage", "SLA 策略管理")),
          permGroup(
              "知识库",
              // 1.5：读码。知识库的读接口比其它模块更**刻意**：文章没有 owner 维度，Service 里
              // 也没有任何数据范围过滤，靠"数据范围"是兜不住的。授予范围 = 改造前类级
              // hasAnyRole('ADMIN','SALES','SUPPORT') 放行的三个角色（保留它们的事实能力）
              // ∪ 持有 'knowledge' 菜单的两个客服角色（矩阵承诺了但被粗粒度门挡在门外）。
              perm("knowledge:read", "查看文章"),
              perm("knowledge:create", "创建文章"),
              perm("knowledge:update", "编辑文章"),
              perm("knowledge:delete", "删除文章")),
          // 1.5：AnnouncementController 的 create/update 标的是 announcement:manage。
          permGroup("公告管理", perm("announcement:manage", "公告管理")),
          // 096：评论（037-announcements 的协作面）。此前 CommentController 是**类级**
          // hasAnyRole('ADMIN','SALES','SUPPORT')，字典里一个 comment:* 码都没有 ⇒ 这三条端点的访问范围
          // 写死在角色名里，管理员既看不见、也改不了（该类的 javadoc 自述为「一处已知的、待裁决的锁死」：
          // 「要么给评论建码族并明确授予名单，要么把这些角色从协作场景里排除，二选一」——096 选前者）。
          // 建码后按 V81 判据① 补授 ADMIN + SALES + SUPPORT（= 那道类级门**原先放行的集合**，
          // 一个不多一个不少）⇒ 零扩权、零收窄。⚠️ 注意 hasAnyRole 匹配的是**角色 code**：
          // SALES_REP / SALES_MANAGER / SUPPORT_AGENT / SUPPORT_MANAGER 都**不等于** 'SALES'/'SUPPORT'，
          // 故它们改造前后**都**进不来——本项新增的只是「可勾选性」，不是权限本身。
          // 评论**没有菜单**（它挂在客户/线索/商机/工单的详情页里，见 MENU_TREE），
          // 故本组的授予范围**不能**按「菜单承诺」推导（V81 判据② 在此不适用），只能走判据①。
          permGroup(
              "评论协作",
              // 读码：覆盖 GET /api/v1/comments。CommentService.checkEntityVisible 保证的是
              // 「**看得见**就能评」，且 TICKET 一类直通、ADMIN 直接 return ⇒ 它不是范围过滤，
              // 故这条读必须设码；撤门而不设码 = 任何登录用户都能读走全部评论。
              perm("comment:read", "查看评论"),
              perm("comment:create", "发表评论"),
              // 删除**另有**服务层判据：CommentService.delete 只允许「作者或 ADMIN」。
              // 本码是它**之前**的闸门，两者是 ∧ 关系——有码 ≠ 能删任何人的评论。
              perm("comment:delete", "删除评论")),
          permGroup(
              "任务管理",
              perm("task:create", "创建任务"),
              perm("task:update", "编辑任务"),
              perm("task:delete", "删除任务")),
          permGroup(
              "跟进管理",
              // 1.5：读码。FollowUpService.page 的实体可见性校验是**逐参数**的——customerId/leadId/
              // opportunityId 三个都不传时它一条过滤都不加，直接分页拉全表。于是这条读在"带 id 调用"时
              // 是安全的、在"无参调用"时是全量的，类级门一撤必须设码。授予范围 = 改造前类级
              // hasAnyRole('ADMIN','SALES','SUPPORT') 放行的三个角色 ∪ 已持有 follow_up:* 的三个 081 角色。
              perm("follow_up:read", "查看跟进"),
              perm("follow_up:create", "创建跟进"),
              perm("follow_up:update", "编辑跟进"),
              // 至今没有任何端点校验它：FollowUpController 只有列表/新增/编辑三个动作，没有删除。
              // 与 quote:delete / ticket:approve 同一形态——保留是为了不动已发布的权限语义，
              // 但它在角色页上是可以勾的（见 1.5 报告）。
              // 097：全仓**22 个**未接线码的台账（本码与其余 21 条的逐条性质与理由）在
              // com.crm.security.UnwiredPermissionCodeTest 的 LEDGER——集合增或减该测试都红。
              perm("follow_up:delete", "删除跟进")),
          // 1.5：FieldVisitController 的 cancel 等写操作标的是 visit:manage。
          permGroup("外勤拜访", perm("visit:manage", "外勤拜访管理")),
          permGroup(
              "通话记录",
              // 1.5：读码。CallRecordService 里**一条数据范围过滤都没有**（列表按条件过滤、详情按主键直取、
              // 统计是全表聚合），类级门一撤，不设读码就是任何登录用户都能拉走全部通话记录。
              // 授予范围 = 改造前类级 hasAnyRole('ADMIN','SALES','SUPPORT') 放行的三个角色 ∪ 已持有
              // call_record:* 的三个 081 角色。与 invoice:read / ticket:read 同一口径。
              perm("call_record:read", "查看通话记录"),
              perm("call_record:create", "创建通话记录"),
              perm("call_record:update", "编辑通话记录"),
              perm("call_record:delete", "删除通话记录")),
          // 1.5 批 3：邮件同步（062）的两个码，此前整类都是角色字面量，字典里一个码都没有。
          // 拆两个是因为两半的可访问范围不同，且不该被顺手合并：账户配置是**平台级凭证与主机配置**
          // （改造前 hasRole('ADMIN')，本码不授任何人）；同步记录面改造前是 hasAnyRole('ADMIN','SALES')，
          // 故 mail_sync:manage 按判据①补授 ADMIN + SALES，一个不多一个不少。
          // 合并成一个码的话，要么 SALES 能改账户配置（扩权），要么 SALES 丢掉模拟同步（把能用的打成 403）。
          permGroup(
              "邮件同步", perm("mail_account:manage", "邮件账户管理"), perm("mail_sync:manage", "邮件同步操作")),
          permGroup(
              "工作流",
              // 1.5 批 3：读码。规则列表与执行日志改造前是 hasRole('ADMIN')，字典里却只有
              // create/update/delete/manage 四个写向码——没有读码可挂。**刻意不挂 workflow:manage**：
              // 它被授给了 SALES_MANAGER / MARKETING_MANAGER / FINANCE_MANAGER（V75），挂上去等于
              // 让三个非管理员角色读到全部自动化规则与执行流水，而「工作流」菜单只授给了 ADMIN。
              // 本码不授给任何角色 ⇒ 与改造前一致。
              perm("workflow:read", "查看工作流"),
              perm("workflow:create", "创建工作流"),
              perm("workflow:update", "编辑工作流"),
              perm("workflow:delete", "删除工作流"),
              perm("workflow:manage", "管理工作流")),
          permGroup(
              "审批管理",
              perm("approval:create", "创建审批"),
              perm("approval:update", "编辑审批"),
              perm("approval:delete", "删除审批"),
              perm("approval:approve", "审批操作")),
          permGroup(
              "数据导出",
              perm("export:create", "创建导出"),
              perm("export:delete", "删除导出"),
              perm("export:scheduled", "定时导出"),
              perm("export:compliance", "合规导出")),
          // 1.5：报表与数据大屏此前共用「系统管理」组里的 report:manage，而 ReportController 的读接口与方法级
          // hasRole('ADMIN') 没有任何码对应——ANALYST 持有 report:manage 却跑不了报表（那三个模板端点它也用不了）。
          // 本次拆成读码 + 大屏码，report:manage 仍留在「系统管理」组（V46 起就在那，不搬动它以免扰乱习惯）。
          permGroup(
              "报表",
              // 授予范围恰好等于持有「自定义报表」菜单的角色（今天＝全部 13 个角色）。发给所有人也仍然建码：
              // 报表查询是任意维度的聚合面，而角色页允许自建角色——留空等于"任何新建角色自动拿到全量数据报表"。
              perm("report:view", "运行/导出报表"),
              // 数据大屏（data-vision 菜单）。原先是方法级 hasRole('ADMIN')，持有该菜单的 ANALYST 恒 403。
              perm("kpi:view", "查看数据大屏")),
          permGroup(
              "数据保留",
              perm("retention:create", "创建策略"),
              perm("retention:update", "编辑策略"),
              perm("retention:delete", "删除策略"),
              perm("retention:execute", "执行归档")),
          permGroup(
              "系统管理",
              perm("user:manage", "用户管理"),
              perm("role:manage", "角色管理"),
              perm("department:manage", "部门管理"),
              perm("field_permission:manage", "字段权限管理"),
              // 084：读码。GET /currencies 与 POST /currencies/convert 此前是角色字面量
              // hasAnyRole('ADMIN','SALES')，而「多币种」菜单的持有者是 FINANCE_MANAGER——持有
              // currency:manage（V75）却打不开这一页。拆读写两码：能看汇率 ≠ 能改汇率。
              perm("currency:read", "查看币种汇率"),
              perm("currency:manage", "多币种管理"),
              perm("workflow:manage", "工作流管理"),
              perm("report:manage", "报表管理"),
              perm("system:manage", "系统管理")),
          // 1.5：V75 把这两组码授给了 ANALYST，字典里却一直没有它们——角色页勾不出来，
          // 而 RoleService.replacePermissions 是先删后插，管理员保存一次角色就会把这两组授权静默删掉。
          permGroup(
              "自定义字段",
              // 1.5：读码。CustomFieldController.page 是**配置面**（全量字段定义），类级 hasRole('ADMIN') 撤除后
              // 必须设码；授予范围 = 持有「自定义字段」菜单的角色（ADMIN、ANALYST）。
              // 注意 /definitions（表单渲染用的元数据读）**不设码**——客户/线索/商机/工单四个列表页都要它。
              perm("custom_field:read", "查看字段定义"),
              perm("custom_field:create", "创建自定义字段"),
              perm("custom_field:update", "编辑自定义字段"),
              perm("custom_field:delete", "删除自定义字段")),
          permGroup(
              "自定义对象",
              // 084：读码。CustomObjectController.page 是**配置面**（全量对象定义列表），类级 hasRole('ADMIN')
              // 撤除后必须设码；授予范围 = 持有「自定义对象」菜单的角色（ADMIN、ANALYST）。
              //
              // ⚠️ 2026-09-16 订正（096）——下面这段**原文逐字保留**，其结论已作废：
              // 【原文】「注意 /{id}/records*（对象**记录**的增删改查）**不设码**——那是业务面，靠菜单 + 数据范围，
              // 且改造前就是 hasAnyRole('ADMIN','SALES')，不在 084 范围内。」
              // 【订正】该判断的**两个依据都不成立**：① 「数据范围」——实测 CustomObjectRecordService
              // 全类**没有任何范围过滤调用**（`SecurityUtil` 仅出现在 `record.setCreatedBy(...)` 一处，
              // 无 resolveVisibleOwnerIds / DataPermissionService / EntityAccessService）；
              // ② 「菜单」——菜单只决定**前端可见性**，**不是服务端闸门**（那正是 084 自己的前提）。
              // ⇒ 记录面是权限模型里唯一一处「**既无范围保护、又不可配置**」的写面（5 个端点含 3 个写）。
              // 096 已为它建下述四码并挂到端点上；补授走 V81 判据①——范围 = 原 hasAnyRole('ADMIN','SALES')
              // 放行的集合，一个不多一个不少 ⇒ 零扩权、零收窄，管理员从此可勾选。
              perm("custom_object:read", "查看自定义对象"),
              perm("custom_object:create", "创建自定义对象"),
              perm("custom_object:update", "编辑自定义对象"),
              perm("custom_object:delete", "删除自定义对象"),
              // 096：对象**记录**（业务数据）面的四个码。与上面的定义面码**刻意分开**，因为受众不同——
              // 定义面是配置（ADMIN/ANALYST），记录面是业务（原门放行 ADMIN/SALES）；复用定义面码会
              // **双向错**：ANALYST 有定义面码却被角色门挡住（露出必然 403 的按钮），
              // SALES 被门放行却无定义面码（按钮消失 = 真收窄）。
              // 读码覆盖 `GET /{id}/records`（列表）与 `GET /{id}/records/{recordId}`（详情）两个端点——
              // 同一个动作，共用一码（照 opportunity:read 等既有做法）。
              perm("custom_object_record:read", "查看对象记录"),
              perm("custom_object_record:create", "创建对象记录"),
              perm("custom_object_record:update", "编辑对象记录"),
              perm("custom_object_record:delete", "删除对象记录")),
          // 1.5 批 3：开放平台的 API Key 与 Webhook 管理（/platform/**，改造前整类是 hasRole('ADMIN')）。
          // 「开放平台」菜单在种子里无人持有，故本码也不授任何人——范围与改造前一致。
          // ⚠️ /open/**（X-API-Key 鉴权的开放端点）**不加码**：走的是 ApiKeyAuthFilter 的
          // ApiKeyPrincipal，不是 JWT 主体，挂 @RequirePermission 会把全部 API Key 调用方打成 403。
          permGroup("开放平台", perm("open_platform:manage", "开放平台管理")),
          // 1.5：集成中心此前**没有任何码覆盖它**——IntegrationChannelController 是 ADMIN-only 类级门，
          // 于是通道配置（含企微/钉钉 webhook 地址与密钥）只能靠改代码开口。补码后不授给任何角色，
          // 可访问范围与改造前一致，但管理员从此能在角色页上勾选。
          permGroup("集成中心", perm("integration:manage", "集成中心管理")),
          permGroup(
              "审计维护",
              perm("audit:view", "查看审计日志"),
              // 1.5：回收站的读码。原先类级 hasRole('ADMIN') 把读与写（恢复/物理删）一起放行，
              // 撤门后读必须设码——跨实体列出已删除数据是敏感读。与「能看≠能开」同一口径。
              perm("recycle:view", "查看回收站"),
              perm("recycle:restore", "恢复数据"),
              perm("recycle:purge", "清理数据"),
              perm("tag:manage", "标签管理"),
              perm("segment:manage", "细分管理")));

  private static Map<String, Object> group(String title, Map<String, Object>... items) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("title", title);
    m.put("children", List.of(items));
    return m;
  }

  private static Map<String, Object> item(String key, String title) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", key);
    m.put("title", title);
    return m;
  }

  private static Map<String, Object> permGroup(String title, Map<String, Object>... perms) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("title", title);
    m.put("children", List.of(perms));
    return m;
  }

  private static Map<String, Object> perm(String code, String label) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", code);
    m.put("label", label);
    return m;
  }
}
