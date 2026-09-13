import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Upload,
} from 'antd'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlusOutlined,
  SwapOutlined,
  UploadOutlined,
  UserAddOutlined,
} from '@ant-design/icons'
import {
  batchTransferCustomers,
  claimCustomer,
  createCustomer,
  deleteCustomer,
  downloadTemplate,
  exportCustomers,
  fetchCustomers,
  fetchMyCustomers,
  fetchPoolCustomers,
  importCustomers,
  scanPool,
  updateCustomer,
  type CustomerPayload,
  type ImportResult,
} from '../../services/customerService'
import { fetchUsers } from '../../services/userService'
import { fetchCampaigns } from '../../services/marketingService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { FormGrid, FormModal, useFormMetrics } from '../../components/ui'
import { extractCfParams, useCustomFieldFilterColumns } from '../../hooks/useCustomFieldFilters'
import type { Customer } from '../../types/customer'
import { fromCustomFieldValues, toCustomFieldPayload } from '../../utils/customField'
import { CustomFieldFormItems } from '../../components/CustomFieldItems'

interface FormValues {
  name: string
  company: string
  contactPerson?: string
  phone?: string
  email?: string
  address?: string
  campaignId?: number
  customFieldValues?: Record<string, string | number | undefined>
  remark?: string
}

type ViewMode = 'all' | 'mine' | 'pool'

export default function CustomerListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Customer | null>(null)
  const [form] = Form.useForm<FormValues>()
  const [view, setView] = useState<ViewMode>('all')
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([])
  const [transferOpen, setTransferOpen] = useState(false)
  const [userOptions, setUserOptions] = useState<{ value: number; label: string }[]>([])
  const [campaignOptions, setCampaignOptions] = useState<{ value: number; label: string }[]>([])
  const [transferForm] = Form.useForm<{ targetOwnerId: number }>()
  const [toolbarBusy, setToolbarBusy] = useState(false)
  // 标签宽度的唯一来源（中文 96 / 英文 112），取代此前写死的 '100px'
  // ——那是全库 5 种 labelCol 定宽里**出现次数最多**的一个（11 处）。
  const metrics = useFormMetrics()
  // 028：操作权限（ADMIN 由 hasPerm 短路放行，不需要再 || isAdmin）
  const can = usePerms([
    PERMS.customerCreate,
    PERMS.customerUpdate,
    PERMS.customerDelete,
    PERMS.customerImport,
    PERMS.customerClaim,
    PERMS.customerPoolManage,
  ])

  useEffect(() => {
    void fetchCampaigns({ page: 1, pageSize: 100 }).then((res) =>
      setCampaignOptions(res.items.map((c) => ({ value: c.id, label: c.name }))),
    )
  }, [])

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: Customer) => {
    setEditing(row)
    form.setFieldsValue({
      name: row.name,
      company: row.company,
      contactPerson: row.contactPerson,
      phone: row.phone,
      email: row.email,
      address: row.address,
      campaignId: row.campaignId,
      remark: row.remark,
    })
    const cf = fromCustomFieldValues(row.customFieldValues)
    if (cf) form.setFieldsValue({ customFieldValues: cf })
    setModalOpen(true)
  }

  const onSave = async () => {
    // 校验失败的 rejection 就地吃掉（同 T031/T033/T035）：antd 已把错误显示在字段下方，
    // 再弹一条 message 只会重复；而放它逃出去会让 FormModal 的 handleOk 产生一个无人接管的
    // promise rejection（FormModal **刻意不吞异常**，见其文件头）。原先的
    // `onOk={() => void onSave()}` 是裸调用，只是那时没人注意到这个 rejection。
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
    const payload: CustomerPayload = { name: values.name, company: values.company }
    for (const [key, value] of Object.entries(values)) {
      if (key !== 'name' && key !== 'company' && key !== 'customFieldValues' && value !== undefined && value !== '') {
        payload[key as keyof CustomerPayload] = value as never
      }
    }
    const cf = toCustomFieldPayload(values.customFieldValues as Record<string, unknown>)
    if (cf) payload.customFieldValues = cf
    try {
      if (editing) {
        await updateCustomer(editing.id, { ...payload, version: editing.version })
        message.success(t('common.message.saved'))
      } else {
        await createCustomer(payload)
        message.success(t('common.message.success'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const onDelete = async (row: Customer) => {
    try {
      await deleteCustomer(row.id)
      message.success(t('common.message.deleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const onClaim = async (row: Customer) => {
    try {
      await claimCustomer(row.id)
      message.success(t('common.message.claimed'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const onScan = async () => {
    setToolbarBusy(true)
    try {
      const result = await scanPool()
      message.success(t('pages.customer.list.msgScanned', { count: result.returnedCount }))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setToolbarBusy(false)
    }
  }

  const openTransfer = async () => {
    if (selectedKeys.length === 0) {
      message.warning(t('pages.customer.list.msgSelectFirst'))
      return
    }
    const users = await fetchUsers({ page: 1, pageSize: 100 })
    setUserOptions(
      users.items
        // 负责人候选排除整个客服族（SUPPORT / SUPPORT_MANAGER / SUPPORT_AGENT）。改造前写的是
        // `u.role !== 'SUPPORT'`，只认那一个字面量角色名——081 之后客服改成 SUPPORT_MANAGER /
        // SUPPORT_AGENT，这个判断就再也筛不掉客服了。按前缀匹配才匹配得上「客服不做客户负责人」的本意。
        // 后端没有对应规则（CustomerService / CustomerShareService 都不按角色筛负责人），
        // 所以这只是下拉框的**建议性过滤**，不是强制——接口本身不拦。
        .filter((u) => !u.role.startsWith('SUPPORT'))
        .map((u) => ({ value: u.id, label: u.displayName || u.username })),
    )
    transferForm.resetFields()
    setTransferOpen(true)
  }

  const onTransfer = async () => {
    const values = await transferForm.validateFields()
    setToolbarBusy(true)
    try {
      const count = await batchTransferCustomers(selectedKeys as number[], values.targetOwnerId)
      message.success(t('pages.customer.list.msgTransferred', { count }))
      setTransferOpen(false)
      setSelectedKeys([])
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setToolbarBusy(false)
    }
  }

  const onImport = async (file: File) => {
    setToolbarBusy(true)
    try {
      const result: ImportResult = await importCustomers(file)
      message.success(
        t('pages.customer.list.msgImported', { success: result.successCount, fail: result.failureCount }),
      )
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setToolbarBusy(false)
    }
    return false
  }

  const customFieldFilterColumns = useCustomFieldFilterColumns('CUSTOMER')

  const viewColumns: ProColumns<Customer>[] = [
    {
      title: t('pages.customer.list.colName'),
      dataIndex: 'name',
      render: (_, row) => <Link to={`/customers/${row.id}`}>{row.name}</Link>,
    },
    { title: t('pages.customer.list.colCompany'), dataIndex: 'company' },
    { title: t('pages.customer.list.colContact'), dataIndex: 'contactPerson', search: false },
    { title: t('pages.customer.list.colPhone'), dataIndex: 'phone', search: false },
    { title: t('pages.customer.list.colEmail'), dataIndex: 'email', search: false },
    {
      title: t('pages.customer.list.colStatus'),
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: {
        ACTIVE: { text: t('common.status.active'), status: 'Success' },
        INACTIVE: { text: t('common.status.inactive'), status: 'Default' },
      },
    },
    {
      title: t('pages.customer.list.colOwner'),
      dataIndex: 'ownerName',
      search: false,
      render: (_, row) => row.ownerName ?? <span style={{ color: '#fa8c16' }}>{t('pages.customer.list.pool')}</span>,
    },
    {
      title: t('pages.customer.list.colAction'),
      valueType: 'option',
      width: 180,
      render: (_, row) => [
        view === 'pool' ? (
          can[PERMS.customerClaim] ? (
            <a key="claim" onClick={() => onClaim(row)}>
              <UserAddOutlined /> {t('pages.customer.list.claim')}
            </a>
          ) : null
        ) : can[PERMS.customerUpdate] ? (
          <a key="edit" onClick={() => openEdit(row)}>
            <EditOutlined /> {t('pages.customer.list.edit')}
          </a>
        ) : null,
        can[PERMS.customerDelete] ? (
          <Popconfirm
            key="delete"
            title={t('pages.customer.list.deleteConfirm', { name: row.name })}
            onConfirm={() => onDelete(row)}
          >
            <a style={{ color: '#ff4d4f' }}>
              <DeleteOutlined /> {t('pages.customer.list.delete')}
            </a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  const fetchByView = async (params: {
    keyword?: string
    status?: string
    current?: number
    pageSize?: number
    [k: string]: unknown
  }) => {
    const common = {
      keyword: params.keyword,
      status: params.status,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
      ...extractCfParams(params),
    }
    const res =
      view === 'pool'
        ? await fetchPoolCustomers(common)
        : view === 'mine'
          ? await fetchMyCustomers(common)
          : await fetchCustomers(common)
    return { data: res.items, success: true, total: res.total }
  }

  return (
    <div className="page-stack">
      <Space>
        <Button type={view === 'all' ? 'primary' : 'default'} onClick={() => { setView('all'); reload() }}>
          {t('pages.customer.list.viewAll')}
        </Button>
        <Button type={view === 'mine' ? 'primary' : 'default'} onClick={() => { setView('mine'); reload() }}>
          {t('pages.customer.list.viewMine')}
        </Button>
        <Button type={view === 'pool' ? 'primary' : 'default'} onClick={() => { setView('pool'); reload() }}>
          {t('pages.customer.list.viewPool')}
        </Button>
      </Space>

      <ProTable<Customer>
        size="small"
        headerTitle={t('pages.customer.list.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={[...viewColumns, ...customFieldFilterColumns]}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        rowSelection={
          // 行选择只服务于「批量转移」，而 POST /customers/batch-transfer 挂的是 customer:pool_manage
          // （1.5 批 3 起，此前是 hasRole('ADMIN')）——按码判断，不再按角色名。
          // 该码目前无人被授，所以效果与改造前一致：只有管理员能选中行、能点「转移」。
          can[PERMS.customerPoolManage] && view !== 'pool'
            ? { selectedRowKeys: selectedKeys, onChange: setSelectedKeys }
            : undefined
        }
        request={fetchByView}
        toolBarRender={() => [
          ...(can[PERMS.customerImport]
            ? [
                <Upload key="import" showUploadList={false} beforeUpload={(f) => onImport(f as unknown as File)} accept=".xlsx">
                  <Button icon={<UploadOutlined />} loading={toolbarBusy}>{t('pages.customer.list.import')}</Button>
                </Upload>,
                <Button key="template" icon={<DownloadOutlined />} disabled={toolbarBusy} onClick={() => void downloadTemplate()}>
                  {t('pages.customer.list.downloadTemplate')}
                </Button>,
              ]
            : []),
          <Button key="export" icon={<DownloadOutlined />} loading={toolbarBusy} onClick={() => void exportCustomers({})}>
            {t('pages.customer.list.export')}
          </Button>,
          ...(can[PERMS.customerPoolManage]
            ? [
                // POST /customers/pool/scan 挂的是 customer:pool_manage（1.5 批 3 起，此前是
                // hasRole('ADMIN')）——同样按码判断。
                <Button key="scan" loading={toolbarBusy} onClick={() => void onScan()}>
                  {t('pages.customer.list.poolScan')}
                </Button>,
              ]
            : []),
          ...(can[PERMS.customerPoolManage]
            ? [
                // 与行选择同码（POST /customers/batch-transfer 挂的就是 customer:pool_manage）。
                // 此前这里按 customer:transfer 判断——那个码被授给了 SALES / SALES_MANAGER，
                // 但后端没有任何端点校验它，于是这些角色看到一个永远 disabled 的「转移」按钮。
                <Button
                  key="transfer"
                  icon={<SwapOutlined />}
                  disabled={selectedKeys.length === 0 || toolbarBusy}
                  onClick={() => void openTransfer()}
                >
                  {t('pages.customer.list.transfer')}
                </Button>,
              ]
            : []),
          ...(can[PERMS.customerCreate]
            ? [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  {t('pages.customer.list.create')}
                </Button>,
              ]
            : []),
        ]}
      />

      <FormModal
        size="md"
        title={editing ? t('pages.customer.list.editModal') : t('pages.customer.list.createModal')}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        onSubmit={onSave}
      >
        <Form
          form={form}
          name="customerForm"
          layout="horizontal"
          labelCol={{ flex: `${metrics.labelWidth}px` }}
          wrapperCol={{ flex: 1 }}
        >
          {/* 7 个成对字段进栅格。原先的 `<Row gutter={16}>` + 7 个 `<Col span={12}>`
              是**写死两列、无任何断点**的（R3 的 96 处之一）：375px 视口下 antd 把弹窗夹到
              `calc(100vw - 32px)` = 343px，扣掉 24×2 内边距只剩约 295px，`span={12}` 仍要排两列
              ⇒ 每列约 140px（低于 MIN_FIELD_WIDTH=160），是 088 要修的那类真缺陷。
              `FormGrid` 的 auto-fit 在 md 档 640px 里算出的可用宽 592 ≥ 2 × 256 ⇒ 同样是两列，
              只在容器窄到装不下两个字段时自动退成一列。 */}
          <FormGrid>
            <Form.Item name="name" label={t('pages.customer.list.formName')} rules={[{ required: true, message: t('pages.customer.list.msgNameRequired') }]}>
              <Input />
            </Form.Item>
            <Form.Item name="company" label={t('pages.customer.list.formCompany')} rules={[{ required: true, message: t('pages.customer.list.msgCompanyRequired') }]}>
              <Input />
            </Form.Item>
            <Form.Item name="contactPerson" label={t('pages.customer.list.formContact')}>
              <Input />
            </Form.Item>
            <Form.Item name="phone" label={t('pages.customer.list.formPhone')}>
              <Input />
            </Form.Item>
            <Form.Item name="email" label={t('pages.customer.list.formEmail')}>
              <Input />
            </Form.Item>
            <Form.Item name="address" label={t('pages.customer.list.formAddress')}>
              <Input />
            </Form.Item>
            <Form.Item name="campaignId" label={t('pages.customer.list.formCampaign')}>
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder={t('pages.customer.list.formCampaignPlaceholder')}
                options={campaignOptions}
              />
            </Form.Item>
          </FormGrid>
          {/* 以下两项**刻意留在栅格之外**（`FormGrid` 的使用纪律第 1 条）：自定义字段的数量与
              宽度都由后端配置决定、备注是长文本，两者都是**全宽项**；放进栅格会悄悄退化成
              N 列里的一列。**同一表单内也不再混用 `<Col>`**（纪律第 2 条）。 */}
          <CustomFieldFormItems entityType="CUSTOMER" />
          <Form.Item name="remark" label={t('pages.customer.list.formRemark')}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </FormModal>

      {/* 第二个弹窗（纵向、单字段）。同样换成 `FormModal`：原写法既没设宽度（吃 antd 默认 520，
          是 R2 的 23 处之一）、也没有 `confirmLoading`——提交期间 OK 按钮可重复点，
          而 `batchTransferCustomers` 不带幂等键。改为 `sm`(480) 是一处**可归因的视觉变化**（−40px），
          与 T031 的作废弹窗同一取档理由（单字段纵向表单，432px 可用宽足够）。 */}
      <FormModal
        size="sm"
        title={t('pages.customer.list.transferModal', { count: selectedKeys.length })}
        open={transferOpen}
        onCancel={() => setTransferOpen(false)}
        okText={t('pages.customer.list.transferOk')}
        onSubmit={onTransfer}
      >
        <Form form={transferForm} name="transferForm" layout="vertical">
          <Form.Item
            name="targetOwnerId"
            label={t('pages.customer.list.transferTarget')}
            rules={[{ required: true, message: t('pages.customer.list.msgTargetRequired') }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder={t('pages.customer.list.transferPlaceholder')}
              options={userOptions}
            />
          </Form.Item>
        </Form>
      </FormModal>
    </div>
  )
}
