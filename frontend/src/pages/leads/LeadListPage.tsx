import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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
import { hasPerm } from '../../hooks/usePermission'
import { PERMS } from '../../constants/permissions'
import type { ImportResult } from '../../types/importResult'
import {
  STATUS_COLORS,
  type Lead,
  type LeadSource,
  type LeadStatus,
} from '../../types/lead'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
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
  const { t } = useTranslation()
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
  // 「分配给我」走 POST /leads/{id}/assign，LeadController 上标的是 lead:assign。
  // 改造前写 `role === 'ADMIN'`——081 新增的角色里凡是拿到 lead:assign 的（如 SALES_MANAGER）
  // 在列表上根本看不到这个操作。
  const canAssign = hasPerm(PERMS.leadAssign, user)
  // 删除线索走 DELETE /leads/{id}，LeadController 上标的是 lead:delete。
  // 改造前这一处**完全没有判据**——谁都能看到「删除」并按下去，是本规格要补的漏网。
  const canDelete = hasPerm(PERMS.leadDelete, user)
  const customFieldFilterColumns = useCustomFieldFilterColumns('LEAD')

  const onImport = async (file: File) => {
    setImporting(true)
    try {
      const res = await importLeads(file)
      setImportResult(res)
      setImportOpen(true)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
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
        message.success(t('pages.lead.list.msgSaved'))
      } else {
        await createLead(payload)
        message.success(t('pages.lead.list.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Lead) => {
    try {
      await deleteLead(row.id)
      message.success(t('pages.lead.list.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const onClaim = async (row: Lead) => {
    try {
      await claimLead(row.id)
      message.success(t('pages.lead.list.msgClaimed'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const onAssignToMe = async (row: Lead) => {
    if (!user?.id) return
    try {
      await assignLead(row.id, user.id)
      message.success(t('pages.lead.list.msgAssigned'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const canEdit = (row: Lead) => row.status !== 'QUALIFIED' && row.status !== 'DISQUALIFIED'
  const canConvert = (row: Lead) => row.status === 'NEW' || row.status === 'WORKING'

  const columns: ProColumns<Lead>[] = [
    {
      title: t('pages.lead.list.colName'),
      dataIndex: 'name',
      render: (_, row) => <Link to={`/leads/${row.id}`}>{row.name}</Link>,
    },
    { title: t('pages.lead.list.colCompany'), dataIndex: 'company' },
    { title: t('pages.lead.list.colTitle'), dataIndex: 'title', search: false },
    { title: t('pages.lead.list.colPhone'), dataIndex: 'phone', search: false },
    { title: t('pages.lead.list.colEmail'), dataIndex: 'email', search: false },
    {
      title: t('pages.lead.list.colSource'),
      dataIndex: 'source',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.source).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.source, code) },
        ]),
      ),
    },
    {
      title: t('pages.lead.list.colStatus'),
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.leadStatus).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.leadStatus, code), status: STATUS_COLORS[code as LeadStatus] },
        ]),
      ),
    },
    {
      title: t('pages.lead.list.colScore'),
      dataIndex: 'score',
      search: false,
      render: (_, row) => <Tag color={row.score >= 70 ? 'green' : row.score >= 40 ? 'orange' : 'default'}>{row.score}</Tag>,
    },
    { title: t('pages.lead.list.colOwner'), dataIndex: 'ownerName', search: false },
    {
      title: t('pages.lead.list.colAction'),
      valueType: 'option',
      width: 300,
      render: (_, row) => [
        canEdit(row) && (
          <a key="edit" onClick={() => openEdit(row)}>
            <EditOutlined /> {t('pages.lead.list.edit')}
          </a>
        ),
        row.ownerId == null && (
          <a key="claim" onClick={() => onClaim(row)}>
            <UserAddOutlined /> {t('pages.lead.list.claim')}
          </a>
        ),
        row.ownerId != null && row.ownerId !== user?.id && canAssign && (
          <a key="assign" onClick={() => onAssignToMe(row)}>
            <SwapOutlined /> {t('pages.lead.list.assignToMe')}
          </a>
        ),
        canConvert(row) && (
          <a key="convert" onClick={() => openConvert(row)}>
            <SwapOutlined /> {t('pages.lead.list.convert')}
          </a>
        ),
        canEdit(row) && canDelete && (
          <Popconfirm key="delete" title={t('pages.lead.list.deleteConfirm', { name: row.name })} onConfirm={() => onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>
              <DeleteOutlined /> {t('pages.lead.list.delete')}
            </a>
          </Popconfirm>
        ),
      ],
    },
  ]

  return (
    <>
      <Space>
        <Button type={activeTab === 'all' ? 'primary' : 'default'} onClick={() => { setActiveTab('all'); reload() }}>
          {t('pages.lead.list.viewAll')}
        </Button>
        <Button type={activeTab === 'pool' ? 'primary' : 'default'} onClick={() => { setActiveTab('pool'); reload() }}>
          {t('pages.lead.list.viewPool')}
        </Button>
      </Space>

      <div style={{ height: 16 }} />

      <ProTable<Lead>
        size="small"
        headerTitle={t('pages.lead.list.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={[...columns, ...customFieldFilterColumns]}
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
            ...extractCfParams(params as Record<string, unknown>),
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Upload key="import" showUploadList={false} beforeUpload={(f) => onImport(f as unknown as File)} accept=".xlsx">
            <Button icon={<UploadOutlined />} loading={importing}>{t('pages.lead.list.import')}</Button>
          </Upload>,
          <Button key="template" icon={<DownloadOutlined />} onClick={() => void downloadLeadTemplate()}>
            {t('pages.lead.list.downloadTemplate')}
          </Button>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.lead.list.create')}
          </Button>,
        ]}
      />

      {/* 导入结果反馈 */}
      <Modal
        title={t('pages.lead.list.importResult')}
        open={importOpen}
        footer={null}
        onCancel={() => setImportOpen(false)}
      >
        <div style={{ marginBottom: 12 }}>
          <Tag color="green">{t('pages.lead.list.importSuccess', { count: importResult?.successCount ?? 0 })}</Tag>
          <Tag color="red">{t('pages.lead.list.importFail', { count: importResult?.failureCount ?? 0 })}</Tag>
        </div>
        {(importResult?.failures ?? []).length > 0 && (
          <Table
            rowKey={(r, i) => `${(r as { row: number }).row}-${i}`}
            size="small"
            dataSource={importResult?.failures ?? []}
            pagination={false}
            columns={[
              { title: t('pages.lead.list.colRow'), dataIndex: 'row', width: 80 },
              { title: t('pages.lead.list.colFail'), dataIndex: 'message' },
            ]}
          />
        )}
      </Modal>

      <Modal
        title={editing ? t('pages.lead.list.editModal') : t('pages.lead.list.createModal')}
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
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label={t('pages.lead.list.formName')} rules={[{ required: true, message: t('pages.lead.list.msgNameRequired') }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="company" label={t('pages.lead.list.formCompany')} rules={[{ required: true, message: t('pages.lead.list.msgCompanyRequired') }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="title" label={t('pages.lead.list.formTitle')}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label={t('pages.lead.list.formPhone')}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="email" label={t('pages.lead.list.formEmail')}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="source" label={t('pages.lead.list.colSource')}>
                <Select
                  options={Object.keys(ENUM_KEYS.source).map((value) => ({
                    value,
                    label: labelOf(t, ENUM_KEYS.source, value),
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label={t('pages.lead.list.colStatus')}>
                <Select
                  options={Object.keys(ENUM_KEYS.leadStatus).map((value) => ({
                    value,
                    label: labelOf(t, ENUM_KEYS.leadStatus, value),
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="score" label={t('pages.lead.list.formScore')}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="campaignId" label={t('pages.lead.list.formCampaign')}>
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder={t('pages.lead.list.phCampaign')}
                  options={campaignOptions}
                />
              </Form.Item>
            </Col>
          </Row>
          <CustomFieldFormItems entityType="LEAD" />
          <Form.Item name="remark" label={t('pages.lead.list.formRemark')}>
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
