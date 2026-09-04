import { useRef, useState } from 'react'
import { App, Button, Col, DatePicker, Form, InputNumber, Modal, Popconfirm, Row, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  closeSalesOpportunity,
  createSalesOpportunity,
  fetchOpportunities,
  fetchSalesOpportunities,
  type SalesOpportunityPayload,
} from '../../services/opportunityService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  ACTIVE_STAGES,
  formatAmount,
  STAGE_LABELS,
  type OpportunityStage,
  type SalesOpportunity,
} from '../../types/opportunity'
import { useQuery } from '@tanstack/react-query'
import type { Dayjs } from 'dayjs'

interface FormValues {
  opportunityId: number
  amount?: number
  stage: OpportunityStage
  expectedCloseDate?: Dayjs
}

export default function SalesOpportunityListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<FormValues>()

  const opportunities = useQuery({
    queryKey: ['opportunities-options'],
    queryFn: () => fetchOpportunities({ page: 1, pageSize: 100 }),
  })

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    form.resetFields()
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: SalesOpportunityPayload = {
      opportunityId: values.opportunityId,
      amount: values.amount ? values.amount * 100 : undefined,
      stage: values.stage,
      expectedCloseDate: values.expectedCloseDate?.format('YYYY-MM-DD'),
    }
    setSaving(true)
    try {
      await createSalesOpportunity(payload)
      message.success('已创建')
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '创建失败'))
    } finally {
      setSaving(false)
    }
  }

  const onClose = async (row: SalesOpportunity, result: 'WON' | 'LOST') => {
    try {
      await closeSalesOpportunity(row.id, result, row.version)
      message.success(`已标记为${result === 'WON' ? '赢单' : '输单'}`)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '关闭失败'))
    }
  }

  const stageStatus: Record<OpportunityStage, 'success' | 'error' | 'processing' | 'default'> = {
    INITIAL_CONTACT: 'processing',
    NEGOTIATING: 'processing',
    CLOSED_WON: 'success',
    CLOSED_LOST: 'error',
  }

  const columns: ProColumns<SalesOpportunity>[] = [
    { title: '所属商机', dataIndex: 'opportunityName', search: false },
    { title: '关联客户', dataIndex: 'customerName', search: false },
    { title: '金额（元）', dataIndex: 'amount', search: false, render: (_, row) => formatAmount(row.amount) },
    {
      title: '阶段',
      dataIndex: 'stage',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(STAGE_LABELS).map(([value, text]) => [value, { text }]),
      ),
      render: (_, row) => <Tag color={stageStatus[row.stage]}>{STAGE_LABELS[row.stage]}</Tag>,
    },
    { title: '预计成交', dataIndex: 'expectedCloseDate', search: false, render: (_, row) => row.expectedCloseDate ?? '-' },
    {
      title: '操作',
      valueType: 'option',
      width: 150,
      render: (_, row) =>
        ACTIVE_STAGES.includes(row.stage)
          ? [
              <Popconfirm
                key="won"
                title="确定将该机会关闭为赢单吗？"
                onConfirm={() => onClose(row, 'WON')}
              >
                <a style={{ color: '#52c41a' }}>赢单</a>
              </Popconfirm>,
              <Popconfirm
                key="lost"
                title="确定将该机会关闭为输单吗？"
                onConfirm={() => onClose(row, 'LOST')}
              >
                <a style={{ color: '#ff4d4f' }}>输单</a>
              </Popconfirm>,
            ]
          : [<span key="closed" style={{ color: '#999' }}>已关闭</span>],
    },
  ]

  return (
    <>
      <ProTable<SalesOpportunity>
        size="small"
        headerTitle="销售机会管道"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchSalesOpportunities({
            stage: params.stage,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增销售机会
          </Button>,
        ]}
      />
      <Modal title="新增销售机会" open={modalOpen} onOk={() => void onSave()} onCancel={() => setModalOpen(false)} okText="创建" confirmLoading={saving} destroyOnClose width={640}>
        <Form
          form={form}
          name="salesOpportunityForm"
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="opportunityId"
                label="所属商机"
                rules={[{ required: true, message: '请选择商机' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={(opportunities.data?.items ?? []).map((o) => ({
                    value: o.id,
                    label: `${o.name}（${o.customerName ?? o.customerId}）`,
                  }))}
                  placeholder="请选择商机"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="amount" label="金额（元）">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="stage"
                label="阶段"
                rules={[{ required: true, message: '请选择阶段' }]}
              >
                <Select
                  options={ACTIVE_STAGES.map((s) => ({ value: s, label: STAGE_LABELS[s] }))}
                  placeholder="请选择阶段"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="expectedCloseDate" label="预计成交日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
