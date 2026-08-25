export interface IntegrationChannel {
  id: number
  channelType: 'WECHAT_WORK' | 'DINGTALK' | 'CUSTOM'
  name: string
  webhookUrl: string
  enabled: boolean
  createdAt?: string
}

export const CHANNEL_TYPE_LABELS: Record<string, string> = {
  WECHAT_WORK: '企业微信',
  DINGTALK: '钉钉',
  CUSTOM: '自定义',
}
