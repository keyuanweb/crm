/** 审批流（033）。 */
export interface FlowNode {
  name: string
  approverType: 'ROLE' | 'USER' | 'MANAGER'
  approverValue?: string
}

export interface ConditionJson {
  field: string
  op: string
  value: number
  extraNodes: FlowNode[]
}

export interface ApprovalFlow {
  id: number
  name: string
  businessType: string
  nodes: string
  conditionJson?: string
  enabled: boolean
}

export interface ApprovalTask {
  id: number
  instanceId: number
  nodeName: string
  approverType: string
  approverValue?: string
  approverId?: number
  status: string
  comment?: string
  seq: number
  createdAt?: string
}

export interface ApprovalInstance {
  id: number
  flowId: number
  businessType: string
  businessId: number
  title: string
  status: string
  currentTaskId?: number
  initiator: number
  createdAt?: string
}

export interface ApprovalLog {
  id: number
  action: string
  operator: number
  comment?: string
  createdAt?: string
}

export interface ApprovalDetail {
  instance: ApprovalInstance
  tasks: ApprovalTask[]
  logs: ApprovalLog[]
}
