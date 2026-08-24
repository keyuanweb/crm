import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createAnnouncement,
  deleteAnnouncement,
  fetchAnnouncements,
  markAnnouncementRead,
  updateAnnouncement,
} from '../../services/announcementService'
import { extractErrorMessage } from '../../services/apiClient'
import type { Announcement } from '../../types/announcement'
import dayjs from 'dayjs'

interface FormValues {
  title: string
  content: string
  pinned?: boolean
  expiresAt?: string
}

export default function AnnouncementPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<Announcement | null>(null)
  const [form] = Form.useForm<FormValues>()

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ pinned: false })
    setModalOpen(true)
  }

  const openEdit = (row: Announcement) => {
    setEditing(row)
    form.setFieldsValue({
      title: row.title,
      content: row.content,
      pinned: row.pinned,
      expiresAt: row.expiresAt,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const payload = {
        title: values.title.trim(),
        content: values.content,
        pinned: values.pinned ?? false,
        expiresAt: values.expiresAt ? dayjs(values.expiresAt).format('YYYY-MM-DDTHH:mm:ss') : undefined,
      }
      if (editing) {
        await updateAnnouncement(editing.id, payload)
        message.success('已保存')
      } else {
        await createAnnouncement(payload)
        message.success('已发布')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Announcement) => {
    try {
      await deleteAnnouncement(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const onRead = async (row: Announcement) => {
    try {
      await markAnnouncementRead(row.id)
      message.success('已读')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const columns: ProColumns<Announcement>[] = [
    {
      title: '标题',
      dataIndex: 'title',
      render: (_, row) => (
        <Space size={6}>
          {row.pinned ? <Tag color="orange">置顶</Tag> : null}
          {!row.read ? <Tag color="red">未读</Tag> : null}
          <Typography.Text strong>{row.title}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '内容',
      dataIndex: 'content',
      search: false,
      ellipsis: true,
      render: (_, row) => row.content.replace(/<[^>]*>/g, ''),
    },
    {
      title: '发布时间',
      dataIndex: 'createdAt',
      width: 150,
      search: false,
      render: (_, row) => (row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 190,
      render: (_, row) => [
        !row.read ? (
          <a key="read" onClick={() => void onRead(row)}>
            标记已读
          </a>
        ) : null,
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm key="delete" title="确定删除该公告？" onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<Announcement>
        size="small"
        headerTitle="团队公告"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ pageSize: 10 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchAnnouncements({ page: params.current ?? 1, pageSize: params.pageSize ?? 10 })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            发布公告
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑公告' : '发布公告'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={640}
      >
        <Form form={form} name="announceForm" layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="如：季度目标发布" />
          </Form.Item>
          <Form.Item name="content" label="正文" rules={[{ required: true, message: '请输入正文' }]}>
            <Input.TextArea rows={6} placeholder="支持 HTML" />
          </Form.Item>
          <Space size={32}>
            <Form.Item name="pinned" label="置顶" valuePropName="checked" style={{ marginBottom: 0 }}>
              <Switch />
            </Form.Item>
            <Form.Item name="expiresAt" label="过期时间（留空永久）" style={{ marginBottom: 0 }}>
              <DatePicker showTime placeholder="选择过期时间" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  )
}
