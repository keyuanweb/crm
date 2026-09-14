import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
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
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<Announcement | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 086：删除公告按权限码收口（DELETE /announcements/{id} 挂 announcement:manage）。
  const can = usePerms([PERMS.announcementManage])

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
        message.success(t('pages.announcement.messages.saved'))
      } else {
        await createAnnouncement(payload)
        message.success(t('pages.announcement.messages.published'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.announcement.messages.saveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Announcement) => {
    try {
      await deleteAnnouncement(row.id)
      message.success(t('pages.announcement.messages.deleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.announcement.messages.deleteFailed')))
    }
  }

  const onRead = async (row: Announcement) => {
    try {
      await markAnnouncementRead(row.id)
      message.success(t('pages.announcement.messages.markedRead'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.announcement.messages.operationFailed')))
    }
  }

  const columns: ProColumns<Announcement>[] = [
    {
      title: t('pages.announcement.colTitle'),
      dataIndex: 'title',
      render: (_, row) => (
        <Space size={6}>
          {row.pinned ? <Tag color="orange">{t('pages.announcement.tags.pinned')}</Tag> : null}
          {!row.read ? <Tag color="red">{t('pages.announcement.tags.unread')}</Tag> : null}
          <Typography.Text strong>{row.title}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('pages.announcement.colContent'),
      dataIndex: 'content',
      search: false,
      ellipsis: true,
      render: (_, row) => row.content.replace(/<[^>]*>/g, ''),
    },
    {
      title: t('pages.announcement.colCreatedAt'),
      dataIndex: 'createdAt',
      width: 150,
      search: false,
      render: (_, row) => (row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: t('pages.announcement.colAction'),
      valueType: 'option',
      width: 190,
      render: (_, row) => [
        !row.read ? (
          <a key="read" onClick={() => void onRead(row)}>
            {t('pages.announcement.action.markRead')}
          </a>
        ) : null,
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.announcement.action.edit')}
        </a>,
        can[PERMS.announcementManage] ? (
          <Popconfirm key="delete" title={t('pages.announcement.confirmDelete')} onConfirm={() => onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('pages.announcement.action.delete')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  return (
    <>
      <ProTable<Announcement>
        size="small"
        headerTitle={t('pages.announcement.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ pageSize: 10 }}
        request={async (params) => {
          const res = await fetchAnnouncements({ page: params.current ?? 1, pageSize: params.pageSize ?? 10 })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.announcement.toolbar.publish')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.announcement.modal.edit') : t('pages.announcement.modal.publish')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.announcement.modal.save')}
        destroyOnClose
        width={640}
      >
        <Form form={form} name="announceForm" layout="vertical">
          <Form.Item name="title" label={t('pages.announcement.form.title')} rules={[{ required: true, message: t('pages.announcement.form.titleRequired') }]}>
            <Input placeholder={t('pages.announcement.form.titlePlaceholder')} />
          </Form.Item>
          <Form.Item name="content" label={t('pages.announcement.form.content')} rules={[{ required: true, message: t('pages.announcement.form.contentRequired') }]}>
            <Input.TextArea rows={6} placeholder={t('pages.announcement.form.contentPlaceholder')} />
          </Form.Item>
          <Space size={32}>
            <Form.Item name="pinned" label={t('pages.announcement.form.pinned')} valuePropName="checked" style={{ marginBottom: 0 }}>
              <Switch />
            </Form.Item>
            <Form.Item name="expiresAt" label={t('pages.announcement.form.expiresAt')} style={{ marginBottom: 0 }}>
              <DatePicker showTime placeholder={t('pages.announcement.form.expiresAtPlaceholder')} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  )
}
