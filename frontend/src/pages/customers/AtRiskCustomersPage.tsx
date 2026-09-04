import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Form, Input, Modal, Select, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { fetchAtRiskCustomers } from '../../services/customerService'
import { createFollowUp } from '../../services/followUpService'
import { extractErrorMessage } from '../../services/apiClient'
import type { CustomerHealthBrief } from '../../types/customer'
import type { FollowUpMethod } from '../../types/followUp'

const { Title, Paragraph } = Typography

interface FollowUpFormValues {
  method: FollowUpMethod
  content: string
}

export default function AtRiskCustomersPage() {
  const { t } = useTranslation()

  const METHOD_LABELS: Record<string, string> = {
    PHONE: t('pages.atRiskCustomers.methodPhone'),
    EMAIL: t('pages.atRiskCustomers.methodEmail'),
    MEETING: t('pages.atRiskCustomers.methodMeeting'),
    OTHER: t('pages.atRiskCustomers.methodOther'),
  }
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
      message.success(t('pages.atRiskCustomers.msgFollowSaved'))
      setFollowOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.atRiskCustomers.msgFollowFailed')))
    } finally {
      setSaving(false)
    }
  }

  const columns: ProColumns<CustomerHealthBrief>[] = [
    { title: t('pages.atRiskCustomers.colName'), dataIndex: 'name', render: (_, row) => <a onClick={() => navigate(`/customers/${row.id}`)}>{row.name}</a> },
    { title: t('pages.atRiskCustomers.colCompany'), dataIndex: 'company', search: false },
    {
      title: t('pages.atRiskCustomers.colHealthScore'),
      dataIndex: 'healthScore',
      search: false,
      width: 100,
      render: (_, row) => {
        const score = row.healthScore
        const color = score < 60 ? 'red' : score < 80 ? 'gold' : 'green'
        return <Tag color={color}>{score}</Tag>
      },
    },
    { title: t('pages.atRiskCustomers.colDaysInactive'), dataIndex: 'daysInactive', search: false, width: 110 },
    {
      title: t('pages.atRiskCustomers.colLastFollowUp'),
      dataIndex: 'lastFollowUpAt',
      search: false,
      render: (_, row) => (row.lastFollowUpAt ? row.lastFollowUpAt.replace('T', ' ').slice(0, 16) : '-'),
    },
    { title: t('pages.atRiskCustomers.colOwner'), dataIndex: 'ownerName', search: false, render: (_, row) => row.ownerName ?? '-' },
    {
      title: t('pages.atRiskCustomers.colAction'),
      valueType: 'option',
      width: 120,
      render: (_, row) => [
        <a key="follow" onClick={() => openFollow(row)}>
          <PlusOutlined /> {t('pages.atRiskCustomers.btnFollow')}
        </a>,
      ],
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Title level={4} style={{ marginBottom: 4 }}>
          {t('pages.atRiskCustomers.title')}
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          {t('pages.atRiskCustomers.description')}
        </Paragraph>
      </div>

      <ProTable<CustomerHealthBrief>
        size="small"
        headerTitle={t('pages.atRiskCustomers.listTitle')}
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
        title={current ? t('pages.atRiskCustomers.followUpTitle', { name: current.name }) : t('pages.atRiskCustomers.btnFollow')}
        open={followOpen}
        onOk={() => void onFollow()}
        onCancel={() => setFollowOpen(false)}
        okText={t('pages.atRiskCustomers.btnSave')}
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} name="atRiskFollowUp" layout="vertical">
          <Form.Item name="method" label={t('pages.atRiskCustomers.followUpMethod')} rules={[{ required: true, message: t('pages.atRiskCustomers.followUpMethodRequired') }]}>
            <Select
              placeholder={t('pages.atRiskCustomers.followUpMethodPlaceholder')}
              options={Object.entries(METHOD_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="content" label={t('pages.atRiskCustomers.followUpContent')} rules={[{ required: true, message: t('pages.atRiskCustomers.followUpContentRequired') }]}>
            <Input.TextArea rows={3} placeholder={t('pages.atRiskCustomers.followUpContentPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
