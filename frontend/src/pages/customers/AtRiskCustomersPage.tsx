import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Form, Input, Modal, Select, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { fetchAtRiskCustomers } from '../../services/customerService'
import { createFollowUp } from '../../services/followUpService'
import { extractErrorMessage } from '../../services/apiClient'
import type { CustomerHealthBrief } from '../../types/customer'
import type { FollowUpMethod } from '../../types/followUp'

const { Title, Paragraph } = Typography

const METHOD_LABELS: Record<string, string> = {
  PHONE: '电话',
  EMAIL: '邮件',
  MEETING: '会议',
  OTHER: '其他',
}

interface FollowUpFormValues {
  method: FollowUpMethod
  content: string
}

export default function AtRiskCustomersPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [followOpen, setFollowOpen] = useState(false)
  const [current, setCurrent] = useState<CustomerHealthBrief | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<FollowUpFormValues>()

  const openFollow = (row: CustomerHealthBrief) => {
    setCurrent(row)
    form.resetFields()
    setFollowOpen(true)
  }

  const onFollow = async () => {
    if (!current) return
    const values = await form.validateFields()
    setSaving(true)
    try {
      await createFollowUp({
        customerId: current.id,
        method: values.method,
        content: values.content,
      })
      message.success('跟进已记录，客户已移出预警列表')
      setFollowOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '跟进失败'))
    } finally {
      setSaving(false)
    }
  }

  const columns: ProColumns<CustomerHealthBrief>[] = [
    { title: '客户', dataIndex: 'name', render: (_, row) => <a onClick={() => navigate(`/customers/${row.id}`)}>{row.name}</a> },
    { title: '公司', dataIndex: 'company', search: false },
    {
      title: '健康度',
      dataIndex: 'healthScore',
      search: false,
      width: 100,
      render: (_, row) => {
        const score = row.healthScore
        const color = score < 60 ? 'red' : score < 80 ? 'gold' : 'green'
        return <Tag color={color}>{score}</Tag>
      },
    },
    { title: '无活动天数', dataIndex: 'daysInactive', search: false, width: 110 },
    {
      title: '最近跟进',
      dataIndex: 'lastFollowUpAt',
      search: false,
      render: (_, row) => (row.lastFollowUpAt ? row.lastFollowUpAt.replace('T', ' ').slice(0, 16) : '-'),
    },
    { title: '负责人', dataIndex: 'ownerName', search: false, render: (_, row) => row.ownerName ?? '-' },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, row) => [
        <a key="follow" onClick={() => openFollow(row)}>
          <PlusOutlined /> 跟进
        </a>,
      ],
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Title level={4} style={{ marginBottom: 4 }}>
          客户流失预警
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          超过阈值天数无跟进且无新订单的客户，按健康度升序；建议优先跟进低分客户。
        </Paragraph>
      </div>

      <ProTable<CustomerHealthBrief>
        size="small"
        headerTitle="预警列表"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchAtRiskCustomers({
            daysInactive: 45,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
      />

      <Modal
        title={current ? `跟进「${current.name}」` : '跟进'}
        open={followOpen}
        onOk={() => void onFollow()}
        onCancel={() => setFollowOpen(false)}
        okText="保存"
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} name="atRiskFollowUp" layout="vertical">
          <Form.Item name="method" label="跟进方式" rules={[{ required: true, message: '请选择跟进方式' }]}>
            <Select
              placeholder="选择跟进方式"
              options={Object.entries(METHOD_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="content" label="跟进内容" rules={[{ required: true, message: '请输入跟进内容' }]}>
            <Input.TextArea rows={3} placeholder="记录本次跟进情况" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
