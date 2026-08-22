import { useRef, useState } from 'react'
import { App, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space } from 'antd'
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

interface FormValues {
  customerId: number
  name: string
  expectedAmountMin?: number
  expectedAmountMax?: number
  remark?: string
}

export default function OpportunityListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Opportunity | null>(null)
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
    }
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
        headerTitle="商机管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchOpportunities({
            keyword: params.keyword,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
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
        okText="保存" destroyOnClose>
        <Form form={form} name="opportunityForm" layout="vertical">
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
          <Form.Item name="name" label="商机名称" rules={[{ required: true, message: '请输入商机名称' }]}>
            <Input />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item name="expectedAmountMin" label="预期金额下限（元）" style={{ flex: 1 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="expectedAmountMax" label="预期金额上限（元）" style={{ flex: 1 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
