import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
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
import { useAuthStore } from '../../store/authStore'
import { hasPerm } from '../../hooks/usePermission'
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
  const [saving, setSaving] = useState(false)
  const [toolbarBusy, setToolbarBusy] = useState(false)
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  // 028：操作权限（ADMIN 恒真）
  const canCreate = hasPerm('customer:create', user)
  const canUpdate = hasPerm('customer:update', user)
  const canDelete = hasPerm('customer:delete', user)
  const canTransfer = hasPerm('customer:transfer', user)
  const canImport = hasPerm('customer:import', user)

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
    const values = await form.validateFields()
    const payload: CustomerPayload = { name: values.name, company: values.company }
    for (const [key, value] of Object.entries(values)) {
      if (key !== 'name' && key !== 'company' && key !== 'customFieldValues' && value !== undefined && value !== '') {
        payload[key as keyof CustomerPayload] = value as never
      }
    }
    const cf = toCustomFieldPayload(values.customFieldValues as Record<string, unknown>)
    if (cf) payload.customFieldValues = cf
    setSaving(true)
    try {
      if (editing) {
        await updateCustomer(editing.id, { ...payload, version: editing.version })
        message.success(t('message.saved'))
      } else {
        await createCustomer(payload)
        message.success(t('message.success'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Customer) => {
    try {
      await deleteCustomer(row.id)
      message.success(t('message.deleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const onClaim = async (row: Customer) => {
    try {
      await claimCustomer(row.id)
      message.success(t('message.claimed'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '领取失败'))
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
        .filter((u) => u.role !== 'SUPPORT')
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
          <a key="claim" onClick={() => onClaim(row)}>
            <UserAddOutlined /> {t('pages.customer.list.claim')}
          </a>
        ) : canUpdate ? (
          <a key="edit" onClick={() => openEdit(row)}>
            <EditOutlined /> {t('pages.customer.list.edit')}
          </a>
        ) : null,
        canDelete ? (
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
    <>
      <Space style={{ marginBottom: 16 }}>
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
          isAdmin && view !== 'pool'
            ? { selectedRowKeys: selectedKeys, onChange: setSelectedKeys }
            : undefined
        }
        request={fetchByView}
        toolBarRender={() => [
          ...(canImport
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
          ...(isAdmin
            ? [
                <Button key="scan" loading={toolbarBusy} onClick={() => void onScan()}>
                  {t('pages.customer.list.poolScan')}
                </Button>,
              ]
            : []),
          ...(canTransfer
            ? [
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
          ...(canCreate
            ? [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  {t('pages.customer.list.create')}
                </Button>,
              ]
            : []),
        ]}
      />

      <Modal
        title={editing ? t('pages.customer.list.editModal') : t('pages.customer.list.createModal')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        confirmLoading={saving}
        destroyOnClose
        width={640}
      >
        <Form
          form={form}
          name="customerForm"
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label={t('pages.customer.list.formName')} rules={[{ required: true, message: t('pages.customer.list.msgNameRequired') }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="company" label={t('pages.customer.list.formCompany')} rules={[{ required: true, message: t('pages.customer.list.msgCompanyRequired') }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="contactPerson" label={t('pages.customer.list.formContact')}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label={t('pages.customer.list.formPhone')}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="email" label={t('pages.customer.list.formEmail')}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="address" label={t('pages.customer.list.formAddress')}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="campaignId" label={t('pages.customer.list.formCampaign')}>
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder={t('pages.customer.list.formCampaignPlaceholder')}
                  options={campaignOptions}
                />
              </Form.Item>
            </Col>
          </Row>
          <CustomFieldFormItems entityType="CUSTOMER" />
          <Form.Item name="remark" label={t('pages.customer.list.formRemark')}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('pages.customer.list.transferModal', { count: selectedKeys.length })}
        open={transferOpen}
        onOk={() => void onTransfer()}
        onCancel={() => setTransferOpen(false)}
        okText={t('pages.customer.list.transferOk')}
        destroyOnClose
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
      </Modal>
    </>
  )
}
