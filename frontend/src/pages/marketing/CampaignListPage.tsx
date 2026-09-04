import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Col, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  createCampaign,
  deleteCampaign,
  endCampaign,
  fetchCampaigns,
  startCampaign,
  updateCampaign,
  type CampaignPayload,
} from '../../services/marketingService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  CAMPAIGN_CHANNEL_LABELS,
  CAMPAIGN_STATUS_COLORS,
  CAMPAIGN_STATUS_LABELS,
  type CampaignChannel,
  type CampaignStatus,
  type MarketingCampaign,
} from '../../types/marketing'

interface FormValues {
  name: string
  channel: CampaignChannel
  budget?: number
  cost?: number
  startDate?: dayjs.Dayjs
  endDate?: dayjs.Dayjs
}

export default function CampaignListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<MarketingCampaign | null>(null)
  const [form] = Form.useForm<FormValues>()

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: MarketingCampaign) => {
    setEditing(row)
    form.setFieldsValue({
      name: row.name,
      channel: row.channel,
      budget: row.budget,
      cost: row.cost,
      startDate: row.startDate ? dayjs(row.startDate) : undefined,
      endDate: row.endDate ? dayjs(row.endDate) : undefined,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: CampaignPayload = {
      name: values.name.trim(),
      channel: values.channel,
      budget: values.budget,
      cost: values.cost,
      startDate: values.startDate ? values.startDate.format('YYYY-MM-DD') : undefined,
      endDate: values.endDate ? values.endDate.format('YYYY-MM-DD') : undefined,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateCampaign(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createCampaign(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const onStart = async (row: MarketingCampaign) => {
    try {
      await startCampaign(row.id)
      message.success('活动已开始')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const onEnd = async (row: MarketingCampaign) => {
    try {
      await endCampaign(row.id)
      message.success('活动已结束')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const onDelete = async (row: MarketingCampaign) => {
    try {
      await deleteCampaign(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<MarketingCampaign>[] = [
    { title: '活动名称', dataIndex: 'name' },
    {
      title: '渠道',
      dataIndex: 'channel',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(CAMPAIGN_CHANNEL_LABELS).map(([k, v]) => [k, { text: v }]),
      ),
      render: (_, row) => <Tag color="blue">{CAMPAIGN_CHANNEL_LABELS[row.channel]}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(CAMPAIGN_STATUS_LABELS).map(([k, v]) => [
          k,
          { text: v, status: CAMPAIGN_STATUS_COLORS[k as CampaignStatus] },
        ]),
      ),
    },
    { title: '预算', dataIndex: 'budget', search: false },
    { title: '成本', dataIndex: 'cost', search: false },
    {
      title: '归因线索',
      dataIndex: 'leadCount',
      search: false,
      render: (_, row) => row.leadCount ?? 0,
    },
    {
      title: '归因客户',
      dataIndex: 'customerCount',
      search: false,
      render: (_, row) => row.customerCount ?? 0,
    },
    { title: '开始日期', dataIndex: 'startDate', search: false, valueType: 'date' },
    { title: '结束日期', dataIndex: 'endDate', search: false, valueType: 'date' },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        row.status === 'PLANNING' && (
          <a key="start" onClick={() => onStart(row)}>
            开始
          </a>
        ),
        row.status === 'RUNNING' && (
          <a key="end" onClick={() => onEnd(row)}>
            结束
          </a>
        ),
        row.status !== 'ENDED' && (
          <a key="edit" onClick={() => openEdit(row)}>
            编辑
          </a>
        ),
        <Popconfirm
          key="delete"
          title={`确定删除活动「${row.name}」吗？有归因数据时将被拒绝。`}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<MarketingCampaign>
        size="small"
        headerTitle="营销活动"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchCampaigns({
            keyword: params.keyword,
            channel: params.channel,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Link key="roi" to="/marketing/roi">
            <Button>渠道 ROI</Button>
          </Link>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增活动
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑活动' : '新增活动'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={640}
      >
        <Form
          form={form}
          name="campaignForm"
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="活动名称" rules={[{ required: true, message: '请输入活动名称' }]}>
                <Input maxLength={100} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="channel" label="渠道" rules={[{ required: true, message: '请选择渠道' }]}>
                <Select options={Object.entries(CAMPAIGN_CHANNEL_LABELS).map(([value, label]) => ({ value, label }))} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="budget" label="预算">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="cost" label="成本">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="startDate" label="开始日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="endDate" label="结束日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
