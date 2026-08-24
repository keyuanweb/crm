import { useEffect, useRef, useState } from 'react'
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
import type { Segment, SegmentCondition } from '../../types/tag'

interface FilterRow {
  key: number
  field: 'tag' | 'amount' | 'lastFollowUpDays'
  op: 'IN' | 'GT' | 'GTE' | 'LT' | 'LTE'
  values?: string[]
  value?: number
}

const FIELD_LABELS: Record<string, string> = {
  tag: '标签',
  amount: '订单金额（元）',
  lastFollowUpDays: '距最近跟进天数',
}

export default function SegmentListPage() {
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
        message.success('已保存')
      } else {
        await createSegment(payload)
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

  const reload = () => actionRef.current?.reload()

  const onDelete = async (row: Segment) => {
    try {
      await deleteSegment(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const openMembers = async (row: Segment) => {
    setMemberDrawer(row)
    const res = await fetchSegmentMembers(row.id, { page: 1, pageSize: 50 })
    setMemberData(res.items)
  }

  const columns: ProColumns<Segment>[] = [
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description', search: false, ellipsis: true, render: (_, row) => row.description || '-' },
    {
      title: '成员数',
      dataIndex: 'memberCount',
      width: 90,
      search: false,
      render: (_, row) => <Tag color="blue">{row.memberCount}</Tag>,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      render: (_, row) => [
        <a key="members" onClick={() => void openMembers(row)}>
          成员
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm key="delete" title={`确定删除细分「${row.name}」吗？`} onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<Segment>
        size="small"
        headerTitle="客户细分"
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
            新增细分
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑细分' : '新增细分'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={680}
      >
        <Form form={form} name="segmentForm" layout="horizontal" labelCol={{ flex: '80px' }} wrapperCol={{ flex: 1 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="细分名" rules={[{ required: true, message: '请输入细分名' }]}>
                <Input placeholder="如：高价值未跟进" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="description" label="描述">
                <Input placeholder="细分用途说明" />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        <div style={{ marginBottom: 8 }}>
          <Space>
            <span style={{ fontWeight: 600, fontSize: 13 }}>条件逻辑：</span>
            <Radio.Group value={logic} onChange={(e) => setLogic(e.target.value)} size="small">
              <Radio.Button value="AND">满足全部（AND）</Radio.Button>
              <Radio.Button value="OR">满足任一（OR）</Radio.Button>
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
                  options={Object.entries(FIELD_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Col>
              <Col span={5}>
                <Select
                  value={f.op}
                  onChange={(v) => updateFilter(f.key, { op: v })}
                  style={{ width: '100%' }}
                  options={
                    f.field === 'tag'
                      ? [{ value: 'IN', label: '包含' }]
                      : [
                          { value: 'GT', label: '大于' },
                          { value: 'GTE', label: '大于等于' },
                          { value: 'LT', label: '小于' },
                          { value: 'LTE', label: '小于等于' },
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
                    placeholder="选择/输入标签名"
                    style={{ width: '100%' }}
                    options={tagOptions}
                  />
                ) : (
                  <InputNumber
                    value={f.value}
                    onChange={(v) => updateFilter(f.key, { value: v ?? undefined })}
                    placeholder={f.field === 'lastFollowUpDays' ? '天数，如 30' : '金额，如 100000'}
                    style={{ width: '100%' }}
                  />
                )}
              </Col>
              <Col span={3}>
                <Button size="small" danger onClick={() => removeFilter(f.key)}>
                  删
                </Button>
              </Col>
            </Row>
          ))}
          <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addFilter} block>
            添加条件
          </Button>
        </div>
      </Modal>

      <Drawer
        title={`细分成员：${memberDrawer?.name ?? ''}`}
        open={!!memberDrawer}
        onClose={() => setMemberDrawer(null)}
        width={420}
      >
        {memberData.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#8c8c8c', padding: 40 }}>暂无成员</div>
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
