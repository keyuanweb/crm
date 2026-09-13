import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Col, Form, Input, Modal, Popconfirm, Row, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { createTag, deleteTag, fetchTags, updateTag } from '../../services/tagService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { Tag as TagItem } from '../../types/tag'

interface FormValues {
  name: string
  color?: string
}

const COLOR_OPTIONS = ['red', 'volcano', 'orange', 'gold', 'lime', 'green', 'cyan', 'blue', 'geekblue', 'purple']

export default function TagListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<TagItem | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 删除标签走 DELETE /tags/{id}，TagController 上标的是 tag:manage（新建/编辑也是同一个码）。
  const can = usePerms([PERMS.tagManage])

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
        message.success(t('pages.tagList.msgSaved'))
      } else {
        await createTag({ ...values, entityType: 'CUSTOMER' })
        message.success(t('pages.tagList.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.tagList.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: TagItem) => {
    try {
      await deleteTag(row.id)
      message.success(t('pages.tagList.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.tagList.msgDeleteFailed')))
    }
  }

  const columns: ProColumns<TagItem>[] = [
    {
      title: t('pages.tagList.colTag'),
      dataIndex: 'name',
      render: (_, row) => <Tag color={row.color}>{row.name}</Tag>,
    },
    { title: t('pages.tagList.colColor'), dataIndex: 'color', search: false, render: (_, row) => row.color || '-' },
    { title: t('pages.tagList.colEntityType'), dataIndex: 'entityType', width: 110, search: false },
    {
      title: t('pages.tagList.colAction'),
      valueType: 'option',
      width: 120,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.tagList.edit')}
        </a>,
        can[PERMS.tagManage] && (
          <Popconfirm key="delete" title={t('pages.tagList.confirmDelete', { name: row.name })} onConfirm={() => onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('pages.tagList.delete')}</a>
          </Popconfirm>
        ),
      ],
    },
  ]

  return (
    <div className="page-stack">
      <ProTable<TagItem>
        size="small"
        headerTitle={t('pages.tagList.title')}
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
            {t('pages.tagList.btnAdd')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.tagList.modalEditTitle') : t('pages.tagList.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.tagList.btnSave')}
        destroyOnClose
      >
        <Form form={form} name="tagForm" layout="horizontal" labelCol={{ flex: '80px' }} wrapperCol={{ flex: 1 }}>
          <Form.Item name="name" label={t('pages.tagList.formNameLabel')} rules={[{ required: true, message: t('pages.tagList.formNameRequired') }]}>
            <Input placeholder={t('pages.tagList.formNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="color" label={t('pages.tagList.formColorLabel')}>
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
    </div>
  )
}
