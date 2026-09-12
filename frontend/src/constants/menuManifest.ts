/**
 * 菜单清单（**生成物**）——请勿手改。
 *
 * 由 `frontend/scripts/gen-menu.mjs` 从后端权威定义
 * `backend/src/main/java/com/crm/common/RoleConstants.java` 的 `MENU_TREE` 生成，
 * 共 11 个分组 / 56 个菜单项。
 *
 * 重新生成：`pnpm menu:gen`；陈旧性校验：`pnpm menu:check`（CI 已接入）。
 * 要改菜单的分组、顺序或名称，改 `MENU_TREE` 后重跑生成器，本文件自动跟上。
 *
 * `title` 是权威中文名：它既是缺文案时的降级显示，也是「两侧名称一致」的判据。
 * `i18nKey` 不含 `menu.` 前缀——用 `t(`menu.${i18nKey}`)` 取文案。
 */

export interface MenuManifestItem {
  /** 菜单 key，与 `role_menu.menu_key` 及 `MenuRouteAlignmentTest` 的解析结果逐字一致 */
  readonly menuKey: string
  /** 界面文案键（不含 `menu.` 前缀） */
  readonly i18nKey: string
  /** 权威中文名 */
  readonly title: string
}

export interface MenuManifestGroup {
  /** 分组标题（权威中文名） */
  readonly title: string
  /** 分组文案键（不含 `menu.` 前缀） */
  readonly i18nKey: string
  readonly items: readonly MenuManifestItem[]
}

export const MENU_MANIFEST: readonly MenuManifestGroup[] = [
  {
    title: '首页',
    i18nKey: 'home',
    items: [{ menuKey: 'stats', i18nKey: 'home', title: '首页' }],
  },
  {
    title: '客户管理',
    i18nKey: 'customer',
    items: [
      { menuKey: 'leads', i18nKey: 'leads', title: '线索' },
      { menuKey: 'customers', i18nKey: 'customers', title: '客户' },
      { menuKey: 'contacts', i18nKey: 'contacts', title: '联系人' },
      { menuKey: 'customer-merge', i18nKey: 'merge', title: '查重合并' },
      { menuKey: 'at-risk', i18nKey: 'atRisk', title: '流失预警' },
      { menuKey: 'tags', i18nKey: 'tags', title: '标签与细分' },
    ],
  },
  {
    title: '销售管理',
    i18nKey: 'sales',
    items: [
      { menuKey: 'opportunities', i18nKey: 'opportunities', title: '商机' },
      { menuKey: 'sales-opportunities', i18nKey: 'salesOpportunities', title: '销售机会' },
      { menuKey: 'quotes', i18nKey: 'quotes', title: '报价单' },
      { menuKey: 'visits', i18nKey: 'visits', title: '外勤拜访' },
      { menuKey: 'products', i18nKey: 'products', title: '产品' },
      { menuKey: 'playbook', i18nKey: 'playbook', title: '销售 Playbook' },
      { menuKey: 'quotas', i18nKey: 'quotas', title: '销售配额' },
    ],
  },
  {
    title: '交易管理',
    i18nKey: 'deal',
    items: [
      { menuKey: 'contracts', i18nKey: 'contracts', title: '合同' },
      { menuKey: 'contract-renewal', i18nKey: 'renewal', title: '合同续约' },
      { menuKey: 'orders', i18nKey: 'orders', title: '订单' },
      { menuKey: 'invoices', i18nKey: 'invoices', title: '发票' },
    ],
  },
  {
    title: '营销管理',
    i18nKey: 'marketing',
    items: [
      { menuKey: 'marketing', i18nKey: 'marketingActivity', title: '营销活动' },
      { menuKey: 'marketing/email', i18nKey: 'emailMarketing', title: '邮件营销' },
      { menuKey: 'email-unsubscribes', i18nKey: 'unsubscribe', title: '邮件退订' },
      { menuKey: 'online-forms', i18nKey: 'onlineForms', title: '在线表单' },
      { menuKey: 'landing-pages', i18nKey: 'landingPages', title: '落地页' },
    ],
  },
  {
    title: '客户服务',
    i18nKey: 'service',
    items: [
      { menuKey: 'tickets', i18nKey: 'tickets', title: '工单管理' },
      { menuKey: 'knowledge', i18nKey: 'knowledge', title: '知识库' },
      { menuKey: 'announcements', i18nKey: 'announcements', title: '公告管理' },
      { menuKey: 'portal', i18nKey: 'portal', title: '客户门户' },
      { menuKey: 'satisfaction', i18nKey: 'satisfaction', title: '满意度调查' },
      { menuKey: 'sla-calendar', i18nKey: 'slaCalendar', title: 'SLA 日历' },
    ],
  },
  {
    title: '工作台',
    i18nKey: 'workbench',
    items: [
      { menuKey: 'tasks', i18nKey: 'tasks', title: '任务管理' },
      { menuKey: 'suggestions', i18nKey: 'suggestions', title: '智能建议' },
      { menuKey: 'call-records', i18nKey: 'callRecords', title: '通话记录' },
      { menuKey: 'mail-sync', i18nKey: 'mailSync', title: '邮件同步' },
      { menuKey: 'approvals', i18nKey: 'approvals', title: '我的审批' },
    ],
  },
  {
    title: '数据分析',
    i18nKey: 'data',
    items: [
      { menuKey: 'reports', i18nKey: 'reports', title: '自定义报表' },
      { menuKey: 'stats/leaderboard', i18nKey: 'leaderboard', title: '团队排行' },
      { menuKey: 'exports', i18nKey: 'exports', title: '导出中心' },
      { menuKey: 'exports/scheduled', i18nKey: 'scheduledExports', title: '定时导出' },
      { menuKey: 'data-vision', i18nKey: 'dataVision', title: '数据大屏' },
    ],
  },
  {
    title: '系统管理',
    i18nKey: 'admin',
    items: [
      { menuKey: 'users', i18nKey: 'users', title: '用户管理' },
      { menuKey: 'roles', i18nKey: 'roles', title: '角色权限' },
      { menuKey: 'departments', i18nKey: 'departments', title: '部门管理' },
      { menuKey: 'currencies', i18nKey: 'currencies', title: '多币种' },
    ],
  },
  {
    title: '流程配置',
    i18nKey: 'config',
    items: [
      { menuKey: 'workflows', i18nKey: 'workflows', title: '工作流' },
      { menuKey: 'approval-flows', i18nKey: 'approvalFlows', title: '审批流' },
      { menuKey: 'sla-policies', i18nKey: 'slaPolicies', title: 'SLA 策略' },
      { menuKey: 'opportunity-stages', i18nKey: 'opportunityStages', title: '商机阶段' },
      { menuKey: 'contract-templates', i18nKey: 'contractTemplates', title: '合同模板' },
      { menuKey: 'settings/custom-fields', i18nKey: 'customFields', title: '自定义字段' },
      { menuKey: 'field-permissions', i18nKey: 'fieldPermissions', title: '字段权限' },
      { menuKey: 'custom-objects', i18nKey: 'customObjects', title: '自定义对象' },
      { menuKey: 'open-platform', i18nKey: 'openPlatform', title: '开放平台' },
      { menuKey: 'integration-hub', i18nKey: 'integrationHub', title: '集成中心' },
    ],
  },
  {
    title: '审计维护',
    i18nKey: 'audit',
    items: [
      { menuKey: 'audit-logs', i18nKey: 'auditLogs', title: '审计日志' },
      { menuKey: 'recycle-bin', i18nKey: 'recycleBin', title: '回收站' },
      { menuKey: 'data-retention', i18nKey: 'dataRetention', title: '数据保留策略' },
    ],
  },
]
