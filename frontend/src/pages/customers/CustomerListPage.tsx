import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Form,
  Input,
  Modal,
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
import { useAuthStore } from '../../store/authStore'
import type { Customer } from '../../types/customer'
import {
  CustomFieldFormItems,
  fromCustomFieldValues,
  toCustomFieldPayload,
} from '../../components/CustomFieldItems'

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
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'

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
    try {
      if (editing) {
        await updateCustomer(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createCustomer(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: Customer) => {
    try {
      await deleteCustomer(row.id)
      message.success('已删除（逻辑删除）')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const onClaim = async (row: Customer) => {
    try {
      await claimCustomer(row.id)
      message.success('已领取')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '领取失败'))
    }
  }

  const onScan = async () => {
    try {
      const result = await scanPool()
      message.success(`扫描完成：${result.returnedCount} 个客户退回公海`)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '扫描失败'))
    }
  }

  const openTransfer = async () => {
    if (selectedKeys.length === 0) {
      message.warning('请先选择客户')
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
    try {
      const count = await batchTransferCustomers(selectedKeys as number[], values.targetOwnerId)
      message.success(`已转移 ${count} 个客户`)
      setTransferOpen(false)
      setSelectedKeys([])
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '转移失败'))
    }
  }

  const onImport = async (file: File) => {
    try {
      const result: ImportResult = await importCustomers(file)
      message.success(`导入完成：成功 ${result.successCount} 条，失败 ${result.failureCount} 条`)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '导入失败'))
    }
    return false
  }

  const viewColumns: ProColumns<Customer>[] = [
    {
      title: '客户名称',
      dataIndex: 'name',
      render: (_, row) => <Link to={`/customers/${row.id}`}>{row.name}</Link>,
    },
    { title: '公司', dataIndex: 'company' },
    { title: '联系人', dataIndex: 'contactPerson', search: false },
    { title: '电话', dataIndex: 'phone', search: false },
    { title: '邮箱', dataIndex: 'email', search: false },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: {
        ACTIVE: { text: '启用', status: 'Success' },
        INACTIVE: { text: '停用', status: 'Default' },
      },
    },
    {
      title: '归属',
      dataIndex: 'ownerName',
      search: false,
      render: (_, row) => row.ownerName ?? <span style={{ color: '#fa8c16' }}>公海</span>,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      render: (_, row) => [
        view === 'pool' ? (
          <a key="claim" onClick={() => onClaim(row)}>
            <UserAddOutlined /> 领取
          </a>
        ) : (
          <a key="edit" onClick={() => openEdit(row)}>
            <EditOutlined /> 编辑
          </a>
        ),
        <Popconfirm
          key="delete"
          title={`确定删除客户「${row.name}」吗？（逻辑删除，可恢复）`}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>
            <DeleteOutlined /> 删除
          </a>
        </Popconfirm>,
      ],
    },
  ]

  const fetchByView = async (params: { keyword?: string; status?: string; current?: number; pageSize?: number }) => {
    const common = {
      keyword: params.keyword,
      status: params.status,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
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
          全部客户
        </Button>
        <Button type={view === 'mine' ? 'primary' : 'default'} onClick={() => { setView('mine'); reload() }}>
          我的客户
        </Button>
        <Button type={view === 'pool' ? 'primary' : 'default'} onClick={() => { setView('pool'); reload() }}>
          公海客户
        </Button>
      </Space>

      <ProTable<Customer>
        headerTitle="客户管理"
        rowKey="id"
        actionRef={actionRef}
        columns={viewColumns}
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
          ...(isAdmin
            ? [
                <Upload key="import" showUploadList={false} beforeUpload={(f) => onImport(f as unknown as File)} accept=".xlsx">
                  <Button icon={<UploadOutlined />}>导入</Button>
                </Upload>,
                <Button key="template" icon={<DownloadOutlined />} onClick={() => void downloadTemplate()}>
                  下载模板
                </Button>,
                <Button key="export" icon={<DownloadOutlined />} onClick={() => void exportCustomers({})}>
                  导出
                </Button>,
                <Button key="scan" onClick={() => void onScan()}>
                  公海扫描
                </Button>,
                <Button
                  key="transfer"
                  icon={<SwapOutlined />}
                  disabled={selectedKeys.length === 0}
                  onClick={() => void openTransfer()}
                >
                  批量转移
                </Button>,
              ]
            : []),
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增客户
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑客户' : '新增客户'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
      >
        <Form form={form} name="customerForm" layout="vertical">
          <Form.Item name="name" label="客户名称" rules={[{ required: true, message: '请输入客户名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="company" label="公司" rules={[{ required: true, message: '请输入公司' }]}>
            <Input />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item name="contactPerson" label="联系人" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="phone" label="电话" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item name="email" label="邮箱" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="address" label="地址" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Form.Item name="campaignId" label="营销活动">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="选择来源活动（可选）"
              options={campaignOptions}
            />
          </Form.Item>
          <CustomFieldFormItems entityType="CUSTOMER" />
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`批量转移客户（已选 ${selectedKeys.length} 个）`}
        open={transferOpen}
        onOk={() => void onTransfer()}
        onCancel={() => setTransferOpen(false)}
        okText="转移"
        destroyOnClose
      >
        <Form form={transferForm} name="transferForm" layout="vertical">
          <Form.Item
            name="targetOwnerId"
            label="目标销售"
            rules={[{ required: true, message: '请选择目标销售' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择目标销售"
              options={userOptions}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
