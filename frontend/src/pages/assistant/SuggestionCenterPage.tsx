import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Empty, List, Space, Tag, Typography } from 'antd'
import { CloseOutlined, RightOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { fetchSuggestions, ignoreSuggestion } from '../../services/suggestionService'
import type { SmartSuggestion } from '../../types/suggestion'

const { Title, Paragraph, Text } = Typography

export default function SuggestionCenterPage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [ignoring, setIgnoring] = useState<number | null>(null)

  const TYPE_META: Record<string, { label: string; color: string }> = {
    CUSTOMER_AT_RISK: { label: t('pages.suggestionCenter.atRiskCustomers'), color: 'red' },
    OPPORTUNITY_STALLED: { label: t('pages.suggestionCenter.stalledOpportunities'), color: 'orange' },
    CUSTOMER_FOLLOWUP: { label: t('pages.suggestionCenter.followUpCustomers'), color: 'blue' },
    LEAD_HIGH_SCORE: { label: t('pages.suggestionCenter.highScoreLeads'), color: 'green' },
  }

  const PRIORITY_LABEL: Record<string, string> = {
    URGENT: t('pages.suggestionCenter.priorityUrgent'),
    IMPORTANT: t('pages.suggestionCenter.priorityImportant'),
    NORMAL: t('pages.suggestionCenter.priorityNormal'),
  }

  const { data: items = [], isLoading } = useQuery({
    queryKey: ['suggestions'],
    queryFn: () => fetchSuggestions(20),
  })

  const onIgnore = async (item: SmartSuggestion) => {
    setIgnoring(item.entityId)
    try {
      await ignoreSuggestion(item.type, item.entityId)
      message.success(t('pages.suggestionCenter.msgIgnored'))
      queryClient.invalidateQueries({ queryKey: ['suggestions'] })
      queryClient.invalidateQueries({ queryKey: ['suggestion-summary'] })
    } catch {
      message.error(t('pages.suggestionCenter.msgOperationFailed'))
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
          {t('pages.suggestionCenter.title')}
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          {t('pages.suggestionCenter.subtitle')}
        </Paragraph>
      </div>

      <Card style={{ borderRadius: 10 }} styles={{ body: { padding: 0 } }}>
        <List<SmartSuggestion>
          loading={isLoading}
          dataSource={items}
          locale={{ emptyText: <Empty description={t('pages.suggestionCenter.emptyText')} /> }}
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
                    {t('pages.suggestionCenter.btnIgnore')}
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
