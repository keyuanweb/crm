import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Col, Form, Input, Modal, Popconfirm, Row, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { createTag, deleteTag, fetchTags, updateTag } from '../../services/tagService'
import { extractErrorMessage } from '../../services/apiClient'
import type { Tag as TagItem } from '../../types/tag'

interface FormValues {
  name: string
  color?: string
}

const COLOR_OPTIONS = ['red', 'volcano', 'orange', 'gold', 'lime', 'green', 'cyan', 'blue', 'geekblue', 'purple']

export default function TagListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<TagItem | null>(null)
  const [form] = Form.useForm<FormValues>()

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: TagItem) => {
    setEditing(row)
    form.setFieldsValue({ name: row.name, color: row.color })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) {
        await updateTag(editing.id, values)
        message.success('已保存')
      } else {
        await createTag({ ...values, entityType: 'CUSTOMER' })
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

  const onDelete = async (row: TagItem) => {
    try {
      await deleteTag(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<TagItem>[] = [
    {
      title: '标签',
      dataIndex: 'name',
      render: (_, row) => <Tag color={row.color}>{row.name}</Tag>,
    },
    { title: '颜色', dataIndex: 'color', search: false, render: (_, row) => row.color || '-' },
    { title: '适用实体', dataIndex: 'entityType', width: 110, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm key="delete" title={`确定删除标签「${row.name}」吗？`} onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<TagItem>
        size="small"
        headerTitle="标签管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async () => {
          const items = await fetchTags('CUSTOMER')
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增标签
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑标签' : '新增标签'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
      >
        <Form form={form} name="tagForm" layout="horizontal" labelCol={{ flex: '80px' }} wrapperCol={{ flex: 1 }}>
          <Form.Item name="name" label="标签名" rules={[{ required: true, message: '请输入标签名' }]}>
            <Input placeholder="如：VIP、重点客户" />
          </Form.Item>
          <Form.Item name="color" label="颜色">
            <Row gutter={[4, 4]}>
              {COLOR_OPTIONS.map((c) => (
                <Col key={c} span={2}>
                  <Form.Item name="color" noStyle>
                    <Tag
                      color={c}
                      style={{ cursor: 'pointer' }}
                      onClick={() => form.setFieldValue('color', c)}
                    >
                      {c === form.getFieldValue('color') ? '✓' : ' '}
                    </Tag>
                  </Form.Item>
                </Col>
              ))}
            </Row>
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
