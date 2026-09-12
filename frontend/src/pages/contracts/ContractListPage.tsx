import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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
  type Contract,
} from '../../types/contract'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'

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
  const { t } = useTranslation()
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
      message.success(t('pages.contract.list.msgCreated'))
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const columns: ProColumns<Contract>[] = [
    {
      title: t('pages.contract.list.colNo'),
      dataIndex: 'contractNo',
      render: (_, row) => <Link to={`/contracts/${row.id}`}>{row.contractNo}</Link>,
    },
    { title: t('pages.contract.list.colTitle'), dataIndex: 'title' },
    {
      title: t('pages.contract.list.colCustomer'),
      dataIndex: 'customerName',
      render: (_, row) =>
        row.customerId ? <Link to={`/customers/${row.customerId}`}>{row.customerName ?? '-'}</Link> : '-',
    },
    {
      title: t('pages.contract.list.colStatus'),
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.contractStatus).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.contractStatus, code) },
        ]),
      ),
      render: (_, row) => (
        <Tag color={CONTRACT_STATUS_COLORS[row.status]}>
          {labelOf(t, ENUM_KEYS.contractStatus, row.status)}
        </Tag>
      ),
    },
    {
      title: t('pages.contract.list.colAmount'),
      dataIndex: 'amount',
      search: false,
      render: (_, row) => (row.amount / 100).toLocaleString('zh-CN'),
    },
    {
      title: t('pages.contract.list.colEffective'),
      dataIndex: 'startDate',
      search: false,
      render: (_, row) => row.startDate ?? '-',
    },
    {
      title: t('pages.contract.list.colCreated'),
      dataIndex: 'createdAt',
      search: false,
      render: (_, row) => (row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 16) : '-'),
    },
  ]

  return (
    <>
      <ProTable<Contract>
        size="small"
        headerTitle={t('pages.contract.list.title')}
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
            {t('pages.contract.list.create')}
          </Button>,
        ]}
      />

      <Modal
        title={t('pages.contract.list.createModal')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.contract.list.saveDraft')}
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
              <Form.Item name="title" label={t('pages.contract.list.formTitle')} rules={[{ required: true, message: t('pages.contract.list.msgTitleRequired') }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="customerId" label={t('pages.contract.list.formCustomer')} rules={[{ required: true, message: t('pages.contract.list.msgCustomerRequired') }]}>
                <Select
                  showSearch
                  placeholder={t('pages.contract.list.phCustomer')}
                  options={customerOptions}
                  filterOption={false}
                  onSearch={(kw) => void loadCustomers(kw)}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="quoteId" label={t('pages.contract.list.formQuote')}>
                <Select
                  showSearch
                  allowClear
                  placeholder={t('pages.contract.list.phQuote')}
                  options={quoteOptions}
                  filterOption={false}
                  onSearch={(kw) => void loadQuotes(kw)}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="amount" label={t('pages.contract.list.formAmount')}>
                <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder={t('pages.contract.list.phAmount')} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="templateId" label={t('pages.contract.list.formTemplate')}>
                <Select allowClear placeholder={t('pages.contract.list.phTemplate')} options={templateOptions} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="startDate" label={t('pages.contract.list.formStart')}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="endDate" label={t('pages.contract.list.formEnd')}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="renewedFromId" label={t('pages.contract.list.formRenewedFrom')}>
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder={t('pages.contract.list.phRenewedFrom')}
                  options={renewalOptions}
                />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label={t('pages.contract.list.formRemark')}>
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
