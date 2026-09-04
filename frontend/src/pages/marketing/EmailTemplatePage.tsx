import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Tag,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createEmailTemplate,
  deleteEmailTemplate,
  fetchEmailTemplates,
  updateEmailTemplate,
} from '../../services/emailService'
import { extractErrorMessage } from '../../services/apiClient'
import type { EmailTemplate } from '../../types/email'

interface FormValues {
  name: string
  subject: string
  content: string
  category: string
}

const CATEGORY_LABELS: Record<string, string> = {
  WELCOME: '欢迎',
  PROMOTION: '促销',
  FOLLOW_UP: '跟进',
  NOTICE: '通知',
}

export default function EmailTemplatePage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<EmailTemplate | null>(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewContent, setPreviewContent] = useState('')
  const [previewSubject, setPreviewSubject] = useState('')
  const [form] = Form.useForm<FormValues>()

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: EmailTemplate) => {
    setEditing(row)
    form.setFieldsValue({ name: row.name, subject: row.subject, content: row.content, category: row.category })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) {
        await updateEmailTemplate(editing.id, values)
        message.success('已保存')
      } else {
        await createEmailTemplate(values)
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

  const onDelete = async (row: EmailTemplate) => {
    try {
      await deleteEmailTemplate(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const openPreview = (row: EmailTemplate) => {
    setPreviewSubject(row.subject.replace('{name}', '示例客户').replace('{company}', '示例公司'))
    setPreviewContent(row.content.replace('{name}', '示例客户').replace('{company}', '示例公司'))
    setPreviewOpen(true)
  }

  const columns: ProColumns<EmailTemplate>[] = [
    { title: '名称', dataIndex: 'name' },
    { title: '主题', dataIndex: 'subject', ellipsis: true },
    { title: '正文', dataIndex: 'content', search: false, ellipsis: true, render: (_, row) => row.content.replace(/<[^>]*>/g, '').slice(0, 40) },
    {
      title: '分类',
      dataIndex: 'category',
      width: 90,
      search: false,
      render: (_, row) => <Tag color="blue">{CATEGORY_LABELS[row.category] ?? row.category}</Tag>,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      render: (_, row) => [
        <a key="preview" onClick={() => openPreview(row)}>
          预览
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm key="delete" title={`确定删除模板「${row.name}」吗？`} onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<EmailTemplate>
        size="small"
        headerTitle="邮件模板"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async () => {
          const items = await fetchEmailTemplates()
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增模板
          </Button>,
        ]}
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
        <Form form={form} name="emailTemplateForm" layout="horizontal" labelCol={{ flex: '70px' }} wrapperCol={{ flex: 1 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
                <Input placeholder="如：客户欢迎" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="category" label="分类">
                <Select
                  options={Object.entries(CATEGORY_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="subject" label="主题" rules={[{ required: true, message: '请输入主题' }]}>
            <Input placeholder="可用变量：{name} {company} {phone}" />
          </Form.Item>
          <Form.Item
            name="content"
            label="正文"
            rules={[{ required: true, message: '请输入正文' }]}
            extra="支持 HTML；变量 {name} {company} {phone}"
          >
            <Input.TextArea rows={8} placeholder="<p>尊敬的 {name}：</p>" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`预览：${previewSubject}`}
        open={previewOpen}
        footer={null}
        onCancel={() => setPreviewOpen(false)}
        width={560}
      >
        <div dangerouslySetInnerHTML={{ __html: previewContent }} />
      </Modal>
    </>
  )
}
