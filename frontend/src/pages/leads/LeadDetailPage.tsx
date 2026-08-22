import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  App,
  Button,
  Card,
  Descriptions,
  Space,
  Spin,
  Tag,
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
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
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
      message.error(extractErrorMessage(err, '加载线索失败'))
    } finally {
      setLoading(false)
    }
  }, [id, message])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!lead) {
    return <div>线索不存在</div>
  }

  const canConvert = lead.status === 'NEW' || lead.status === 'WORKING'
  const isConverted = lead.status === 'QUALIFIED'

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/leads')}>
          返回列表
        </Button>
        {canConvert && (
          <Button type="primary" icon={<SwapOutlined />} onClick={() => setConvertOpen(true)}>
            转化为客户
          </Button>
        )}
        {isConverted && (
          <Tag color="success" style={{ fontSize: 14, padding: '4px 12px' }}>
            已转化为客户 #{lead.convertedCustomerId}
          </Tag>
        )}
      </Space>

      <Card title="线索信息" style={{ marginBottom: 16, borderRadius: 10 }}>
        <Descriptions column={3} bordered size="small">
          <Descriptions.Item label="姓名">{lead.name}</Descriptions.Item>
          <Descriptions.Item label="公司">{lead.company}</Descriptions.Item>
          <Descriptions.Item label="职位">{lead.title || '-'}</Descriptions.Item>
          <Descriptions.Item label="电话">{lead.phone || '-'}</Descriptions.Item>
          <Descriptions.Item label="邮箱">{lead.email || '-'}</Descriptions.Item>
          <Descriptions.Item label="评分">
            <Tag color={lead.score >= 70 ? 'green' : lead.score >= 40 ? 'orange' : 'default'}>
              {lead.score}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="来源">{SOURCE_LABELS[lead.source as keyof typeof SOURCE_LABELS] || lead.source}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={STATUS_COLORS[lead.status as keyof typeof STATUS_COLORS]}>
              {STATUS_LABELS[lead.status as keyof typeof STATUS_LABELS]}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="负责人">{lead.ownerName || '未分配（线索池）'}</Descriptions.Item>
          <Descriptions.Item label="备注" span={3}>{lead.remark || '-'}</Descriptions.Item>
          {lead.customFieldValues?.map((cf) => (
            <Descriptions.Item key={cf.fieldId} label={cf.fieldName ?? `字段#${cf.fieldId}`}>
              {cf.value || '-'}
            </Descriptions.Item>
          ))}
          {lead.convertedAt && (
            <Descriptions.Item label="转化时间" span={3}>
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
