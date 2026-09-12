export interface IntegrationChannel {
  id: number
  channelType: 'WECHAT_WORK' | 'DINGTALK' | 'CUSTOM'
  name: string
  webhookUrl: string
  enabled: boolean
  createdAt?: string
}
