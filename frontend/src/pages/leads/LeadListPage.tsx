import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ProTable,
  type ActionType,
  type ProColumns,
} from '@ant-design/pro-components'
import { App, Button, Col, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Table, Tag, Upload } from 'antd'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlusOutlined,
  UserAddOutlined,
  SwapOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import {
  assignLead,
  claimLead,
  createLead,
  deleteLead,
  downloadLeadTemplate,
  fetchLeads,
  importLeads,
  updateLead,
  type LeadPayload,
} from '../../services/leadService'
import { fetchCampaigns } from '../../services/marketingService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import type { ImportResult } from '../../types/importResult'
import {
  SOURCE_LABELS,
  STATUS_COLORS,
  STATUS_LABELS,
  type Lead,
  type LeadSource,
  type LeadStatus,
} from '../../types/lead'
import LeadConvertModal from '../../components/LeadConvertModal'
import { extractCfParams, useCustomFieldFilterColumns } from '../../hooks/useCustomFieldFilters'
import {
  fromCustomFieldValues,
  toCustomFieldPayload,
} from '../../utils/customField'
import { CustomFieldFormItems } from '../../components/CustomFieldItems'

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
  customFieldValues?: Record<string, string | number | undefined>
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
  const [saving, setSaving] = useState(false)
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [importing, setImporting] = useState(false)
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  const customFieldFilterColumns = useCustomFieldFilterColumns('LEAD')

  const onImport = async (file: File) => {
    setImporting(true)
    try {
      const res = await importLeads(file)
      setImportResult(res)
      setImportOpen(true)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '导入失败'))
    } finally {
      setImporting(false)
    }
    return false
  }

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
    const cf = fromCustomFieldValues(row.customFieldValues)
    if (cf) form.setFieldsValue({ customFieldValues: cf })
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
      customFieldValues: toCustomFieldPayload(values.customFieldValues as Record<string, unknown>),
    }
    setSaving(true)
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
    } finally {
      setSaving(false)
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
      width: 300,
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
        size="small"
        headerTitle="线索管理"
        rowKey="id"
        actionRef={actionRef}
        columns={[...columns, ...customFieldFilterColumns]}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchLeads({
            keyword: params.keyword,
            status: params.status,
            source: params.source,
            poolOnly: activeTab === 'pool',
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
            ...extractCfParams(params as Record<string, unknown>),
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Upload key="import" showUploadList={false} beforeUpload={(f) => onImport(f as unknown as File)} accept=".xlsx">
            <Button icon={<UploadOutlined />} loading={importing}>导入</Button>
          </Upload>,
          <Button key="template" icon={<DownloadOutlined />} onClick={() => void downloadLeadTemplate()}>
            下载模板
          </Button>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增线索
          </Button>,
        ]}
      />

      {/* 导入结果反馈 */}
      <Modal
        title="导入结果"
        open={importOpen}
        footer={null}
        onCancel={() => setImportOpen(false)}
      >
        <div style={{ marginBottom: 12 }}>
          <Tag color="green">成功 {importResult?.successCount ?? 0} 条</Tag>
          <Tag color="red">失败 {importResult?.failureCount ?? 0} 条</Tag>
        </div>
        {(importResult?.failures ?? []).length > 0 && (
          <Table
            rowKey={(r, i) => `${(r as { row: number }).row}-${i}`}
            size="small"
            dataSource={importResult?.failures ?? []}
            pagination={false}
            columns={[
              { title: '行号', dataIndex: 'row', width: 80 },
              { title: '失败原因', dataIndex: 'message' },
            ]}
          />
        )}
      </Modal>

      <Modal
        title={editing ? '编辑线索' : '新增线索'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        confirmLoading={saving}
        destroyOnClose
        width={640}
      >
        <Form
          form={form}
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="company" label="公司" rules={[{ required: true, message: '请输入公司' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="title" label="职位">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label="电话">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="email" label="邮箱">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="source" label="来源">
                <Select
                  options={Object.entries(SOURCE_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="状态">
                <Select
                  options={Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="score" label="评分（0-100）">
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="campaignId" label="营销活动">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择来源活动（可选）"
                  options={campaignOptions}
                />
              </Form.Item>
            </Col>
          </Row>
          <CustomFieldFormItems entityType="LEAD" />
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
