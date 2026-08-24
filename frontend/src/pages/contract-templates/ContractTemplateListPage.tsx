import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, Modal, Popconfirm, Select, Tag, Typography } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createContractTemplate,
  deleteContractTemplate,
  fetchContractTemplates,
  updateContractTemplate,
  type ContractTemplatePayload,
} from '../../services/contractService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import type { ContractTemplate } from '../../types/contract'

interface FormValues {
  name: string
  content: string
  status?: 'ACTIVE' | 'INACTIVE'
}

export default function ContractTemplateListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<ContractTemplate | null>(null)
  const [form] = Form.useForm<FormValues>()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: ContractTemplate) => {
    setEditing(row)
    form.setFieldsValue({ name: row.name, content: row.content, status: row.status })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: ContractTemplatePayload = {
      name: values.name.trim(),
      content: values.content,
      status: values.status,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateContractTemplate(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createContractTemplate(payload)
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

  const onDelete = async (row: ContractTemplate) => {
    try {
      await deleteContractTemplate(row.id)
      message.success('已停用')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<ContractTemplate>[] = [
    { title: '名称', dataIndex: 'name' },
    {
      title: '正文',
      dataIndex: 'content',
      search: false,
      ellipsis: true,
      render: (_, row) => (
        <Typography.Text type="secondary" ellipsis={{ tooltip: row.content }} style={{ maxWidth: 400 }}>
          {row.content}
        </Typography.Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: {
        ACTIVE: { text: '启用' },
        INACTIVE: { text: '停用' },
      },
      render: (_, row) =>
        row.status === 'ACTIVE' ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) =>
        isAdmin
          ? [
              <a key="edit" onClick={() => openEdit(row)}>
                <EditOutlined /> 编辑
              </a>,
              <Popconfirm key="delete" title={`确定停用模板「${row.name}」吗？`} onConfirm={() => onDelete(row)}>
                <a style={{ color: '#ff4d4f' }}>
                  <DeleteOutlined /> 停用
                </a>
              </Popconfirm>,
            ]
          : [],
    },
  ]

  return (
    <>
      <ProTable<ContractTemplate>
        size="small"
        headerTitle="合同模板"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchContractTemplates({
            keyword: params.keyword,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() =>
          isAdmin
            ? [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新增模板
                </Button>,
              ]
            : []
        }
      />

      <Modal
        title={editing ? '编辑模板' : '新增模板'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={680}
      >
        <Form form={form} name="contractTemplateForm" layout="vertical">            <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="status" label="状态" style={{ flex: 1 }}>
              <Select
                options={[
                  { value: 'ACTIVE', label: '启用' },
                  { value: 'INACTIVE', label: '停用' },
                ]}
              />
            </Form.Item>
          <Form.Item
            name="content"
            label="模板正文"
            extra="支持占位符：{customerName} 客户名、{contractNo} 合同编号、{amount} 金额（元）"
            rules={[{ required: true, message: '请输入模板正文' }]}
          >
            <Input.TextArea rows={8} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
