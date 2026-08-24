import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Empty, List, Space, Tag, Typography } from 'antd'
import { CloseOutlined, RightOutlined } from '@ant-design/icons'
import { fetchSuggestions, ignoreSuggestion } from '../../services/suggestionService'
import type { SmartSuggestion } from '../../types/suggestion'

const { Title, Paragraph, Text } = Typography

const TYPE_META: Record<string, { label: string; color: string }> = {
  CUSTOMER_AT_RISK: { label: '流失预警', color: 'red' },
  OPPORTUNITY_STALLED: { label: '商机停滞', color: 'orange' },
  CUSTOMER_FOLLOWUP: { label: '待跟进', color: 'blue' },
  LEAD_HIGH_SCORE: { label: '高分线索', color: 'green' },
}

const PRIORITY_LABEL: Record<string, string> = {
  URGENT: '紧急',
  IMPORTANT: '重要',
  NORMAL: '普通',
}

export default function SuggestionCenterPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [ignoring, setIgnoring] = useState<number | null>(null)

  const { data: items = [], isLoading } = useQuery({
    queryKey: ['suggestions'],
    queryFn: () => fetchSuggestions(20),
  })

  const onIgnore = async (item: SmartSuggestion) => {
    setIgnoring(item.entityId)
    try {
      await ignoreSuggestion(item.type, item.entityId)
      message.success('已忽略')
      queryClient.invalidateQueries({ queryKey: ['suggestions'] })
      queryClient.invalidateQueries({ queryKey: ['suggestion-summary'] })
    } catch {
      message.error('操作失败')
    } finally {
      setIgnoring(null)
    }
  }

  const onNavigate = (item: SmartSuggestion) => {
    if (item.entityType === 'CUSTOMER') navigate(`/customers/${item.entityId}`)
    else if (item.entityType === 'OPPORTUNITY') navigate('/opportunities')
    else navigate('/leads')
  }

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <Title level={4} style={{ marginBottom: 4 }}>
          AI 智能建议
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          基于客户健康度、商机停滞、跟进活跃度与线索评分，自动识别需要优先处理的事项。
        </Paragraph>
      </div>

      <Card style={{ borderRadius: 10 }} styles={{ body: { padding: 0 } }}>
        <List<SmartSuggestion>
          loading={isLoading}
          dataSource={items}
          locale={{ emptyText: <Empty description="暂无建议，一切正常" /> }}
          renderItem={(item) => {
            const meta = TYPE_META[item.type] ?? { label: item.type, color: 'default' }
            return (
              <List.Item
                style={{ padding: '14px 20px', cursor: 'pointer' }}
                actions={[
                  <Button
                    key="ignore"
                    type="text"
                    size="small"
                    icon={<CloseOutlined />}
                    loading={ignoring === item.entityId}
                    onClick={(e) => {
                      e.stopPropagation()
                      void onIgnore(item)
                    }}
                  >
                    忽略
                  </Button>,
                ]}
                onClick={() => onNavigate(item)}
              >
                <List.Item.Meta
                  title={
                    <Space>
                      <Tag color={meta.color}>{meta.label}</Tag>
                      <Text strong>{item.title}</Text>
                      <Tag bordered={false} color={item.priority === 'URGENT' ? 'red' : item.priority === 'IMPORTANT' ? 'orange' : 'default'}>
                        {PRIORITY_LABEL[item.priority] ?? item.priority}
                      </Tag>
                    </Space>
                  }
                  description={<Text type="secondary">{item.reason}</Text>}
                />
                <RightOutlined style={{ color: '#bfbfbf', fontSize: 12 }} />
              </List.Item>
            )
          }}
        />
      </Card>
    </div>
  )
}
