import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ProTable,
  type ActionType,
  type ProColumns,
} from '@ant-design/pro-components'
import { App, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Tag } from 'antd'
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  UserAddOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import {
  assignLead,
  claimLead,
  createLead,
  deleteLead,
  fetchLeads,
  updateLead,
  type LeadPayload,
} from '../../services/leadService'
import { fetchCampaigns } from '../../services/marketingService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import {
  SOURCE_LABELS,
  STATUS_COLORS,
  STATUS_LABELS,
  type Lead,
  type LeadSource,
  type LeadStatus,
} from '../../types/lead'
import LeadConvertModal from '../../components/LeadConvertModal'

interface FormValues {
  name: string
  company: string
  title?: string
  phone?: string
  email?: string
  source?: LeadSource
  status?: LeadStatus
  score?: number
  campaignId?: number
  remark?: string
}

export default function LeadListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [convertOpen, setConvertOpen] = useState(false)
  const [convertLeadId, setConvertLeadId] = useState<number | undefined>()
  const [editing, setEditing] = useState<Lead | null>(null)
  const [activeTab, setActiveTab] = useState<'all' | 'pool'>('all')
  const [form] = Form.useForm<FormValues>()
  const [campaignOptions, setCampaignOptions] = useState<{ value: number; label: string }[]>([])
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

  const openEdit = (row: Lead) => {
    setEditing(row)
    form.setFieldsValue({
      name: row.name,
      company: row.company,
      title: row.title,
      phone: row.phone,
      email: row.email,
      source: row.source as LeadSource,
      status: row.status as LeadStatus,
      score: row.score,
      campaignId: row.campaignId,
      remark: row.remark,
    })
    setModalOpen(true)
  }

  const openConvert = (row: Lead) => {
    setConvertLeadId(row.id)
    setConvertOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: LeadPayload = {
      name: values.name,
      company: values.company,
      title: values.title,
      phone: values.phone,
      email: values.email,
      source: values.source,
      status: values.status,
      score: values.score,
      campaignId: values.campaignId,
      remark: values.remark,
    }
    try {
      if (editing) {
        await updateLead(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createLead(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: Lead) => {
    try {
      await deleteLead(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const onClaim = async (row: Lead) => {
    try {
      await claimLead(row.id)
      message.success('已领取')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '领取失败'))
    }
  }

  const onAssignToMe = async (row: Lead) => {
    if (!user?.id) return
    try {
      await assignLead(row.id, user.id)
      message.success('已分配')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '分配失败'))
    }
  }

  const canEdit = (row: Lead) => row.status !== 'QUALIFIED' && row.status !== 'DISQUALIFIED'
  const canConvert = (row: Lead) => row.status === 'NEW' || row.status === 'WORKING'

  const columns: ProColumns<Lead>[] = [
    {
      title: '姓名',
      dataIndex: 'name',
      render: (_, row) => <Link to={`/leads/${row.id}`}>{row.name}</Link>,
    },
    { title: '公司', dataIndex: 'company' },
    { title: '职位', dataIndex: 'title', search: false },
    { title: '电话', dataIndex: 'phone', search: false },
    { title: '邮箱', dataIndex: 'email', search: false },
    {
      title: '来源',
      dataIndex: 'source',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(SOURCE_LABELS).map(([k, v]) => [k, { text: v }]),
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(STATUS_LABELS).map(([k, v]) => [k, { text: v, status: STATUS_COLORS[k as LeadStatus] }]),
      ),
    },
    {
      title: '评分',
      dataIndex: 'score',
      search: false,
      render: (_, row) => <Tag color={row.score >= 70 ? 'green' : row.score >= 40 ? 'orange' : 'default'}>{row.score}</Tag>,
    },
    { title: '负责人', dataIndex: 'ownerName', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 240,
      render: (_, row) => [
        canEdit(row) && (
          <a key="edit" onClick={() => openEdit(row)}>
            <EditOutlined /> 编辑
          </a>
        ),
        row.ownerId == null && (
          <a key="claim" onClick={() => onClaim(row)}>
            <UserAddOutlined /> 领取
          </a>
        ),
        row.ownerId != null && row.ownerId !== user?.id && isAdmin && (
          <a key="assign" onClick={() => onAssignToMe(row)}>
            <SwapOutlined /> 分配给我
          </a>
        ),
        canConvert(row) && (
          <a key="convert" onClick={() => openConvert(row)}>
            <SwapOutlined /> 转化
          </a>
        ),
        canEdit(row) && (
          <Popconfirm key="delete" title={`确定删除线索「${row.name}」吗？`} onConfirm={() => onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>
              <DeleteOutlined /> 删除
            </a>
          </Popconfirm>
        ),
      ],
    },
  ]

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type={activeTab === 'all' ? 'primary' : 'default'} onClick={() => { setActiveTab('all'); reload() }}>
          全部线索
        </Button>
        <Button type={activeTab === 'pool' ? 'primary' : 'default'} onClick={() => { setActiveTab('pool'); reload() }}>
          线索池（未分配）
        </Button>
      </Space>

      <ProTable<Lead>
        headerTitle="线索管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchLeads({
            keyword: params.keyword,
            status: params.status,
            source: params.source,
            poolOnly: activeTab === 'pool',
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增线索
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑线索' : '新增线索'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={640}
      >
        <Form form={form} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="company" label="公司" rules={[{ required: true, message: '请输入公司' }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item name="title" label="职位" style={{ flex: 1 }}>
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
            <Form.Item name="source" label="来源" style={{ flex: 1 }}>
              <Select
                options={Object.entries(SOURCE_LABELS).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item name="status" label="状态" style={{ flex: 1 }}>
              <Select
                options={Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
            <Form.Item name="score" label="评分（0-100）" style={{ flex: 1 }}>
              <InputNumber min={0} max={100} style={{ width: '100%' }} />
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
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <LeadConvertModal
        open={convertOpen}
        leadId={convertLeadId}
        onCancel={() => setConvertOpen(false)}
        onSuccess={() => {
          setConvertOpen(false)
          reload()
        }}
      />
    </>
  )
}
