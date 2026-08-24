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
  icon: ReactNode
  path: string
}

/** 业务主流程：线索→客户→商机→报价→合同→订单回款→客户服务。 */
export const MAIN_FLOW: FlowDef = {
  id: 'main',
  title: '业务主流程（销售全链路）',
  nodes: [
    { id: 'lead', title: '线索管理', desc: '录入 / 导入 / 分配 / 认领', path: '/leads', color: '#1677ff' },
    { id: 'customer', title: '客户转化', desc: '线索转化为客户 / 新建客户', path: '/customers', color: '#1677ff' },
    { id: 'opportunity', title: '商机推进', desc: '阶段跟进 / 赢单 / 输单', path: '/opportunities', color: '#722ed1' },
    { id: 'quote', title: '报价单', desc: '创建报价 / 提交审批 / 批准', path: '/quotes', color: '#fa8c16' },
    { id: 'contract', title: '合同审批', desc: '合同起草 / 审批 / 生效', path: '/contracts', color: '#52c41a' },
    { id: 'order', title: '订单回款', desc: '订单登记 / 回款计划 / 收款', path: '/orders', color: '#52c41a' },
    { id: 'service', title: '客户服务', desc: '工单受理 / 处理 / 关闭', path: '/tickets', color: '#13c2c2' },
  ],
  edges: [
    { source: 'lead', target: 'customer' },
    { source: 'customer', target: 'opportunity' },
    { source: 'opportunity', target: 'quote' },
    { source: 'quote', target: 'contract' },
    { source: 'contract', target: 'order' },
    { source: 'order', target: 'service' },
  ],
}

/** 角色操作链。 */
export const ROLE_FLOWS: FlowDef[] = [
  {
    id: 'sales',
    title: '销售操作链',
    role: 'SALES',
    nodes: [
      { id: 's1', title: '线索认领', desc: '从线索池认领 / 分配给我', path: '/leads', color: '#1677ff' },
      { id: 's2', title: '客户跟进', desc: '电话 / 会议 / 邮件跟进', path: '/customers', color: '#1677ff' },
      { id: 's3', title: '商机推进', desc: '阶段更新 / 赢单', path: '/opportunities', color: '#722ed1' },
      { id: 's4', title: '报价成交', desc: '报价 → 合同 → 订单', path: '/quotes', color: '#fa8c16' },
      { id: 's5', title: '回款管理', desc: '回款计划 / 登记回款', path: '/orders', color: '#52c41a' },
    ],
    edges: [
      { source: 's1', target: 's2' },
      { source: 's2', target: 's3' },
      { source: 's3', target: 's4' },
      { source: 's4', target: 's5' },
    ],
  },
  {
    id: 'support',
    title: '客服操作链',
    role: 'SUPPORT',
    nodes: [
      { id: 't1', title: '工单接收', desc: '受理客户提交的工单', path: '/tickets', color: '#13c2c2' },
      { id: 't2', title: '问题处理', desc: '开始处理 / 标记解决', path: '/tickets', color: '#13c2c2' },
      { id: 't3', title: '回复客户', desc: '时间线回复 / 反馈', path: '/tickets', color: '#13c2c2' },
      { id: 't4', title: '工单关闭', desc: '确认解决 / 关闭工单', path: '/tickets', color: '#13c2c2' },
      { id: 't5', title: '知识库沉淀', desc: '沉淀常见问题 / 解决方案', path: '/knowledge', color: '#52c41a' },
    ],
    edges: [
      { source: 't1', target: 't2' },
      { source: 't2', target: 't3' },
      { source: 't3', target: 't4' },
      { source: 't4', target: 't5' },
    ],
  },
  {
    id: 'admin',
    title: '管理操作链',
    role: 'ADMIN',
    nodes: [
      { id: 'a1', title: '用户与角色', desc: '用户管理 / 角色权限配置', path: '/users', color: '#722ed1' },
      { id: 'a2', title: '组织与流程', desc: '部门 / 工作流 / SLA 策略', path: '/departments', color: '#722ed1' },
      { id: 'a3', title: '业务配置', desc: '产品 / 合同模板 / 自定义字段', path: '/products', color: '#fa8c16' },
      { id: 'a4', title: '数据洞察', desc: '报表 / 大屏 / 团队排行', path: '/reports', color: '#52c41a' },
      { id: 'a5', title: '系统维护', desc: '审计日志 / 回收站 / 导出中心', path: '/audit-logs', color: '#8c8c8c' },
    ],
    edges: [
      { source: 'a1', target: 'a2' },
      { source: 'a2', target: 'a3' },
      { source: 'a3', target: 'a4' },
      { source: 'a4', target: 'a5' },
    ],
  },
]

/** 全部流程（主流程 + 角色流程）。 */
export const ALL_FLOWS: FlowDef[] = [MAIN_FLOW, ...ROLE_FLOWS]

/** 高频操作快捷入口。 */
export const QUICK_ACTIONS: QuickAction[] = [
  { key: 'create-customer', label: '创建客户', icon: 'TeamOutlined', path: '/customers' },
  { key: 'follow-up', label: '记跟进', icon: 'CommentOutlined', path: '/customers' },
  { key: 'create-opportunity', label: '新增商机', icon: 'FundOutlined', path: '/opportunities' },
  { key: 'create-ticket', label: '新建工单', icon: 'CustomerServiceOutlined', path: '/tickets' },
  { key: 'create-task', label: '创建任务', icon: 'CalendarOutlined', path: '/tasks' },
  { key: 'create-quote', label: '新建报价', icon: 'FileTextOutlined', path: '/quotes' },
]

/**
 * 状态流转流程（各业务对象状态机，含审核通过/驳回分支）。
 * 节点 color 表示状态类别；边 label 标注流转动作（通过/驳回）。
 */
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
      { id: 'cs6', title: '已驳回', desc: '退回修改', color: '#cf1322' },
      { id: 'cs7', title: '已终止', desc: '提前终止', color: '#8c8c8c' },
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
      { id: 'qs5', title: '已驳回', desc: '退回修改', color: '#cf1322' },
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
      { id: 'ts4', title: '已关闭', desc: '客户确认关闭', color: '#8c8c8c' },
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
      { id: 'os6', title: '输单', desc: '竞标失败', color: '#cf1322' },
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
      { id: 'ls4', title: '已废弃', desc: '无效线索', color: '#8c8c8c' },
    ],
    edges: [
      { source: 'ls1', target: 'ls2', label: '认领/分配' },
      { source: 'ls2', target: 'ls3', label: '转化' },
      { source: 'ls2', target: 'ls4', label: '废弃' },
      { source: 'ls1', target: 'ls4', label: '无效' },
    ],
  },
]
