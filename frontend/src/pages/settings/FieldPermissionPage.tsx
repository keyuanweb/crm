import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Modal, Popconfirm, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  deleteFieldPermission,
  fetchFieldPermissions,
  upsertFieldPermission,
} from '../../services/fieldPermissionService'
import { fetchCustomFields } from '../../services/customFieldService'
import { extractErrorMessage } from '../../services/apiClient'
import { FIELD_ENTITY_LABELS, FIELD_PERMISSION_LABELS, type FieldPermission } from '../../types/fieldPermission'

interface FormValues {
  roleCode: string
  entityType: string
  fieldId: number
  permission: string
}

const ROLE_OPTIONS = ['ADMIN', 'SALES', 'SUPPORT', 'SERVICE'].map((r) => ({ value: r, label: r }))

/** 字段权限配置页（056，仅 ADMIN）。 */
export default function FieldPermissionPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [fieldOptions, setFieldOptions] = useState<{ value: number; label: string }[]>([])
  const [form] = Form.useForm<FormValues>()

  const loadFields = async (entityType: string) => {
    try {
      const res = await fetchCustomFields(entityType, 1, 100)
      setFieldOptions(res.items.map((f) => ({ value: f.id, label: f.name })))
    } catch {
      setFieldOptions([])
    }
  }

  const columns: ProColumns<FieldPermission>[] = [
    { title: t('pages.fieldPermission.colRole'), dataIndex: 'roleCode', valueType: 'select', valueEnum: Object.fromEntries(ROLE_OPTIONS.map((r) => [r, { text: r }])) },
    { title: t('pages.fieldPermission.colEntity'), dataIndex: 'entityType', render: (_, row) => FIELD_ENTITY_LABELS[row.entityType] ?? row.entityType },
    { title: t('pages.fieldPermission.colField'), dataIndex: 'fieldId', search: false },
    {
      title: t('pages.fieldPermission.colPermission'),
      dataIndex: 'permission',
      search: false,
      render: (_, row) => {
        const color = row.permission === 'HIDDEN' ? 'red' : row.permission === 'READ_ONLY' ? 'orange' : 'green'
        return <Tag color={color}>{FIELD_PERMISSION_LABELS[row.permission] ?? row.permission}</Tag>
      },
    },
    {
      title: t('pages.fieldPermission.colAction'),
      valueType: 'option',
      render: (_, row) => [
        <Popconfirm key="del" title={t('pages.fieldPermission.confirmDelete')} onConfirm={() => void onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>{t('pages.fieldPermission.btnDelete')}</a>
        </Popconfirm>,
      ],
    },
  ]

  const onDelete = async (row: FieldPermission) => {
    try {
      await deleteFieldPermission(row.id)
      message.success(t('pages.fieldPermission.msgDeleted'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.fieldPermission.msgDeleteFailed')))
    }
  }

  const onCreate = async () => {
    const values = await form.validateFields()
    try {
      await upsertFieldPermission(values)
      message.success(t('pages.fieldPermission.msgSaved'))
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.fieldPermission.msgSaveFailed')))
    }
  }

  return (
    <>
      <ProTable<FieldPermission>
        headerTitle={t('pages.fieldPermission.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchFieldPermissions(params.roleCode, params.entityType, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.resetFields()
              setModalOpen(true)
            }}
          >
            {t('pages.fieldPermission.btnAdd')}
          </Button>,
        ]}
      />
      <Modal title={t('pages.fieldPermission.modalTitle')} open={modalOpen} onOk={() => void onCreate()} onCancel={() => setModalOpen(false)} okText={t('pages.fieldPermission.btnSave')} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="roleCode" label={t('pages.fieldPermission.formRoleLabel')} rules={[{ required: true, message: t('pages.fieldPermission.formRoleRequired') }]}>
            <Select options={ROLE_OPTIONS} placeholder={t('pages.fieldPermission.formRolePlaceholder')} />
          </Form.Item>
          <Form.Item name="entityType" label={t('pages.fieldPermission.formEntityLabel')} rules={[{ required: true, message: t('pages.fieldPermission.formEntityRequired') }]}>
            <Select
              options={Object.entries(FIELD_ENTITY_LABELS).map(([value, label]) => ({ value, label }))}
              onChange={(v: string) => void loadFields(v)}
              placeholder={t('pages.fieldPermission.formEntityPlaceholder')}
            />
          </Form.Item>
          <Form.Item name="fieldId" label={t('pages.fieldPermission.formFieldLabel')} rules={[{ required: true, message: t('pages.fieldPermission.formFieldRequired') }]}>
            <Select options={fieldOptions} placeholder={t('pages.fieldPermission.formFieldPlaceholder')} />
          </Form.Item>
          <Form.Item name="permission" label={t('pages.fieldPermission.formPermissionLabel')} rules={[{ required: true, message: t('pages.fieldPermission.formPermissionRequired') }]} initialValue="READ_ONLY">
            <Select
              options={Object.entries(FIELD_PERMISSION_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
