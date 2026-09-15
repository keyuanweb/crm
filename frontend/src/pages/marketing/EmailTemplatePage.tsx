import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
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
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { EmailTemplate } from '../../types/email'
import { FormGrid, useFormMetrics } from '../../components/ui'

interface FormValues {
  name: string
  subject: string
  content: string
  category: string
}

export default function EmailTemplatePage() {
  const { t } = useTranslation()
  const metrics = useFormMetrics()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<EmailTemplate | null>(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewContent, setPreviewContent] = useState('')
  const [previewSubject, setPreviewSubject] = useState('')
  const [form] = Form.useForm<FormValues>()
  // 086：删除模板按权限码收口（EmailController 全线只有 email:manage 一个写码）。
  const can = usePerms([PERMS.emailManage])

  const categoryLabels: Record<string, string> = {
    WELCOME: t('pages.marketing.emailTemplate.catWelcome'),
    PROMOTION: t('pages.marketing.emailTemplate.catPromotion'),
    FOLLOW_UP: t('pages.marketing.emailTemplate.catFollowUp'),
    NOTICE: t('pages.marketing.emailTemplate.catNotice'),
  }

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
        message.success(t('common.message.saved'))
      } else {
        await createEmailTemplate(values)
        message.success(t('pages.marketing.emailTemplate.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: EmailTemplate) => {
    try {
      await deleteEmailTemplate(row.id)
      message.success(t('pages.marketing.emailTemplate.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.marketing.emailTemplate.msgDeleteFailed')))
    }
  }

  const openPreview = (row: EmailTemplate) => {
    const sampleName = t('pages.marketing.emailTemplate.previewSampleName')
    const sampleCompany = t('pages.marketing.emailTemplate.previewSampleCompany')
    setPreviewSubject(row.subject.replace('{name}', sampleName).replace('{company}', sampleCompany))
    setPreviewContent(row.content.replace('{name}', sampleName).replace('{company}', sampleCompany))
    setPreviewOpen(true)
  }

  const columns: ProColumns<EmailTemplate>[] = [
    { title: t('pages.marketing.emailTemplate.colName'), dataIndex: 'name' },
    { title: t('pages.marketing.emailTemplate.colSubject'), dataIndex: 'subject', ellipsis: true },
    { title: t('pages.marketing.emailTemplate.colContent'), dataIndex: 'content', search: false, ellipsis: true, render: (_, row) => row.content.replace(/<[^>]*>/g, '').slice(0, 40) },
    {
      title: t('pages.marketing.emailTemplate.colCategory'),
      dataIndex: 'category',
      width: 90,
      search: false,
      render: (_, row) => <Tag color="blue">{categoryLabels[row.category] ?? row.category}</Tag>,
    },
    {
      title: t('pages.marketing.emailTemplate.colAction'),
      valueType: 'option',
      width: 160,
      render: (_, row) => [
        <a key="preview" onClick={() => openPreview(row)}>
          {t('pages.marketing.emailTemplate.btnPreview')}
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          {t('common.button.edit')}
        </a>,
        can[PERMS.emailManage] ? (
          <Popconfirm
            key="delete"
            title={t('pages.marketing.emailTemplate.confirmDelete', { name: row.name })}
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
      <ProTable<EmailTemplate>
        size="small"
        headerTitle={t('pages.marketing.emailTemplate.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        request={async () => {
          const items = await fetchEmailTemplates()
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.marketing.emailTemplate.btnCreate')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.marketing.emailTemplate.modalEditTitle') : t('pages.marketing.emailTemplate.modalCreateTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
        width={680}
      >
        <Form form={form} name="emailTemplateForm" layout="horizontal" labelCol={{ flex: `${metrics.labelWidth}px` }} wrapperCol={{ flex: 1 }}>
          {/* 2 个成对字段进栅格；「主题」「正文」是整行独占，按使用纪律第 1 条留在栅格之外。
              原先的 `<Row gutter={16}>` + 2 个 `<Col span={12}>` 是**写死两列、无任何断点**的。 */}
          <FormGrid>
            <Form.Item name="name" label={t('pages.marketing.emailTemplate.formNameLabel')} rules={[{ required: true, message: t('pages.marketing.emailTemplate.msgNameRequired') }]}>
              <Input placeholder={t('pages.marketing.emailTemplate.phName')} />
            </Form.Item>
            <Form.Item name="category" label={t('pages.marketing.emailTemplate.formCategoryLabel')}>
              <Select
                options={Object.entries(categoryLabels).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
          </FormGrid>
          <Form.Item name="subject" label={t('pages.marketing.emailTemplate.formSubjectLabel')} rules={[{ required: true, message: t('pages.marketing.emailTemplate.msgSubjectRequired') }]}>
            <Input placeholder={t('pages.marketing.emailTemplate.phSubject')} />
          </Form.Item>
          <Form.Item
            name="content"
            label={t('pages.marketing.emailTemplate.formContentLabel')}
            rules={[{ required: true, message: t('pages.marketing.emailTemplate.msgContentRequired') }]}
            extra={t('pages.marketing.emailTemplate.formContentExtra')}
          >
            <Input.TextArea rows={8} placeholder={t('pages.marketing.emailTemplate.phContent')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('pages.marketing.emailTemplate.previewTitle', { subject: previewSubject })}
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
