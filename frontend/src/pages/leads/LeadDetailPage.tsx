import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  App,
  Button,
  Card,
  Descriptions,
  Result,
  Space,
  Tag,
  Typography,
} from 'antd'
import {
  ArrowLeftOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { fetchLead } from '../../services/leadService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  SOURCE_LABELS,
  STATUS_COLORS,
  STATUS_LABELS,
  type LeadDetail,
} from '../../types/lead'
import FollowUpTimeline from '../../components/FollowUpTimeline'
import LeadConvertModal from '../../components/LeadConvertModal'

export default function LeadDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const { message } = App.useApp()
  const [loading, setLoading] = useState(true)
  const [lead, setLead] = useState<LeadDetail | null>(null)
  const [convertOpen, setConvertOpen] = useState(false)

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const data = await fetchLead(Number(id))
      setLead(data)
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.lead.detail.msgLoadFailed')))
    } finally {
      setLoading(false)
    }
  }, [id, message])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) {
    return <Card loading style={{ minHeight: 300 }} />
  }

  if (!lead) {
    return (
      <Result
        status="404"
        title={t('pages.lead.detail.notFound')}
        extra={
          <Link to="/leads">
            <Button type="primary" icon={<ArrowLeftOutlined />}>
              {t('pages.lead.detail.backToList')}
            </Button>
          </Link>
        }
      />
    )
  }

  const canConvert = lead.status === 'NEW' || lead.status === 'WORKING'
  const isConverted = lead.status === 'QUALIFIED'

  return (
    <div>
      <Link to="/leads" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          {t('pages.lead.detail.backToList')}
        </Button>
      </Link>

      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 4 }}>
            {lead.name}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            {lead.company || t('pages.lead.detail.unfilledCompany')}
            {lead.ownerName ? ` · ${t('pages.lead.detail.ownerPrefix')}${lead.ownerName}` : ` · ${t('pages.lead.detail.pool')}`}
          </Typography.Text>
        </div>
        <Space>
          {isConverted && (
            <Tag color="success">{t('pages.lead.detail.convertedTag', { id: lead.convertedCustomerId })}</Tag>
          )}
          {canConvert && (
            <Button type="primary" icon={<SwapOutlined />} onClick={() => setConvertOpen(true)}>
              {t('pages.lead.detail.convertToCustomer')}
            </Button>
          )}
        </Space>
      </div>

      <Card title={t('pages.lead.detail.leadInfo')} style={{ marginBottom: 16, borderRadius: 10 }}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label={t('pages.lead.list.colName')}>{lead.name}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colCompany')}>{lead.company}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colTitle')}>{lead.title || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colPhone')}>{lead.phone || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colEmail')}>{lead.email || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colScore')}>
            <Tag color={lead.score >= 70 ? 'green' : lead.score >= 40 ? 'orange' : 'default'}>
              {lead.score}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colSource')}>{SOURCE_LABELS[lead.source as keyof typeof SOURCE_LABELS] || lead.source}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colStatus')}>
            <Tag color={STATUS_COLORS[lead.status as keyof typeof STATUS_COLORS]}>
              {STATUS_LABELS[lead.status as keyof typeof STATUS_LABELS]}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colOwner')}>{lead.ownerName || t('pages.lead.detail.unassignedPool')}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.detail.labelRemark')} span={2}>{lead.remark || '-'}</Descriptions.Item>
          {lead.customFieldValues?.map((cf) => (
            <Descriptions.Item key={cf.fieldId} label={cf.fieldName ?? `${t('pages.customer.detail.fieldPrefix')}${cf.fieldId}`}>
              {cf.value || '-'}
            </Descriptions.Item>
          ))}
          {lead.convertedAt && (
            <Descriptions.Item label={t('pages.lead.detail.convertedAt')} span={2}>
              {new Date(lead.convertedAt).toLocaleString('zh-CN')}
            </Descriptions.Item>
          )}
        </Descriptions>
      </Card>

      <FollowUpTimeline leadId={lead.id} />

      <LeadConvertModal
        open={convertOpen}
        leadId={lead.id}
        onCancel={() => setConvertOpen(false)}
        onSuccess={() => {
          setConvertOpen(false)
          void load()
        }}
      />
    </div>
  )
}
