import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  App,
  Button,
  Card,
  Descriptions,
  Progress,
  Result,
  Space,
  Typography,
} from 'antd'
import {
  ArrowLeftOutlined,
  SwapOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { fetchLead } from '../../services/leadService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  SOURCE_LABELS,
  STATUS_LABELS,
  type LeadDetail,
} from '../../types/lead'
import FollowUpTimeline from '../../components/FollowUpTimeline'
import LeadConvertModal from '../../components/LeadConvertModal'
import { StatusTag } from '../../components/ui'

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
    // t 用于错误文案；i18next 在语言切换时会给出新的 t 引用，故一并列入依赖
  }, [id, message, t])

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

  // 状态 Tag
  const renderStatus = (status?: string) => {
    if (!status) return <StatusTag>-</StatusTag>
    const type: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
      NEW: 'info',
      WORKING: 'warning',
      QUALIFIED: 'success',
      DISQUALIFIED: 'danger',
    }
    const label = STATUS_LABELS[status as keyof typeof STATUS_LABELS] || status
    return <StatusTag type={type[status] ?? 'default'}>{label}</StatusTag>
  }

  // 来源 Tag
  const renderSource = (source?: string) => {
    if (!source) return <StatusTag>-</StatusTag>
    const label = SOURCE_LABELS[source as keyof typeof SOURCE_LABELS] || source
    return <StatusTag type="info">{label}</StatusTag>
  }

  // 评分可视化
  const renderScore = (score?: number) => {
    if (score == null) return <span>-</span>
    const color = score >= 70 ? 'var(--color-success)' : score >= 40 ? 'var(--color-warning)' : 'var(--color-danger)'
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{ fontSize: 32, fontWeight: 700, color }}>
          {score}
        </div>
        <div style={{ flex: 1, maxWidth: 200 }}>
          <Progress
            percent={score}
            strokeColor={color}
            showInfo={false}
          />
        </div>
      </div>
    )
  }

  return (
    <div>
      {/* 返回按钮 */}
      <Link to="/leads" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />} style={{ paddingLeft: 0 }}>
          {t('pages.lead.detail.backToList')}
        </Button>
      </Link>

      {/* 顶部信息栏 */}
      <Card
        bordered={false}
        style={{
          borderRadius: 'var(--radius-lg)',
          marginBottom: 20,
          background: 'linear-gradient(135deg, var(--color-primary-light) 0%, var(--color-bg-card) 100%)',
          border: '1px solid var(--color-border-light)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, minWidth: 0 }}>
            {/* 线索图标 */}
            <div
              style={{
                width: 56,
                height: 56,
                borderRadius: 'var(--radius-lg)',
                background: 'var(--color-primary)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#fff',
                fontSize: 24,
                flexShrink: 0,
              }}
            >
              💡
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4, flexWrap: 'wrap' }}>
                <Typography.Title level={4} style={{ margin: 0 }}>
                  {lead.name}
                </Typography.Title>
                <StatusTag type={lead.status === 'QUALIFIED' ? 'success' : lead.status === 'WORKING' ? 'warning' : lead.status === 'NEW' ? 'info' : 'default'}>
                  {STATUS_LABELS[lead.status as keyof typeof STATUS_LABELS] || lead.status}
                </StatusTag>
                {isConverted && (
                  <StatusTag type="success">
                    {t('pages.lead.detail.convertedTag', { id: lead.convertedCustomerId })}
                  </StatusTag>
                )}
              </div>
              <Typography.Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                #{lead.id} · {lead.company || t('pages.lead.detail.unfilledCompany')}
              </Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                {lead.ownerName
                  ? `${t('pages.lead.detail.ownerPrefix')}${lead.ownerName}`
                  : t('pages.lead.detail.pool')}
              </Typography.Text>
            </div>
          </div>
          <Space>
            {canConvert && (
              <Button type="primary" icon={<SwapOutlined />} onClick={() => setConvertOpen(true)}>
                {t('pages.lead.detail.convertToCustomer')}
              </Button>
            )}
          </Space>
        </div>
      </Card>

      {/* 统计卡片行 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 20 }}>
        <Card bordered={false} className="stat-card">
          <div className="stat-card-label">{t('pages.lead.list.colScore')}</div>
          <div style={{ marginTop: 8 }}>{renderScore(lead.score)}</div>
        </Card>
        <Card bordered={false} className="stat-card">
          <div className="stat-card-label">{t('pages.lead.list.colSource')}</div>
          <div style={{ marginTop: 8 }}>{renderSource(lead.source)}</div>
        </Card>
        <Card bordered={false} className="stat-card">
          <div className="stat-card-label">{t('pages.lead.list.colOwner')}</div>
          <div style={{ marginTop: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
            <UserOutlined style={{ color: 'var(--color-text-tertiary)' }} />
            <span style={{ fontWeight: 500 }}>{lead.ownerName || t('pages.lead.detail.unassignedPool')}</span>
          </div>
        </Card>
      </div>

      {/* 基本信息 */}
      <Card
        title={t('pages.lead.detail.leadInfo')}
        bordered={false}
        style={{ borderRadius: 'var(--radius-lg)', marginBottom: 20 }}
      >
        <Descriptions
          column={{ xs: 1, sm: 2, md: 3 }}
          bordered
          size="small"
          layout="horizontal"
        >
          <Descriptions.Item label={t('pages.lead.list.colName')}>{lead.name}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colCompany')}>{lead.company || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colTitle')}>{lead.title || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colPhone')}>{lead.phone || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colEmail')}>{lead.email || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colSource')}>{renderSource(lead.source)}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colStatus')}>{renderStatus(lead.status)}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.list.colOwner')}>{lead.ownerName || t('pages.lead.detail.unassignedPool')}</Descriptions.Item>
          <Descriptions.Item label={t('pages.lead.detail.labelRemark')} span={3}>{lead.remark || '-'}</Descriptions.Item>
          {lead.customFieldValues?.map((cf) => (
            <Descriptions.Item key={cf.fieldId} label={cf.fieldName ?? `${t('pages.customer.detail.fieldPrefix')}${cf.fieldId}`}>
              {cf.value || '-'}
            </Descriptions.Item>
          ))}
          {lead.convertedAt && (
            <Descriptions.Item label={t('pages.lead.detail.convertedAt')} span={3}>
              {new Date(lead.convertedAt).toLocaleString('zh-CN')}
            </Descriptions.Item>
          )}
        </Descriptions>
      </Card>

      {/* 跟进记录 */}
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
