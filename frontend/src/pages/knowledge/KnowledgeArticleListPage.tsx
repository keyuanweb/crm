import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, Modal, Popconfirm, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
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
import {
  ARTICLE_CATEGORY_LABELS,
  type ArticleCategory,
  type KnowledgeArticle,
} from '../../types/knowledge'

interface FormValues {
  category: ArticleCategory
  title: string
  content?: string
  keywords?: string
}

export default function KnowledgeArticleListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<KnowledgeArticle | null>(null)
  const [form] = Form.useForm<FormValues>()

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
        message.success('已保存')
      } else {
        await createArticle(payload)
        message.success('已创建（草稿）')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const onPublish = async (row: KnowledgeArticle) => {
    try {
      await publishArticle(row.id)
      message.success('已发布')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const onUnpublish = async (row: KnowledgeArticle) => {
    try {
      await unpublishArticle(row.id)
      message.success('已下线')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const onDelete = async (row: KnowledgeArticle) => {
    try {
      await deleteArticle(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<KnowledgeArticle>[] = [
    { title: '标题', dataIndex: 'title' },
    {
      title: '分类',
      dataIndex: 'category',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(ARTICLE_CATEGORY_LABELS).map(([k, v]) => [k, { text: v }]),
      ),
      render: (_, row) => <Tag color="blue">{ARTICLE_CATEGORY_LABELS[row.category]}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      render: (_, row) =>
        row.status === 'PUBLISHED' ? <Tag color="green">已发布</Tag> : <Tag>草稿</Tag>,
    },
    { title: '关键词', dataIndex: 'keywords', search: false },
    { title: '作者', dataIndex: 'authorName', search: false },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      search: false,
      valueType: 'dateTime',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        row.status === 'DRAFT' ? (
          <a key="publish" onClick={() => onPublish(row)}>
            发布
          </a>
        ) : (
          <a key="unpublish" onClick={() => onUnpublish(row)}>
            下线
          </a>
        ),
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm
          key="delete"
          title={`确定删除文章「${row.title}」吗？`}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<KnowledgeArticle>
        size="small"
        headerTitle="知识库"
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
            新增文章
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑文章' : '新增文章'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={720}
      >
        <Form form={form} name="articleForm" layout="vertical">
          <Form.Item
            name="category"
            label="分类"
            rules={[{ required: true, message: '请选择分类' }]}
          >
            <Select
              options={Object.entries(ARTICLE_CATEGORY_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="keywords" label="关键词（逗号分隔）">
            <Input placeholder="如：密码,重置" />
          </Form.Item>
          <Form.Item name="content" label="内容">
            <Input.TextArea rows={10} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
