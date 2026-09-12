import type { ReactNode } from 'react'

/** 流程节点（029-usage-map）。 */
export interface FlowNode {
  id: string
  title: string
  desc: string
  /** 页面入口（点击跳转；无则仅展示）。 */
  path?: string
  /** 节点主色（默认蓝）。 */
  color?: string
  /** 节点阴影效果（如 `0 2px 8px rgba(0,0,0,0.15)`）。 */
  shadow?: string
  /** 节点圆角半径（像素）。 */
  borderRadius?: number
  /** 悬停时边框高亮颜色。 */
  hoverHighlight?: string
  /** 是否为异常状态节点（用于状态流转图）。 */
  warning?: boolean
}

/** 流程定义。 */
export interface FlowDef {
  id: string
  title: string
  /** 关联角色（SALES/SUPPORT/ADMIN；主流程无）。 */
  role?: string
  nodes: FlowNode[]
  edges: { source: string; target: string; label?: string }[]
}

/** 高频操作。 */
export interface QuickAction {
  key: string
  label: string
  /** 图标：emoji 文本（原先写成 `<span>📝</span>`，但调用方按字符串键取值，实际从未渲染）。 */
  icon: string
  path: string
}

/** 角色枚举。 */
export type RoleType = 'SALES' | 'SUPPORT' | 'ADMIN' | 'EXECUTIVE'

/** 角色显示信息。 */
export interface RoleInfo {
  key: RoleType
  label: string
  icon: ReactNode
  description: string
  flowId: string
}

/** 所有流程（含主流程）。 */
export const ALL_FLOWS: FlowDef[] = [
  {
    id: 'main',
    title: '主业务流程',
    nodes: [
      {
        id: 'm1',
        title: '线索',
        desc: '管理线索池，分配线索给销售',
        path: '/leads',
        color: '#1677ff',
      },
      {
        id: 'm2',
        title: '客户',
        desc: '客户信息维护、跟进记录',
        path: '/customers',
        color: '#1677ff',
      },
      {
        id: 'm3',
        title: '商机',
        desc: '商机阶段管理、预测成交',
        path: '/opportunities',
        color: '#fa8c16',
      },
      {
        id: 'm4',
        title: '报价单',
        desc: '产品报价、折扣审批',
        path: '/quotes',
        color: '#fa8c16',
      },
      {
        id: 'm5',
        title: '合同',
        desc: '合同起草、审批、签署',
        path: '/contracts',
        color: '#52c41a',
      },
      {
        id: 'm6',
        title: '订单',
        desc: '订单处理、发货跟踪',
        path: '/orders',
        color: '#52c41a',
      },
      {
        id: 'm7',
        title: '售后',
        desc: '工单管理、客户服务',
        path: '/support',
        color: '#722ed1',
      },
    ],
    edges: [
      { source: 'm1', target: 'm2', label: '转化' },
      { source: 'm2', target: 'm3', label: '创建' },
      { source: 'm3', target: 'm4', label: '报价' },
      { source: 'm4', target: 'm5', label: '成交' },
      { source: 'm5', target: 'm6', label: '下单' },
      { source: 'm6', target: 'm7', label: '服务' },
    ],
  },
  {
    id: 'sales',
    title: '销售流程',
    role: 'SALES',
    nodes: [
      {
        id: 's1',
        title: '线索获取',
        desc: '从市场活动、推荐等渠道获取线索',
        path: '/leads',
        color: '#1677ff',
      },
      {
        id: 's2',
        title: '需求分析',
        desc: '与客户沟通，明确需求',
        path: '/customers',
        color: '#1677ff',
      },
      {
        id: 's3',
        title: '方案报价',
        desc: '制定方案并提交报价',
        path: '/quotes',
        color: '#fa8c16',
      },
      {
        id: 's4',
        title: '签约',
        desc: '谈判并签订合同',
        path: '/contracts',
        color: '#52c41a',
      },
    ],
    edges: [
      { source: 's1', target: 's2', label: '跟进' },
      { source: 's2', target: 's3', label: '方案' },
      { source: 's3', target: 's4', label: '签约' },
    ],
  },
  {
    id: 'support',
    title: '售后服务流程',
    role: 'SUPPORT',
    nodes: [
      {
        id: 'su1',
        title: '工单提交',
        desc: '客户提交服务请求',
        path: '/support/tickets',
        color: '#fa8c16',
      },
      {
        id: 'su2',
        title: '工单处理',
        desc: '技术人员处理工单',
        path: '/support/tickets',
        color: '#1677ff',
      },
      {
        id: 'su3',
        title: '客户确认',
        desc: '客户确认问题已解决',
        path: '/support/tickets',
        color: '#52c41a',
      },
    ],
    edges: [
      { source: 'su1', target: 'su2', label: '分配' },
      { source: 'su2', target: 'su3', label: '解决' },
    ],
  },
  {
    id: 'admin',
    title: '管理流程',
    role: 'ADMIN',
    nodes: [
      {
        id: 'a1',
        title: '系统配置',
        desc: '系统参数配置、权限管理',
        path: '/admin/settings',
        color: '#722ed1',
      },
      {
        id: 'a2',
        title: '报表分析',
        desc: '业务数据报表、分析洞察',
        path: '/admin/reports',
        color: '#722ed1',
      },
      {
        id: 'a3',
        title: '审计日志',
        desc: '操作日志、审计追踪',
        path: '/admin/audit',
        color: '#722ed1',
      },
    ],
    edges: [
      { source: 'a1', target: 'a2', label: '数据' },
      { source: 'a2', target: 'a3', label: '记录' },
    ],
  },
]

/** 主流程（默认展示）。 */
export const MAIN_FLOW = ALL_FLOWS[0]

/** 状态流转定义。 */
export const STATE_FLOWS: FlowDef[] = [
  {
    id: 'contract-state',
    title: '合同状态流转（含审批）',
    nodes: [
      { id: 'cs1', title: '草稿', desc: '合同起草中', color: '#8c8c8c' },
      { id: 'cs2', title: '待审批', desc: '提交审批', color: '#fa8c16' },
      { id: 'cs3', title: '已批准', desc: '审批通过', color: '#1677ff' },
      { id: 'cs4', title: '生效中', desc: '合同生效', color: '#52c41a' },
      { id: 'cs5', title: '已完成', desc: '合同结项', color: '#52c41a' },
      { id: 'cs6', title: '已驳回', desc: '退回修改', color: '#cf1322', warning: true },
      { id: 'cs7', title: '已终止', desc: '提前终止', color: '#8c8c8c', warning: true },
    ],
    edges: [
      { source: 'cs1', target: 'cs2', label: '提交' },
      { source: 'cs2', target: 'cs3', label: '通过' },
      { source: 'cs2', target: 'cs6', label: '驳回' },
      { source: 'cs6', target: 'cs2', label: '修改后重提' },
      { source: 'cs3', target: 'cs4', label: '生效' },
      { source: 'cs4', target: 'cs5', label: '结项' },
      { source: 'cs4', target: 'cs7', label: '终止' },
    ],
  },
  {
    id: 'quote-state',
    title: '报价单状态流转（含审批）',
    nodes: [
      { id: 'qs1', title: '草稿', desc: '报价编制中', color: '#8c8c8c' },
      { id: 'qs2', title: '待审批', desc: '提交审批', color: '#fa8c16' },
      { id: 'qs3', title: '已批准', desc: '审批通过', color: '#1677ff' },
      { id: 'qs4', title: '已发送', desc: '发送客户', color: '#52c41a' },
      { id: 'qs5', title: '已驳回', desc: '退回修改', color: '#cf1322', warning: true },
    ],
    edges: [
      { source: 'qs1', target: 'qs2', label: '提交' },
      { source: 'qs2', target: 'qs3', label: '通过' },
      { source: 'qs2', target: 'qs5', label: '驳回' },
      { source: 'qs5', target: 'qs2', label: '修改后重提' },
      { source: 'qs3', target: 'qs4', label: '发送' },
    ],
  },
  {
    id: 'ticket-state',
    title: '工单状态流转',
    nodes: [
      { id: 'ts1', title: '待处理', desc: '工单已受理', color: '#fa8c16' },
      { id: 'ts2', title: '处理中', desc: '正在处理', color: '#1677ff' },
      { id: 'ts3', title: '已解决', desc: '已给出方案', color: '#52c41a' },
      { id: 'ts4', title: '已关闭', desc: '客户确认关闭', color: '#8c8c8c', warning: true },
    ],
    edges: [
      { source: 'ts1', target: 'ts2', label: '开始处理' },
      { source: 'ts2', target: 'ts3', label: '标记解决' },
      { source: 'ts3', target: 'ts4', label: '关闭' },
      { source: 'ts2', target: 'ts1', label: '退回' },
    ],
  },
  {
    id: 'opportunity-stage',
    title: '商机阶段流转',
    nodes: [
      { id: 'os1', title: '初次接触', desc: '建立联系', color: '#1677ff' },
      { id: 'os2', title: '需求确认', desc: '明确需求', color: '#1677ff' },
      { id: 'os3', title: '方案报价', desc: '方案与报价', color: '#fa8c16' },
      { id: 'os4', title: '谈判中', desc: '商务谈判', color: '#fa8c16' },
      { id: 'os5', title: '赢单', desc: '成交', color: '#52c41a' },
      { id: 'os6', title: '输单', desc: '竞标失败', color: '#cf1322', warning: true },
    ],
    edges: [
      { source: 'os1', target: 'os2', label: '推进' },
      { source: 'os2', target: 'os3', label: '推进' },
      { source: 'os3', target: 'os4', label: '推进' },
      { source: 'os4', target: 'os5', label: '成交' },
      { source: 'os4', target: 'os6', label: '失败' },
      { source: 'os3', target: 'os6', label: '失败' },
    ],
  },
  {
    id: 'lead-state',
    title: '线索状态流转',
    nodes: [
      { id: 'ls1', title: '新线索', desc: '线索池', color: '#1677ff' },
      { id: 'ls2', title: '跟进中', desc: '销售跟进', color: '#fa8c16' },
      { id: 'ls3', title: '已转化', desc: '转为客户', color: '#52c41a' },
      { id: 'ls4', title: '已废弃', desc: '无效线索', color: '#8c8c8c', warning: true },
    ],
    edges: [
      { source: 'ls1', target: 'ls2', label: '认领/分配' },
      { source: 'ls2', target: 'ls3', label: '转化' },
      { source: 'ls2', target: 'ls4', label: '废弃' },
      { source: 'ls1', target: 'ls4', label: '无效' },
    ],
  },
]

/** 高频操作列表。 */
export const QUICK_ACTIONS: QuickAction[] = [
  {
    key: 'create-lead',
    label: '新建线索',
    icon: '📝',
    path: '/leads/new',
  },
  {
    key: 'create-customer',
    label: '新建客户',
    icon: '🏢',
    path: '/customers/new',
  },
  {
    key: 'create-opportunity',
    label: '新建商机',
    icon: '💰',
    path: '/opportunities/new',
  },
  {
    key: 'create-quote',
    label: '新建报价单',
    icon: '📄',
    path: '/quotes/new',
  },
  {
    key: 'create-contract',
    label: '新建合同',
    icon: '📑',
    path: '/contracts/new',
  },
  {
    key: 'create-ticket',
    label: '新建工单',
    icon: '🔧',
    path: '/support/tickets/new',
  },
  {
    key: 'dashboard',
    label: '工作台',
    icon: '📊',
    path: '/dashboard',
  },
  {
    key: 'reports',
    label: '报表中心',
    icon: '📈',
    path: '/reports',
  },
]
