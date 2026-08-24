import { useRef, useState } from 'react'
import { App, Button, Col, Form, Input, InputNumber, Modal, Popconfirm, Row, Select } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  createOpportunity,
  deleteOpportunity,
  fetchOpportunities,
  updateOpportunity,
  type OpportunityPayload,
} from '../../services/opportunityService'
import { fetchCustomers } from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { formatAmount, type Opportunity } from '../../types/opportunity'
import { useQuery } from '@tanstack/react-query'
import { extractCfParams, useCustomFieldFilterColumns } from '../../hooks/useCustomFieldFilters'
import {
  fromCustomFieldValues,
  toCustomFieldPayload,
} from '../../utils/customField'
import { CustomFieldFormItems } from '../../components/CustomFieldItems'

interface FormValues {
  customerId: number
  name: string
  expectedAmountMin?: number
  expectedAmountMax?: number
  customFieldValues?: Record<string, string | number | undefined>
  remark?: string
}

export default function OpportunityListPage() {
  const actionRef = useRef<ActionType>()
  const customFieldFilterColumns = useCustomFieldFilterColumns('OPPORTUNITY')
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Opportunity | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<FormValues>()

  const customers = useQuery({
    queryKey: ['customers-options'],
    queryFn: () => fetchCustomers({ page: 1, pageSize: 100 }),
  })

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: Opportunity) => {
    setEditing(row)
    form.setFieldsValue({
      customerId: row.customerId,
      name: row.name,
      expectedAmountMin: row.expectedAmountMin ? row.expectedAmountMin / 100 : undefined,
      expectedAmountMax: row.expectedAmountMax ? row.expectedAmountMax / 100 : undefined,
      remark: row.remark,
    })
    const cf = fromCustomFieldValues(row.customFieldValues)
    if (cf) form.setFieldsValue({ customFieldValues: cf })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: OpportunityPayload = {
      customerId: values.customerId,
      name: values.name,
      expectedAmountMin: values.expectedAmountMin ? values.expectedAmountMin * 100 : undefined,
      expectedAmountMax: values.expectedAmountMax ? values.expectedAmountMax * 100 : undefined,
      remark: values.remark,
      customFieldValues: toCustomFieldPayload(values.customFieldValues as Record<string, unknown>),
    }
    setSaving(true)
    try {
      if (editing) {
        await updateOpportunity(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createOpportunity(payload)
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

  const onDelete = async (row: Opportunity) => {
    try {
      await deleteOpportunity(row.id)
      message.success('已删除（含销售机会）')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<Opportunity>[] = [
    { title: '商机名称', dataIndex: 'name' },
    { title: '关联客户', dataIndex: 'customerName', search: false },
    {
      title: '预期金额（元）',
      search: false,
      render: (_, row) =>
        `${formatAmount(row.expectedAmountMin)} ~ ${formatAmount(row.expectedAmountMax)}`,
    },
    { title: '销售机会数', dataIndex: 'salesOpportunityCount', search: false },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: {
        ACTIVE: { text: '进行中', status: 'Processing' },
        ARCHIVED: { text: '已归档', status: 'Default' },
      },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm
          key="delete"
          title={`确定删除商机「${row.name}」及其销售机会吗？`}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<Opportunity>
        size="small"
        headerTitle="商机管理"
        rowKey="id"
        actionRef={actionRef}
        columns={[...columns, ...customFieldFilterColumns]}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchOpportunities({
            keyword: params.keyword,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
            ...extractCfParams(params as Record<string, unknown>),
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增商机
          </Button>,
        ]}
      />
      <Modal
        title={editing ? '编辑商机' : '新增商机'}
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
          name="opportunityForm"
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="customerId"
                label="关联客户"
                rules={[{ required: true, message: '请选择客户' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={(customers.data?.items ?? []).map((c) => ({
                    value: c.id,
                    label: `${c.name}（${c.company}）`,
                  }))}
                  placeholder="请选择客户"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="name"
                label="商机名称"
                rules={[{ required: true, message: '请输入商机名称' }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="expectedAmountMin" label="预期金额下限">
                <InputNumber min={0} suffix="元" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="expectedAmountMax" label="预期金额上限">
                <InputNumber min={0} suffix="元" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <CustomFieldFormItems entityType="OPPORTUNITY" />
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
