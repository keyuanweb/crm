import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
        message.success(t('pages.contractTemplate.msgSaved'))
      } else {
        await createContractTemplate(payload)
        message.success(t('pages.contractTemplate.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.contractTemplate.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: ContractTemplate) => {
    try {
      await deleteContractTemplate(row.id)
      message.success(t('pages.contractTemplate.msgDisabled'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.contractTemplate.msgDeleteFailed')))
    }
  }

  const columns: ProColumns<ContractTemplate>[] = [
    { title: t('pages.contractTemplate.colName'), dataIndex: 'name' },
    {
      title: t('pages.contractTemplate.colContent'),
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
      title: t('pages.contractTemplate.colStatus'),
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: {
        ACTIVE: { text: t('pages.contractTemplate.enabled') },
        INACTIVE: { text: t('pages.contractTemplate.disabled') },
      },
      render: (_, row) =>
        row.status === 'ACTIVE' ? <Tag color="green">{t('pages.contractTemplate.enabled')}</Tag> : <Tag>{t('pages.contractTemplate.disabled')}</Tag>,
    },
    {
      title: t('pages.contractTemplate.colAction'),
      valueType: 'option',
      width: 140,
      render: (_, row) =>
        isAdmin
          ? [
              <a key="edit" onClick={() => openEdit(row)}>
                <EditOutlined /> {t('pages.contractTemplate.edit')}
              </a>,
              <Popconfirm key="delete" title={t('pages.contractTemplate.confirmDelete', { name: row.name })} onConfirm={() => onDelete(row)}>
                <a style={{ color: '#ff4d4f' }}>
                  <DeleteOutlined /> {t('pages.contractTemplate.disable')}
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
        headerTitle={t('pages.contractTemplate.title')}
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
                  {t('pages.contractTemplate.btnAdd')}
                </Button>,
              ]
            : []
        }
      />

      <Modal
        title={editing ? t('pages.contractTemplate.modalEditTitle') : t('pages.contractTemplate.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.contractTemplate.btnSave')}
        destroyOnClose
        width={680}
      >
        <Form form={form} name="contractTemplateForm" layout="vertical">
          <Form.Item name="name" label={t('pages.contractTemplate.formNameLabel')} rules={[{ required: true, message: t('pages.contractTemplate.formNameRequired') }]} style={{ flex: 1 }}>
            <Input />
          </Form.Item>
          <Form.Item name="status" label={t('pages.contractTemplate.formStatusLabel')} style={{ flex: 1 }}>
            <Select
              options={[
                { value: 'ACTIVE', label: t('pages.contractTemplate.enabled') },
                { value: 'INACTIVE', label: t('pages.contractTemplate.disabled') },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="content"
            label={t('pages.contractTemplate.formContentLabel')}
            extra={t('pages.contractTemplate.formContentExtra')}
            rules={[{ required: true, message: t('pages.contractTemplate.formContentRequired') }]}
          >
            <Input.TextArea rows={8} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
