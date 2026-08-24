import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Tag,
} from 'antd'
import type { Dayjs } from 'dayjs'
import { PlusOutlined } from '@ant-design/icons'
import {
  createContract,
  fetchContractTemplates,
  fetchContracts,
  type ContractPayload,
} from '../../services/contractService'
import { fetchCustomers } from '../../services/customerService'
import { fetchQuotes } from '../../services/quoteService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  CONTRACT_STATUS_COLORS,
  CONTRACT_STATUS_LABELS,
  type Contract,
} from '../../types/contract'

interface FormValues {
  title: string
  customerId: number
  quoteId?: number
  amount?: number
  startDate?: Dayjs
  endDate?: Dayjs
  templateId?: number
  /** 续约来源合同（046）。 */
  renewedFromId?: number
  remark?: string
}

export default function ContractListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<FormValues>()
  const [customerOptions, setCustomerOptions] = useState<{ value: number; label: string }[]>([])
  const [quoteOptions, setQuoteOptions] = useState<{ value: number; label: string }[]>([])
  const [templateOptions, setTemplateOptions] = useState<{ value: number; label: string }[]>([])
  const [renewalOptions, setRenewalOptions] = useState<{ value: number; label: string }[]>([])

  const reload = () => actionRef.current?.reload()

  const loadCustomers = async (keyword?: string) => {
    const res = await fetchCustomers({ keyword, page: 1, pageSize: 50 })
    setCustomerOptions(res.items.map((c) => ({ value: c.id, label: c.company || c.name })))
  }

  const loadQuotes = async (keyword?: string) => {
    const res = await fetchQuotes({ keyword, status: 'APPROVED', page: 1, pageSize: 50 })
    setQuoteOptions(
      res.items.map((q) => ({
        value: q.id,
        label: `${q.quoteNo}（${q.customerName ?? '-'}，¥${(q.totalAmount / 100).toLocaleString('zh-CN')}）`,
      })),
    )
  }

  const loadTemplates = async () => {
    const res = await fetchContractTemplates({ status: 'ACTIVE', page: 1, pageSize: 100 })
    setTemplateOptions(res.items.map((t) => ({ value: t.id, label: t.name })))
  }

  const loadRenewalSources = async () => {
    const res = await fetchContracts({ status: 'EFFECTIVE', page: 1, pageSize: 100 })
    setRenewalOptions(
      res.items.map((c) => ({ value: c.id, label: `${c.contractNo}（${c.title ?? '-'}）` })),
    )
  }

  const openCreate = () => {
    form.resetFields()
    void loadCustomers()
    void loadQuotes()
    void loadTemplates()
    void loadRenewalSources()
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: ContractPayload = {
      title: values.title.trim(),
      customerId: values.customerId,
      quoteId: values.quoteId,
      amount: values.amount === undefined ? undefined : Math.round(values.amount * 100),
      startDate: values.startDate ? values.startDate.format('YYYY-MM-DD') : undefined,
      endDate: values.endDate ? values.endDate.format('YYYY-MM-DD') : undefined,
      templateId: values.templateId,
      renewedFromId: values.renewedFromId,
      remark: values.remark,
    }
    setSaving(true)
    try {
      await createContract(payload)
      message.success('已创建（草稿）')
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '创建失败'))
    } finally {
      setSaving(false)
    }
  }

  const columns: ProColumns<Contract>[] = [
    {
      title: '合同编号',
      dataIndex: 'contractNo',
      render: (_, row) => <Link to={`/contracts/${row.id}`}>{row.contractNo}</Link>,
    },
    { title: '标题', dataIndex: 'title' },
    {
      title: '客户',
      dataIndex: 'customerName',
      render: (_, row) =>
        row.customerId ? <Link to={`/customers/${row.customerId}`}>{row.customerName ?? '-'}</Link> : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(CONTRACT_STATUS_LABELS).map(([k, v]) => [k, { text: v }]),
      ),
      render: (_, row) => (
        <Tag color={CONTRACT_STATUS_COLORS[row.status]}>{CONTRACT_STATUS_LABELS[row.status]}</Tag>
      ),
    },
    {
      title: '金额（元）',
      dataIndex: 'amount',
      search: false,
      render: (_, row) => (row.amount / 100).toLocaleString('zh-CN'),
    },
    {
      title: '生效日期',
      dataIndex: 'startDate',
      search: false,
      render: (_, row) => row.startDate ?? '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      search: false,
      render: (_, row) => (row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 16) : '-'),
    },
  ]

  return (
    <>
      <ProTable<Contract>
        size="small"
        headerTitle="合同管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchContracts({
            keyword: params.keyword,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建合同
          </Button>,
        ]}
      />

      <Modal
        title="新建合同"
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存草稿"
        destroyOnClose
        width={720}
      >
        <Form
          form={form}
          name="contractForm"
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="title" label="合同标题" rules={[{ required: true, message: '请输入合同标题' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="customerId" label="客户" rules={[{ required: true, message: '请选择客户' }]}>
                <Select
                  showSearch
                  placeholder="搜索并选择客户"
                  options={customerOptions}
                  filterOption={false}
                  onSearch={(kw) => void loadCustomers(kw)}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="quoteId" label="关联报价（已通过）">
                <Select
                  showSearch
                  allowClear
                  placeholder="可选，选择后自动带入金额"
                  options={quoteOptions}
                  filterOption={false}
                  onSearch={(kw) => void loadQuotes(kw)}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="amount" label="合同金额（元）">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="选择报价后自动带入" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="templateId" label="合同模板">
                <Select allowClear placeholder="可选，选择后生成正文" options={templateOptions} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="startDate" label="生效日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="endDate" label="结束日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="renewedFromId" label="续约自（可选）">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择被续约的生效合同"
                  options={renewalOptions}
                />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label="备注">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
