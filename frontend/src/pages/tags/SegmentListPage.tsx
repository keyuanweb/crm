import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Row,
  Select,
  Space,
  Tag,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createSegment,
  deleteSegment,
  fetchSegmentMembers,
  fetchSegments,
  serializeCondition,
  updateSegment,
} from '../../services/segmentService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { Segment, SegmentCondition } from '../../types/tag'

interface FilterRow {
  key: number
  field: 'tag' | 'amount' | 'lastFollowUpDays'
  op: 'IN' | 'GT' | 'GTE' | 'LT' | 'LTE'
  values?: string[]
  value?: number
}

export default function SegmentListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<Segment | null>(null)
  const [form] = Form.useForm<{ name: string; description?: string }>()
  const [logic, setLogic] = useState<'AND' | 'OR'>('AND')
  const [filters, setFilters] = useState<FilterRow[]>([])
  const [tagOptions, setTagOptions] = useState<{ label: string; value: string }[]>([])
  const [memberDrawer, setMemberDrawer] = useState<Segment | null>(null)
  const [memberData, setMemberData] = useState<{ id: number; name: string; company?: string }[]>([])
  const nextKey = useRef(1)
  // 删除细分走 DELETE /segments/{id}，而 SegmentController 上标的是 **tag:manage**（不是 segment:manage
  // ——后者零端点校验，挂上会让按钮对所有人消失）。新建/编辑挂的也是同一个码。
  const can = usePerms([PERMS.tagManage])

  // 条件字段下拉的显示名（原为模块级常量，含中文需 t()，故搬入组件内）
  const fieldLabels: Record<string, string> = {
    tag: t('pages.segmentList.fieldTag'),
    amount: t('pages.segmentList.fieldAmount'),
    lastFollowUpDays: t('pages.segmentList.fieldLastFollowUpDays'),
  }

  useEffect(() => {
    void import('../../services/tagService').then(async ({ fetchTags }) => {
      try {
        const tags = await fetchTags('CUSTOMER')
        setTagOptions(tags.map((t) => ({ label: t.name, value: t.name })))
      } catch {
        setTagOptions([])
      }
    })
  }, [])

  const addFilter = () => {
    setFilters([...filters, { key: nextKey.current++, field: 'tag', op: 'IN', values: [] }])
  }

  const updateFilter = (key: number, patch: Partial<FilterRow>) => {
    setFilters(filters.map((f) => (f.key === key ? { ...f, ...patch } : f)))
  }

  const removeFilter = (key: number) => {
    setFilters(filters.filter((f) => f.key !== key))
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setLogic('AND')
    setFilters([{ key: nextKey.current++, field: 'tag', op: 'IN', values: [] }])
    setModalOpen(true)
  }

  const openEdit = (row: Segment) => {
    setEditing(row)
    form.setFieldsValue({ name: row.name, description: row.description })
    try {
      const cond = JSON.parse(row.conditions) as SegmentCondition
      setLogic(cond.logic)
      setFilters(
        cond.filters.map((f) => ({
          key: nextKey.current++,
          field: f.field,
          op: f.op,
          values: f.values ?? [],
          value: f.value,
        })),
      )
    } catch {
      setLogic('AND')
      setFilters([])
    }
    setModalOpen(true)
  }

  const buildConditions = (): string => {
    const cond: SegmentCondition = {
      logic,
      filters: filters
        .filter((f) => (f.field === 'tag' ? (f.values ?? []).length > 0 : f.value !== undefined))
        .map((f) =>
          f.field === 'tag'
            ? { field: f.field, op: f.op, values: f.values }
            : { field: f.field, op: f.op, value: f.value },
        ),
    }
    return serializeCondition(cond)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload = { name: values.name.trim(), description: values.description, conditions: buildConditions() }
    setSaving(true)
    try {
      if (editing) {
        await updateSegment(editing.id, payload)
        message.success(t('common.message.saved'))
      } else {
        await createSegment(payload)
        message.success(t('pages.segmentList.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.segmentList.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const reload = () => actionRef.current?.reload()

  const onDelete = async (row: Segment) => {
    try {
      await deleteSegment(row.id)
      message.success(t('pages.segmentList.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.segmentList.msgDeleteFailed')))
    }
  }

  const openMembers = async (row: Segment) => {
    setMemberDrawer(row)
    const res = await fetchSegmentMembers(row.id, { page: 1, pageSize: 50 })
    setMemberData(res.items)
  }

  const columns: ProColumns<Segment>[] = [
    { title: t('pages.segmentList.colName'), dataIndex: 'name' },
    { title: t('pages.segmentList.colDescription'), dataIndex: 'description', search: false, ellipsis: true, render: (_, row) => row.description || '-' },
    {
      title: t('pages.segmentList.colMemberCount'),
      dataIndex: 'memberCount',
      width: 90,
      search: false,
      render: (_, row) => <Tag color="blue">{row.memberCount}</Tag>,
    },
    {
      title: t('pages.segmentList.colAction'),
      valueType: 'option',
      width: 180,
      render: (_, row) => [
        <a key="members" onClick={() => void openMembers(row)}>
          {t('pages.segmentList.members')}
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          {t('common.button.edit')}
        </a>,
        can[PERMS.tagManage] && (
          <Popconfirm
            key="delete"
            title={t('pages.segmentList.confirmDelete', { name: row.name })}
            onConfirm={() => onDelete(row)}
          >
            <a style={{ color: '#ff4d4f' }}>{t('common.button.delete')}</a>
          </Popconfirm>
        ),
      ],
    },
  ]

  return (
    <>
      <ProTable<Segment>
        size="small"
        headerTitle={t('pages.segmentList.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async () => {
          const items = await fetchSegments()
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.segmentList.btnAdd')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.segmentList.modalEditTitle') : t('pages.segmentList.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
        width={680}
      >
        <Form form={form} name="segmentForm" layout="horizontal" labelCol={{ flex: '80px' }} wrapperCol={{ flex: 1 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="name"
                label={t('pages.segmentList.formNameLabel')}
                rules={[{ required: true, message: t('pages.segmentList.formNameRequired') }]}
              >
                <Input placeholder={t('pages.segmentList.formNamePlaceholder')} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="description" label={t('pages.segmentList.formDescriptionLabel')}>
                <Input placeholder={t('pages.segmentList.formDescriptionPlaceholder')} />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        <div style={{ marginBottom: 8 }}>
          <Space>
            <span style={{ fontWeight: 600, fontSize: 13 }}>{t('pages.segmentList.conditionLogicLabel')}</span>
            <Radio.Group value={logic} onChange={(e) => setLogic(e.target.value)} size="small">
              <Radio.Button value="AND">{t('pages.segmentList.logicAnd')}</Radio.Button>
              <Radio.Button value="OR">{t('pages.segmentList.logicOr')}</Radio.Button>
            </Radio.Group>
          </Space>
        </div>

        <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 12, background: '#fafafa' }}>
          {filters.map((f) => (
            <Row key={f.key} gutter={8} style={{ marginBottom: 8 }} align="middle">
              <Col span={7}>
                <Select
                  value={f.field}
                  onChange={(v) => updateFilter(f.key, { field: v, op: v === 'tag' ? 'IN' : 'GT' })}
                  style={{ width: '100%' }}
                  options={Object.entries(fieldLabels).map(([value, label]) => ({ value, label }))}
                />
              </Col>
              <Col span={5}>
                <Select
                  value={f.op}
                  onChange={(v) => updateFilter(f.key, { op: v })}
                  style={{ width: '100%' }}
                  options={
                    f.field === 'tag'
                      ? [{ value: 'IN', label: t('pages.segmentList.opIn') }]
                      : [
                          { value: 'GT', label: t('pages.segmentList.opGt') },
                          { value: 'GTE', label: t('pages.segmentList.opGte') },
                          { value: 'LT', label: t('pages.segmentList.opLt') },
                          { value: 'LTE', label: t('pages.segmentList.opLte') },
                        ]
                  }
                />
              </Col>
              <Col span={9}>
                {f.field === 'tag' ? (
                  <Select
                    mode="tags"
                    value={f.values}
                    onChange={(v) => updateFilter(f.key, { values: v as string[] })}
                    placeholder={t('pages.segmentList.tagPlaceholder')}
                    style={{ width: '100%' }}
                    options={tagOptions}
                  />
                ) : (
                  <InputNumber
                    value={f.value}
                    onChange={(v) => updateFilter(f.key, { value: v ?? undefined })}
                    placeholder={
                      f.field === 'lastFollowUpDays'
                        ? t('pages.segmentList.daysPlaceholder')
                        : t('pages.segmentList.amountPlaceholder')
                    }
                    style={{ width: '100%' }}
                  />
                )}
              </Col>
              <Col span={3}>
                <Button size="small" danger onClick={() => removeFilter(f.key)}>
                  {t('pages.segmentList.btnRemoveCondition')}
                </Button>
              </Col>
            </Row>
          ))}
          <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addFilter} block>
            {t('pages.segmentList.btnAddCondition')}
          </Button>
        </div>
      </Modal>

      <Drawer
        title={t('pages.segmentList.memberDrawerTitle', { name: memberDrawer?.name ?? '' })}
        open={!!memberDrawer}
        onClose={() => setMemberDrawer(null)}
        width={420}
      >
        {memberData.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#8c8c8c', padding: 40 }}>
            {t('pages.segmentList.emptyMembers')}
          </div>
        ) : (
          memberData.map((m) => (
            <div key={m.id} style={{ padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
              <b>{m.name}</b>
              {m.company ? <span style={{ color: '#8c8c8c', marginLeft: 8 }}>{m.company}</span> : null}
            </div>
          ))
        )}
      </Drawer>
    </>
  )
}
