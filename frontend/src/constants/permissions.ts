/**
 * 前端用到的权限码登记表。
 *
 * <p><b>权威来源是后端 `com.crm.common.RoleConstants.PERMISSION_DEFS`</b>（以及各 Controller 方法上的
 * `@RequirePermission("...")`）。这里的每一条都必须能在那个字典里找到，改名前先回去对一遍。
 *
 * <p><b>写错一个码是「静默失效」而不是报错</b>：`hasPerm` 走的是
 * `user.permissions.includes(code)`，字典里不存在的码永远不会被授给任何角色（角色页根本勾不出来），
 * 于是这个码对除 ADMIN 以外的所有人恒为 `false`——按钮无声无息地消失，没有任何报错、没有 403、
 * 控制台也不会有任何提示。所以往这里加码之前，必须先在 `PERMISSION_DEFS` 里确认它存在。
 *
 * <p><b>不需要写「或者 ADMIN」</b>：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true，
 * 所以每个码天然对管理员放行，调用点不要写成 `isAdmin || hasPerm(...)`——那是改造前的旧写法，
 * 而且在 081 扩了角色之后会漏掉真正的授权角色。
 *
 * <p><b>刻意只登记「前端真的在用它做 gating」的码</b>：前端调用了某个接口但没做 gating 的
 * （如客户导出 `customer:export`、订单创建 `order:create`）不在此列——登记不使用的码，等于给自己
 * 多留几个写错了也看不出来的名字。
 */

/** 权限码集合。键是实体 + 动作，值是后端字典里的 `实体:动作` 码。 */
export const PERMS = {
  // ---- 客户管理（CustomerController / CustomerPoolController） ----
  /** 创建客户：POST /api/v1/customers。 */
  customerCreate: 'customer:create',
  /** 编辑客户：PUT /api/v1/customers/{id}。 */
  customerUpdate: 'customer:update',
  /** 删除客户：DELETE /api/v1/customers/{id}。 */
  customerDelete: 'customer:delete',
  /** 导入客户 / 下载导入模板：POST /customers/import、GET /customers/import-template。 */
  customerImport: 'customer:import',
  /**
   * 领取公海客户：POST /customers/pool/{id}/claim（1.5 批 3 新码）。
   *
   * <p>改造前该端点**没有任何闸门**，于是「领取」按钮按 `view === 'pool'` 无条件渲染——谁点都行。
   * 接线后只有销售三角色持有本码，按钮必须跟着收，否则非销售角色会看到一个必然 403 的死按钮。
   */
  customerClaim: 'customer:claim',
  /**
   * 公海扫描 / 批量转移：POST /customers/pool/scan、POST /customers/batch-transfer（1.5 批 3 新码）。
   *
   * <p>「扫描」按钮、「转移」按钮与行选择（转移的前置）都按本码判断——两个端点挂的是同一个码，
   * 界面上就不该出现第二个判据。字典里**没有任何角色被授**（刻意不复用 `customer:transfer`：
   * 批转移不校验调用者是否拥有这些客户、也无数据范围过滤），于是效果是"仅管理员可见、可用"，
   * 与后端放行范围一致。
   *
   * <p>`customer:transfer` **刻意不登记**：它描述的是转移归属，且已被授给 SALES / SALES_MANAGER
   * （V46 / V75），但后端**没有任何端点校验它**——拿它 gating 只会让这些角色看到一个必然点不动的
   * 按钮（该端点的码其实是 pool_manage）。本文件只登记真的在用、且真的对得上端点的码。
   */
  customerPoolManage: 'customer:pool_manage',

  // ---- 合同管理（ContractController） ----
  /** 审批合同（通过 / 驳回）：POST /contracts/{id}/approve、/reject。 */
  contractApprove: 'contract:approve',

  // ---- 报价单管理（QuoteController） ----
  /** 审批报价单（通过 / 驳回）：POST /quotes/{id}/approve、/reject。 */
  quoteApprove: 'quote:approve',

  // ---- 线索管理（LeadController） ----
  /** 分配线索（含「分配给我」）：POST /leads/{id}/assign。 */
  leadAssign: 'lead:assign',

  // ---- 订单管理（OrderController） ----
  /** 删除订单：DELETE /api/v1/orders/{id}。 */
  orderDelete: 'order:delete',

  // ---- 产品管理（ProductController） ----
  /**
   * 产品增删改：POST /api/v1/products、PUT|DELETE /api/v1/products/{id}（1.5 批 3 起挂码）。
   *
   * <p>三个码的授予范围**不一样**，这正是要分开 gating 的原因：create / delete 只有
   * MARKETING_MANAGER 持有（销售角色没有），而 update 还授给了 SALES / SALES_MANAGER / SALES_REP /
   * MARKETING_SPECIALIST（V83 为产品定价授出）——所以持有「产品」菜单的销售角色能编辑、不能新建/删除。
   */
  productCreate: 'product:create',
  productUpdate: 'product:update',
  productDelete: 'product:delete',

  // ---- 营销活动（MarketingController） ----
  /** 创建活动：POST /api/v1/campaigns（1.5 批 3 起；三个码同批授给 SALES）。 */
  campaignCreate: 'campaign:create',
  /** 编辑 / 启动 / 结束活动：PUT /campaigns/{id}、POST /campaigns/{id}/start、/end。 */
  campaignUpdate: 'campaign:update',
  /** 删除活动：DELETE /api/v1/campaigns/{id}。 */
  campaignDelete: 'campaign:delete',

  // ---- 系统管理（RoleController） ----
  /** 角色权限的增删改：POST/PUT/DELETE /api/v1/roles。 */
  roleManage: 'role:manage',

  // ================ 086 批次：按钮级收口 ================
  // 下面这批码只收口「高权与破坏性操作」——删除/批量删除、审批、分配与认领、导出、
  // 执行与手动触发、启用停用、角色与权限配置，以及不可逆的业务终态变更。
  // 新建/编辑/导入**刻意不登记**（不在收口范围内）。
  //
  // 每一条都逐条核过两件事：存在于 `RoleConstants.PERMISSION_DEFS`、且被至少一个
  // `@RequirePermission` 真实校验（`FrontendPermissionCodeAlignmentTest` 的两条断言）。
  // 那 22 个「字典里有、没有任何端点校验」的死码一条都没进来——挂了它们，
  // 对这些码的持有者就是恒真的空动作，对其他人则是永远消失的按钮。

  // ---- 公告（AnnouncementController） ----
  /** 删除公告：DELETE /api/v1/announcements/{id}。 */
  announcementManage: 'announcement:manage',

  // ---- 审批流与工作流（ApprovalController / WorkflowController） ----
  /** 删除审批流定义：DELETE /api/v1/approval-flows/{id}。 */
  workflowManage: 'workflow:manage',
  /** 启停工作流规则：POST /api/v1/workflows/rules/{id}/toggle。 */
  workflowUpdate: 'workflow:update',
  /** 删除工作流规则：DELETE /api/v1/workflows/rules/{id}。 */
  workflowDelete: 'workflow:delete',

  // ---- 通话记录（CallRecordController） ----
  /** 删除通话记录：DELETE /api/v1/call-records/{id}。 */
  callRecordDelete: 'call_record:delete',

  // ---- 评论（CommentController） ----
  /**
   * 发表评论 / 删除评论：POST /api/v1/comments、DELETE /api/v1/comments/{id}。
   *
   * <p>096 之前 `comment:*` 一码不存在，后端是**类级** `@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")`，
   * 于是非这三者的用户照样看得见「发表」按钮、点下去必然 403。096 建了这三码并撤掉类级门（补授范围
   * = 那道门原先放行的集合，逐字不变），本页据此收口。
   *
   * <p>**不登记 `comment:read`**：列表是页面的取数路径、不做 gating（本文件头部那条口径）。
   * 该码在后端字典里照旧存在，管理员可勾选。
   */
  commentCreate: 'comment:create',
  commentDelete: 'comment:delete',

  // ---- 联系人（ContactController） ----
  /** 删除联系人：DELETE /api/v1/contacts/{id}。 */
  contactDelete: 'contact:delete',

  // ---- 合同（ContractController / ContractAttachmentController） ----
  /**
   * 终止合同 / 删除合同附件：PUT /contracts/{id}/terminate、DELETE /contracts/{id}/attachments/{aid}。
   *
   * <p>两个动作共用一个码：`ContractController.java:138-144` 与
   * `ContractAttachmentController.java:82-86` 挂的都是 `contract:update`，且 `ContractController.java:39-40`
   * 的注释写明了理由——「状态推进在语义上就是改这份合同」。
   *
   * <p>本码在 086 之前未登记，但 `ContractDetailPage` 的两个按钮一直在用它该用的终点（只是没判据）。
   * 注意与 `contractApprove` **不可互替**：审批是独立的高权动作，两码各自独立（FR-B07）。
   */
  contractUpdate: 'contract:update',

  // ---- 合同模板（ContractTemplateController） ----
  /**
   * 删除合同模板：DELETE /api/v1/contract-templates/{id}。
   *
   * <p>改造前该按钮按 `user.role === 'ADMIN'` 硬编码判断。该码**非 ADMIN 持有者为零**
   * （`V87:41-42,53-56`），故改挂本码后对非管理员的效果与硬编码**逐字一致**，只是从此可勾选授予。
   */
  contractTemplateManage: 'contract_template:manage',

  // ---- 客户查重合并（CustomerMergeController） ----
  /**
   * 客户查重扫描 / 合并：GET /customers/duplicates、POST /customers/merge。
   *
   * <p>扫描是合并的前置步骤，两个端点挂同一个码，界面上就不该出现第二个判据。
   * 合并**不可逆**，是本次收口的重点对象。
   */
  customerMerge: 'customer:merge',

  // ---- 自定义对象（CustomObjectController） ----
  /** 启用/停用自定义对象：POST /api/v1/custom-objects/{id}/toggle。 */
  customObjectUpdate: 'custom_object:update',
  /** 删除自定义对象：DELETE /api/v1/custom-objects/{id}。 */
  customObjectDelete: 'custom_object:delete',

  // ---- 自定义对象记录（CustomObjectController 的记录面，096 建码） ----
  /**
   * 新建 / 编辑 / 删除对象记录：POST /custom-objects/{id}/records、
   * PUT|DELETE /custom-objects/{id}/records/{rid}。
   *
   * <p>对象**定义**面（上一条那组 `custom_object:*`）与**记录**面是两套码。记录面 096 之前**没有任何码**，
   * 后端是五个记录端点各自的方法级 `@PreAuthorize("hasAnyRole('ADMIN','SALES')")`；补授范围 = 那道门
   * 原先放行的集合（`ADMIN` + `SALES`），故挂码前后对每个角色逐字一致，变的只是从此可在角色页勾选。
   * `ANALYST` 虽有定义面码，本族码**没有**——旧门也不放他进记录面，不是本批收窄的。
   *
   * <p>**不登记 `custom_object_record:read`**：列表与详情是页面的取数路径、不做 gating（同上口径）。
   */
  customObjectRecordCreate: 'custom_object_record:create',
  customObjectRecordUpdate: 'custom_object_record:update',
  customObjectRecordDelete: 'custom_object_record:delete',

  // ---- 数据保留（DataRetentionPolicyController） ----
  /** 删除保留策略：DELETE /api/v1/data-retention/policies/{id}。 */
  retentionDelete: 'retention:delete',
  /** 立即执行归档/删除：POST /api/v1/data-retention/execute。 */
  retentionExecute: 'retention:execute',

  // ---- 部门（DepartmentController） ----
  /** 删除部门：DELETE /api/v1/departments/{id}。 */
  departmentManage: 'department:manage',

  // ---- 邮件营销（EmailController） ----
  /**
   * 邮件模板删除 / 活动测试发送 / 退订恢复：DELETE /email-templates/{id}、
   * POST /email-campaigns/{id}/test、DELETE /email/unsubscribes/{id}。
   *
   * <p>三者分属不同页面但挂同一个码：`EmailController` 全线只有 `email:manage` 一个写码。
   */
  emailManage: 'email:manage',

  // ---- 数据导出（ExportController / ScheduledExportController） ----
  /** 发起导出：POST /api/v1/exports。 */
  exportCreate: 'export:create',
  /**
   * 定时导出任务的暂停/恢复/删除/立即执行：PUT|DELETE /scheduled-exports/{id}、
   * POST /scheduled-exports/{id}/execute-now。
   */
  exportScheduled: 'export:scheduled',

  // ---- 发票（InvoiceController） ----
  /** 作废发票：POST /api/v1/invoices/{id}/void。 */
  invoiceManage: 'invoice:manage',

  // ---- 知识库（KnowledgeArticleController） ----
  /** 发布 / 下架文章：POST /api/v1/knowledge/{id}/publish、/unpublish。 */
  knowledgeUpdate: 'knowledge:update',
  /** 删除文章：DELETE /api/v1/knowledge/{id}。 */
  knowledgeDelete: 'knowledge:delete',

  // ---- 线索（LeadController） ----
  /** 删除线索：DELETE /api/v1/leads/{id}。（086 收口时发现已接线页面漏了这一处） */
  leadDelete: 'lead:delete',

  // ---- 落地页（LandingPageController） ----
  /** 删除落地页：DELETE /api/v1/landing-pages/{id}。 */
  marketingManage: 'marketing:manage',

  // ---- 邮件同步（MailAccountController） ----
  /** 删除邮箱账号：DELETE /api/v1/mail-accounts/{id}。 */
  mailAccountManage: 'mail_account:manage',
  /** 模拟同步 / 删除同步记录：POST /mail-accounts/{id}/sync、DELETE /mail-accounts/{id}/records/{recordId}。 */
  mailSyncManage: 'mail_sync:manage',

  // ---- 在线表单（FormController） ----
  /** 启用/停用、删除表单：POST /api/v1/forms/{id}/toggle、DELETE /api/v1/forms/{id}。 */
  formManage: 'form:manage',

  // ---- 开放平台（OpenPlatformController） ----
  /** 撤销 API Key / webhook 启停与删除：POST /platform/api-keys/{id}/revoke、POST|DELETE /platform/webhooks/**。 */
  openPlatformManage: 'open_platform:manage',

  // ---- 商机（OpportunityController / SalesOpportunityController） ----
  /** 删除商机：DELETE /api/v1/opportunities/{id}。 */
  opportunityDelete: 'opportunity:delete',
  /**
   * 赢单 / 输单：POST /api/v1/sales-opportunities/{id}/close。
   *
   * <p>商机终态变更**不可逆**，收口（086 决策 7）。看板拖拽改阶段打的是同一个
   * `PUT /sales-opportunities/{id}`，同为 `opportunity:update`，但**刻意不收**——
   * 拖拽可逆、且是销售最高频操作，挂判据只添堵。
   */
  opportunityUpdate: 'opportunity:update',

  // ---- 销售 Playbook（PlaybookController） ----
  /** 删除阶段动作模板：DELETE /api/v1/stage-actions/{id}。 */
  playbookManage: 'playbook:manage',

  // ---- 回收站（RecycleBinController） ----
  /** 恢复：POST /api/v1/recycle-bin/restore。 */
  recycleRestore: 'recycle:restore',
  /** 彻底删除（不可逆）：POST /api/v1/recycle-bin/purge。 */
  recyclePurge: 'recycle:purge',

  // ---- 多币种（CurrencyRateController） ----
  /** 删除汇率：DELETE /api/v1/currencies/{id}。 */
  currencyManage: 'currency:manage',

  // ---- 自定义字段（CustomFieldController） ----
  /** 删除自定义字段：DELETE /api/v1/custom-fields/{id}。 */
  customFieldDelete: 'custom_field:delete',

  // ---- 字段权限（FieldPermissionController） ----
  /** 删除字段权限配置：DELETE /api/v1/field-permissions/{id}。 */
  fieldPermissionManage: 'field_permission:manage',

  // ---- 集成中心（IntegrationChannelController） ----
  /** 启用/停用、删除集成渠道：POST /integration-channels/{id}/toggle、DELETE /integration-channels/{id}。 */
  integrationManage: 'integration:manage',

  // ---- 商机阶段（OpportunityStageController） ----
  /** 停用/启用、删除商机阶段：POST /opportunity-stages/{id}/enabled、DELETE /opportunity-stages/{id}。 */
  stageManage: 'stage:manage',

  // ---- SLA 策略（SlaPolicyController） ----
  /** 删除 SLA 策略：DELETE /api/v1/sla-policies/{id}。 */
  slaManage: 'sla:manage',

  // ---- 标签与细分（TagController / SegmentController） ----
  /**
   * 删除标签 / 删除细分：DELETE /api/v1/tags/{id}、DELETE /api/v1/segments/{id}。
   *
   * <p><b>细分的删除端点挂的是 `tag:manage`，不是 `segment:manage`</b>（`SegmentController.java:56-57`）。
   * `segment:manage` 零端点校验，是死码——挂它会让按钮对所有人消失，且测试断言 2（registered ⊆ enforced）
   * 会直接判红。
   */
  tagManage: 'tag:manage',

  // ---- 任务（TaskController） ----
  /** 完成任务 / 重开：POST /api/v1/tasks/{id}/toggle。 */
  taskUpdate: 'task:update',
  /** 删除任务：DELETE /api/v1/tasks/{id}。 */
  taskDelete: 'task:delete',

  // ---- 工单（TicketController） ----
  /** 删除工单：DELETE /api/v1/tickets/{id}。 */
  ticketDelete: 'ticket:delete',
  /** 分配工单：POST /api/v1/tickets/{id}/assign。 */
  ticketAssign: 'ticket:assign',
  /**
   * 工单状态流转（开始处理 / 标记已解决 / 关闭）：POST /api/v1/tickets/{id}/transition。
   *
   * <p>三个按钮打的是**同一个端点、同一个码**，只能整组收（086 决策 7：收不可逆终态）。
   */
  ticketUpdate: 'ticket:update',
  /** 发送回复：POST /api/v1/tickets/{id}/reply。 */
  ticketReply: 'ticket:reply',

  // ---- 用户管理（UserController / UserService） ----
  /**
   * 启用/停用用户、重置密码、设置数据权限、重置双因素认证：PUT /users/{id}、/users/{id}/password、
   * /users/{id}/data-permission、POST /users/{id}/2fa/reset。
   *
   * <p>第四个端点是 082 加的，挂的还是本码（`UserController` 的 `resetMfa`），故**不新增码**：
   * 界面上这四个动作同生同灭，多一个码只会让"授予了 user:manage 却仍看不到某个按钮"变成可能。
   */
  userManage: 'user:manage',

  // ---- 外勤拜访（FieldVisitController） ----
  /** 签到 / 取消拜访：POST /api/v1/field-visits/{id}/check-in、/cancel。 */
  visitManage: 'visit:manage',
} as const

export type PermCode = (typeof PERMS)[keyof typeof PERMS]
