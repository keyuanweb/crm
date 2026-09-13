import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, Modal, Popconfirm, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import {
  createArticle,
  deleteArticle,
  fetchArticles,
  publishArticle,
  unpublishArticle,
  updateArticle,
  type ArticlePayload,
} from '../../services/knowledgeService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { type ArticleCategory, type KnowledgeArticle } from '../../types/knowledge'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'

interface FormValues {
  category: ArticleCategory
  title: string
  content?: string
  keywords?: string
}

export default function KnowledgeArticleListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<KnowledgeArticle | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 086：发布/下架（knowledge:update）与删除（knowledge:delete）挂的是两个不同的码，
  // 分别按各自端点收口。
  const can = usePerms([PERMS.knowledgeUpdate, PERMS.knowledgeDelete])

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: KnowledgeArticle) => {
    setEditing(row)
    form.setFieldsValue({
      category: row.category,
      title: row.title,
      content: row.content,
      keywords: row.keywords,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: ArticlePayload = {
      category: values.category,
      title: values.title.trim(),
      content: values.content,
      keywords: values.keywords,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateArticle(editing.id, { ...payload, version: editing.version })
        message.success(t('pages.knowledge.msgSaved'))
      } else {
        await createArticle(payload)
        message.success(t('pages.knowledge.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.knowledge.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onPublish = async (row: KnowledgeArticle) => {
    try {
      await publishArticle(row.id)
      message.success(t('pages.knowledge.msgPublished'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.knowledge.msgOperationFailed')))
    }
  }

  const onUnpublish = async (row: KnowledgeArticle) => {
    try {
      await unpublishArticle(row.id)
      message.success(t('pages.knowledge.msgUnpublished'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.knowledge.msgOperationFailed')))
    }
  }

  const onDelete = async (row: KnowledgeArticle) => {
    try {
      await deleteArticle(row.id)
      message.success(t('pages.knowledge.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.knowledge.msgDeleteFailed')))
    }
  }

  const columns: ProColumns<KnowledgeArticle>[] = [
    { title: t('pages.knowledge.colTitle'), dataIndex: 'title' },
    {
      title: t('pages.knowledge.colCategory'),
      dataIndex: 'category',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.articleCategory).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.articleCategory, code) },
        ]),
      ),
      render: (_, row) => <Tag color="blue">{labelOf(t, ENUM_KEYS.articleCategory, row.category)}</Tag>,
    },
    {
      title: t('pages.knowledge.colStatus'),
      dataIndex: 'status',
      search: false,
      render: (_, row) =>
        row.status === 'PUBLISHED' ? <Tag color="green">{t('pages.knowledge.statusPublished')}</Tag> : <Tag>{t('pages.knowledge.statusDraft')}</Tag>,
    },
    { title: t('pages.knowledge.colKeywords'), dataIndex: 'keywords', search: false },
    { title: t('pages.knowledge.colAuthor'), dataIndex: 'authorName', search: false },
    {
      title: t('pages.knowledge.colCreatedAt'),
      dataIndex: 'createdAt',
      search: false,
      valueType: 'dateTime',
    },
    {
      title: t('pages.knowledge.colAction'),
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        row.status === 'DRAFT' ? (
          can[PERMS.knowledgeUpdate] ? (
            <a key="publish" onClick={() => onPublish(row)}>
              {t('pages.knowledge.btnPublish')}
            </a>
          ) : null
        ) : can[PERMS.knowledgeUpdate] ? (
          <a key="unpublish" onClick={() => onUnpublish(row)}>
            {t('pages.knowledge.btnUnpublish')}
          </a>
        ) : null,
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.knowledge.btnEdit')}
        </a>,
        can[PERMS.knowledgeDelete] ? (
          <Popconfirm
            key="delete"
            title={t('pages.knowledge.confirmDelete', { title: row.title })}
            onConfirm={() => onDelete(row)}
          >
            <a style={{ color: '#ff4d4f' }}>{t('common.button.delete')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  return (
    <>
      <ProTable<KnowledgeArticle>
        size="small"
        headerTitle={t('pages.knowledge.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchArticles({
            keyword: params.keyword,
            category: params.category,
            includeDraft: true,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.knowledge.btnCreate')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.knowledge.modalEdit') : t('pages.knowledge.modalCreate')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
        width={720}
      >
        <Form form={form} name="articleForm" layout="vertical">
          <Form.Item
            name="category"
            label={t('pages.knowledge.formCategory')}
            rules={[{ required: true, message: t('pages.knowledge.msgCategoryRequired') }]}
          >
            <Select
              options={Object.keys(ENUM_KEYS.articleCategory).map((code) => ({
                value: code,
                label: labelOf(t, ENUM_KEYS.articleCategory, code),
              }))}
            />
          </Form.Item>
          <Form.Item name="title" label={t('pages.knowledge.formTitle')} rules={[{ required: true, message: t('pages.knowledge.msgTitleRequired') }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="keywords" label={t('pages.knowledge.formKeywords')}>
            <Input placeholder={t('pages.knowledge.placeholderKeywords')} />
          </Form.Item>
          <Form.Item name="content" label={t('pages.knowledge.formContent')}>
            <Input.TextArea rows={10} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
