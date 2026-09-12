/**
 * 枚举值 → i18n 键的登记表（一期 1.4 扩展）。
 *
 * <p><b>为什么把这个表集中在一处</b>：改造前每个 `src/types/*.ts` 各自导出一份中文标签常量
 * （`TICKET_STATUS_LABELS = { OPEN: '待处理', ... }`），被 30 多个页面引用。那些常量是**硬编码中文**，
 * 于是页面文案全部双语了、表格里的「状态」「阶段」列还是中文——1.4 把其余部分翻译完，
 * 反而把这层衬得更显眼。
 *
 * <p><b>为什么不把键直接写进调用点</b>：同一个枚举被多个页面用（如工单状态被列表页与详情页共用），
 * 各写各的必然分叉。这里让**同一个枚举只有一份「值 → 键」映射**，页面只消费。
 * 键文案本身仍归 `src/i18n/*.ts` 单写者（本次由 `scripts/merge-i18n-keys.mjs` 合并）。
 *
 * <p><b>命名空间的取舍</b>：能对上既有 `common.status.*` 且中文与语义都一致的，直接复用（如工单
 * `OPEN` → `common.status.open`）；其余统一落在新增的 `enums.*` 下。**刻意不复用页面私有的键**
 * （如 `pages.marketing.onlineForm.sourceWebsite`）——那是另一个页面的命名空间，跨页引用会把两个
 * 页面的文案耦在一起（1.4 已在 `SegmentListPage` 上修过这类问题）。
 *
 * <p><b>诚实降级</b>：`labelOf` 在遇到未登记的枚举值（如后端新增了一个状态）时返回**原始码**而不是
 * 键名字面量。与 `App.tsx` 菜单文案的处理同一原则：显示 `OPEN` 只是不好看，显示
 * `enums.ticketStatus.OPEN` 是让人看不懂。
 */

/** i18n 键 → 该枚举某一取值的映射。 */
export type EnumKeyMap = Readonly<Record<string, string>>

/** 只依赖 `t` 的「给键返回文案」能力，故用结构化最小类型，避免与 i18next 的泛型签名纠缠。 */
export type TranslateFn = (key: string) => string

/**
 * 取枚举文案。
 *
 * @param map 该枚举的「值 → 键」映射
 * @param code 后端给的枚举值；`null` / `undefined` 返回 `fallback`
 * @param fallback 无值时的兜底文案，缺省空串
 */
export function labelOf(
  t: TranslateFn,
  map: EnumKeyMap,
  code: string | null | undefined,
  fallback = '',
): string {
  if (!code) return fallback
  const key = map[code]
  return key ? t(key) : fallback || code
}

export const ENUM_KEYS = {
  // ---------------------------------------------------------------- 通用：实体名 / 优先级

  /** 实体名。导出类型、字段权限、任务关联对象、webhook 事件共用同一套实体词汇。 */
  entity: {
    LEAD: 'enums.entity.lead',
    CUSTOMER: 'enums.entity.customer',
    OPPORTUNITY: 'enums.entity.opportunity',
    TICKET: 'enums.entity.ticket',
    CONTRACT: 'enums.entity.contract',
    ORDER: 'enums.entity.order',
    QUOTE: 'enums.entity.quote',
  },

  /**
   * 任务优先级（HIGH / MEDIUM / LOW）。**只有三档，没有 URGENT。**
   *
   * <p>工单用 {@link ENUM_KEYS.ticketPriority}（多一档 URGENT）。两者刻意分开而不是共用一份：
   * 后端 `TaskItem.priority` 是无校验的 String，`V22__task_item.sql` 只定义了 HIGH/MEDIUM/LOW，
   * 任务表单若按一份含 URGENT 的映射去 `Object.keys(...)` 生成下拉，会多出一个后端不认的「紧急」——
   * 存进去不报错、`PRIORITY_COLORS` 又没有这一档、Tag 颜色变 undefined。共用的代价是一次静默的数据污染。
   */
  priority: {
    HIGH: 'enums.priority.high',
    MEDIUM: 'enums.priority.medium',
    LOW: 'enums.priority.low',
  },

  // ---------------------------------------------------------------- 工单

  ticketStatus: {
    OPEN: 'common.status.open',
    IN_PROGRESS: 'common.status.in_progress',
    RESOLVED: 'common.status.resolved',
    CLOSED: 'common.status.closed',
  },

  ticketPriority: {
    LOW: 'enums.priority.low',
    MEDIUM: 'enums.priority.medium',
    HIGH: 'enums.priority.high',
    URGENT: 'enums.priority.urgent',
  },

  ticketSla: {
    NORMAL: 'enums.slaStatus.normal',
    WARNING: 'enums.slaStatus.warning',
    OVERDUE: 'enums.slaStatus.overdue',
  },

  // ---------------------------------------------------------------- 线索

  leadStatus: {
    NEW: 'enums.leadStatus.new',
    WORKING: 'common.status.working',
    QUALIFIED: 'common.status.qualified',
    DISQUALIFIED: 'common.status.disqualified',
  },

  /**
   * 来源。线索来源与营销活动渠道在本系统里是同一套词汇（两者都含 官网/广告/展会/转介绍/其他），
   * 故共用；活动多出的「邮件」「社交媒体」放在 `campaignChannel` 下。
   */
  source: {
    WEBSITE: 'enums.source.website',
    AD: 'enums.source.ad',
    EXHIBITION: 'enums.source.exhibition',
    REFERRAL: 'enums.source.referral',
    COLD_CALL: 'enums.source.coldCall',
    OTHER: 'enums.other',
  },

  // ---------------------------------------------------------------- 商机 / 销售剧本

  /**
   * 商机阶段。销售剧本的阶段动作模板按同一套阶段划分，故共用。
   *
   * <p>1.2 起阶段是**可配置的字典数据**（`opportunity_stage` 表，V79），本表只保留 V79 种子里的 6 个编码，
   * 用途降级为「字典尚未加载 / 加载失败时的展示兜底」：`useOpportunityStages` 会优先用字典里的 `name`，
   * 只有编码命中本表时才走 i18n 文案（这样英文界面下内建阶段仍显示英文）。管理员自建的阶段没有翻译，
   * 只能显示它自己的名字——这是无法回避的，不要试图在这里补齐自定义编码。
   *
   * <p><b>不要用 `Object.keys(ENUM_KEYS.opportunityStage)` 生成选项列表或列头</b>：它是「内建编码的全集」，
   * 不是「当前可选的阶段」。选项列表一律取自接口，否则新增阶段不会出现、停用阶段也照样可选。
   */
  opportunityStage: {
    INITIAL_CONTACT: 'enums.opportunityStage.initialContact',
    NEEDS_CONFIRMED: 'enums.opportunityStage.needsConfirmed',
    PROPOSAL_QUOTED: 'enums.opportunityStage.proposalQuoted',
    NEGOTIATING: 'enums.opportunityStage.negotiating',
    CLOSED_WON: 'enums.opportunityStage.closedWon',
    CLOSED_LOST: 'enums.opportunityStage.closedLost',
  },

  // ---------------------------------------------------------------- 合同 / 报价

  contractStatus: {
    DRAFT: 'common.status.draft',
    PENDING_APPROVAL: 'enums.approvalStatus.pendingApproval',
    APPROVED: 'enums.approvalStatus.approved',
    SIGNED: 'enums.contractStatus.signed',
    EFFECTIVE: 'common.status.effective',
    COMPLETED: 'common.status.completed',
    TERMINATED: 'common.status.terminated',
    REJECTED: 'enums.approvalStatus.rejected',
  },

  quoteStatus: {
    DRAFT: 'common.status.draft',
    PENDING_APPROVAL: 'enums.approvalStatus.pendingApproval',
    APPROVED: 'enums.approvalStatus.approved',
    REJECTED: 'enums.approvalStatus.rejected',
    // 报价单审批通过后可以签署，`SignatureService` 会把状态改成 SIGNED（`QuoteService.STATUS_SIGNED`）。
    // 漏了它，已签署的报价单在列表/详情里会显示原始码 "SIGNED"。
    SIGNED: 'enums.contractStatus.signed',
  },

  contractRenewalGroup: {
    EXPIRING_SOON: 'enums.contractRenewalGroup.expiringSoon',
    EXPIRED_UNRENEWED: 'enums.contractRenewalGroup.expiredUnrenewed',
    RENEWED: 'enums.contractRenewalGroup.renewed',
  },

  // ---------------------------------------------------------------- 审批中心

  /**
   * 审批**实例**状态（一次审批流整体走到哪）。后端 `ApprovalEngineService` 只写这 4 个：
   * PENDING / APPROVED / REJECTED / CANCELED——`CANCELED` 出现在「重提」时旧实例被作废（`L231`）。
   *
   * <p>也刻意不复用 `contractStatus` / `quoteStatus`：虽然 `PENDING` / `APPROVED` / `REJECTED`
   * 三个码同名，但那是「单据本身的业务状态」，中文措辞也不同——合同报价侧 `rejected` 是「已拒绝」，
   * 审批侧是**「已驳回」**。
   *
   * <p><b>实例与任务必须分成两份</b>：`TRANSFERRED` 只写在**任务**上（`L191` 转交的是某一步），
   * 实例永远不会是 TRANSFERRED；而 `CANCELED` 只写在实例上。合成一份的后果是实例表里永远
   * 匹配不到的状态、以及一个永远不出现的选项。
   */
  approvalInstanceStatus: {
    PENDING: 'enums.approvalInstanceStatus.pending',
    APPROVED: 'enums.approvalInstanceStatus.approved',
    REJECTED: 'enums.approvalInstanceStatus.rejected',
    CANCELED: 'enums.approvalInstanceStatus.canceled',
  },

  /** 审批**任务**（某一步）状态。与 {@link ENUM_KEYS.approvalInstanceStatus} 是两个不同的枚举。 */
  approvalTaskStatus: {
    PENDING: 'enums.approvalTaskStatus.pending',
    APPROVED: 'enums.approvalTaskStatus.approved',
    REJECTED: 'enums.approvalTaskStatus.rejected',
    TRANSFERRED: 'enums.approvalTaskStatus.transferred',
  },

  /** 审批动作（审批流水里那一条做了什么）。与 `workflowAction` 无关，别混用。 */
  approvalAction: {
    SUBMIT: 'enums.approvalAction.submit',
    APPROVE: 'enums.approvalAction.approve',
    REJECT: 'enums.approvalAction.reject',
    TRANSFER: 'enums.approvalAction.transfer',
    RENEW: 'enums.approvalAction.renew',
  },

  // ---------------------------------------------------------------- 订单 / 回款

  orderStatus: {
    PENDING: 'enums.orderStatus.pending',
    PARTIAL: 'enums.orderStatus.partial',
    PAID: 'enums.orderStatus.paid',
  },

  paymentMethod: {
    TRANSFER: 'enums.paymentMethod.transfer',
    CASH: 'enums.paymentMethod.cash',
    CHECK: 'enums.paymentMethod.check',
    OTHER: 'enums.other',
  },

  /** 订单回款提醒。 */
  orderReminder: {
    PAID: 'enums.orderReminder.paid',
    NORMAL: 'enums.reminderStatus.normal',
    DUE_SOON: 'enums.reminderStatus.dueSoon',
    OVERDUE: 'enums.reminderStatus.overdue',
  },

  // ---------------------------------------------------------------- 任务 / 日程

  /** 任务提醒。 */
  taskReminder: {
    OVERDUE: 'enums.reminderStatus.overdue',
    TODAY: 'enums.reminderStatus.today',
    NORMAL: 'enums.reminderStatus.normal',
    DONE: 'enums.reminderStatus.done',
  },

  /** 任务关联对象类型。 */
  linkedType: {
    CUSTOMER: 'enums.entity.customer',
    LEAD: 'enums.entity.lead',
    CONTRACT: 'enums.entity.contract',
    ORDER: 'enums.entity.order',
  },

  // ---------------------------------------------------------------- 联系人 / 用户 / 部门

  /** 联系人角色（决策链角色）。 */
  contactRole: {
    DECISION_MAKER: 'enums.contactRole.decisionMaker',
    INFLUENCER: 'enums.contactRole.influencer',
    EVALUATOR: 'enums.contactRole.evaluator',
    CHAMPION: 'enums.contactRole.champion',
    OTHER: 'enums.other',
  },

  userRole: {
    ADMIN: 'enums.userRole.admin',
    SALES: 'enums.userRole.sales',
    SUPPORT: 'enums.userRole.support',
    // 081 新增的 10 个预置角色。改造前这张表只有 3 个内建角色，于是 `labelOf` 的诚实降级
    // （未登记即返回原始码）让用户管理页把 SALES_REP 这类角色**原样显示成码**——
    // 用户看到的是 `SALES_REP` 而不是「销售代表」，而当初新增这些角色就是为了让人看懂分工。
    SALES_MANAGER: 'enums.userRole.salesManager',
    SALES_REP: 'enums.userRole.salesRep',
    SUPPORT_MANAGER: 'enums.userRole.supportManager',
    SUPPORT_AGENT: 'enums.userRole.supportAgent',
    MARKETING_MANAGER: 'enums.userRole.marketingManager',
    MARKETING_SPECIALIST: 'enums.userRole.marketingSpecialist',
    FINANCE_MANAGER: 'enums.userRole.financeManager',
    FINANCE_ACCOUNTANT: 'enums.userRole.financeAccountant',
    ANALYST: 'enums.userRole.analyst',
    VIEWER: 'enums.userRole.viewer',
  },

  dataScope: {
    SELF: 'enums.dataScope.self',
    DEPT: 'enums.dataScope.dept',
    DEPT_AND_CHILD: 'enums.dataScope.deptAndChild',
    ALL: 'enums.dataScope.all',
  },

  // ---------------------------------------------------------------- 营销

  campaignStatus: {
    PLANNING: 'enums.campaignStatus.planning',
    RUNNING: 'enums.campaignStatus.running',
    ENDED: 'enums.campaignStatus.ended',
  },

  campaignChannel: {
    WEBSITE: 'enums.source.website',
    AD: 'enums.source.ad',
    EXHIBITION: 'enums.source.exhibition',
    REFERRAL: 'enums.source.referral',
    EMAIL: 'enums.campaignChannel.email',
    SOCIAL: 'enums.campaignChannel.social',
    OTHER: 'enums.other',
  },

  // ---------------------------------------------------------------- 工作流

  workflowEvent: {
    LEAD_CREATED: 'enums.event.leadCreated',
    OPPORTUNITY_STAGE_CHANGED: 'enums.event.opportunityStageChanged',
    FOLLOW_UP_CREATED: 'enums.event.followUpCreated',
    PAYMENT_RECORDED: 'enums.event.paymentRecorded',
    LEAD_SCORE_THRESHOLD: 'enums.event.leadScoreThreshold',
    TAG_CHANGED: 'enums.event.tagChanged',
  },

  workflowAction: {
    CREATE_TASK: 'enums.workflowAction.createTask',
    ASSIGN: 'enums.workflowAction.assign',
    NOTIFY: 'enums.workflowAction.notify',
    SEND_EMAIL: 'enums.workflowAction.sendEmail',
    ADD_TAG: 'enums.workflowAction.addTag',
  },

  // ---------------------------------------------------------------- 通知 / 跟进 / 集成

  notificationType: {
    WORKFLOW: 'enums.notificationType.workflow',
    TICKET_ASSIGN: 'enums.notificationType.ticketAssign',
    TICKET_REPLY: 'enums.notificationType.ticketReply',
    // 1.3 的 SLA 升级作业新增的两个类型（`NotificationService.TYPE_SLA_*`），
    // 漏了它们，SLA 通知在通知中心里会显示原始码。
    SLA_WARNING: 'enums.notificationType.slaWarning',
    SLA_OVERDUE: 'enums.notificationType.slaOverdue',
  },

  followUpMethod: {
    PHONE: 'enums.followUpMethod.phone',
    EMAIL: 'enums.followUpMethod.email',
    MEETING: 'enums.followUpMethod.meeting',
    OTHER: 'enums.other',
  },

  channelType: {
    WECHAT_WORK: 'enums.channelType.wechatWork',
    DINGTALK: 'enums.channelType.dingtalk',
    CUSTOM: 'enums.channelType.custom',
  },

  webhookEvent: {
    LEAD_CREATED: 'enums.event.leadCreated',
    LEAD_UPDATED: 'enums.event.leadUpdated',
    CUSTOMER_CREATED: 'enums.event.customerCreated',
  },

  // ---------------------------------------------------------------- 导出 / 字段权限 / 知识库

  exportStatus: {
    PENDING: 'enums.exportStatus.pending',
    RUNNING: 'enums.exportStatus.running',
    DONE: 'common.status.completed',
    FAILED: 'enums.exportStatus.failed',
  },

  exportType: {
    LEAD: 'enums.entity.lead',
    CUSTOMER: 'enums.entity.customer',
    OPPORTUNITY: 'enums.entity.opportunity',
    TICKET: 'enums.entity.ticket',
  },

  fieldEntity: {
    LEAD: 'enums.entity.lead',
    CUSTOMER: 'enums.entity.customer',
    OPPORTUNITY: 'enums.entity.opportunity',
    TICKET: 'enums.entity.ticket',
  },

  fieldPermission: {
    HIDDEN: 'enums.fieldPermission.hidden',
    READ_ONLY: 'enums.fieldPermission.readOnly',
    EDITABLE: 'enums.fieldPermission.editable',
  },

  articleCategory: {
    PRODUCT_USAGE: 'enums.articleCategory.productUsage',
    FAULT_TROUBLESHOOTING: 'enums.articleCategory.faultTroubleshooting',
    PROCESS_CONSULT: 'enums.articleCategory.processConsult',
    AFTER_SALES_POLICY: 'enums.articleCategory.afterSalesPolicy',
    OTHER: 'enums.other',
  },
} as const satisfies Record<string, EnumKeyMap>
