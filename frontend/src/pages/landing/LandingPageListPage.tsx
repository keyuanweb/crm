import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, ColorPicker, Form, Input, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import {
  createLandingPage,
  deleteLandingPage,
  fetchLandingPages,
  updateLandingPage,
  type LandingPagePayload,
} from '../../services/landingPageService'
import { fetchForms } from '../../services/formService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { LandingPage } from '../../types/landingPage'
import type { Color } from 'antd/es/color-picker'

interface FormValues {
  title: string
  subtitle?: string
  description?: string
  themeColor?: Color
  formId: number
  enabled: boolean
}

/** 落地页配置页（053，营销人员）。 */
export default function LandingPageListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<LandingPage | null>(null)
  const [formOptions, setFormOptions] = useState<{ value: number; label: string }[]>([])
  const [form] = Form.useForm<FormValues>()
  // 086：删除落地页按权限码收口（DELETE /landing-pages/{id} 挂 marketing:manage）。
  const can = usePerms([PERMS.marketingManage])

  const loadForms = async () => {
    const res = await fetchForms()
    setFormOptions(res.map((f) => ({ value: f.id, label: f.name })))
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ enabled: true })
    void loadForms()
    setModalOpen(true)
  }

  const openEdit = (row: LandingPage) => {
    setEditing(row)
    void loadForms()
    form.setFieldsValue({
      title: row.title,
      subtitle: row.subtitle,
      description: row.description,
      formId: row.formId,
      enabled: row.enabled,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: LandingPagePayload = {
      title: values.title.trim(),
      subtitle: values.subtitle,
      description: values.description,
      themeColor: values.themeColor ? values.themeColor.toHexString() : undefined,
      formId: values.formId,
      enabled: values.enabled,
    }
    try {
      if (editing) {
        await updateLandingPage(editing.id, { ...payload, version: editing.version })
        message.success(t('pages.landing.msgSaved'))
      } else {
        await createLandingPage(payload)
        message.success(t('pages.landing.msgCreated'))
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.landing.msgSaveFailed')))
    }
  }

  const columns: ProColumns<LandingPage>[] = [
    {
      title: t('pages.landing.colTitle'),
      dataIndex: 'title',
      render: (_, row) => <Link to={`/lp/${row.id}`} target="_blank">{row.title}</Link>,
    },
    { title: t('pages.landing.colSubtitle'), dataIndex: 'subtitle', search: false },
    { title: t('pages.landing.colFormName'), dataIndex: 'formName', search: false },
    {
      title: t('pages.landing.colStatus'),
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">{t('pages.landing.statusActive')}</Tag> : <Tag>{t('pages.landing.statusInactive')}</Tag>),
    },
    {
      title: t('pages.landing.colAction'),
      valueType: 'option',
      width: 180,
      render: (_, row) => [
        <a key="stats" onClick={() => openStats(row.id)}>
          {t('pages.landing.btnStats')}
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.landing.btnEdit')}
        </a>,
        can[PERMS.marketingManage] ? (
          <Popconfirm key="del" title={t('pages.landing.confirmDelete')} onConfirm={() => void onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('pages.landing.btnDelete')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  const onDelete = async (row: LandingPage) => {
    try {
      await deleteLandingPage(row.id)
      message.success(t('pages.landing.msgDeleted'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.landing.msgDeleteFailed')))
    }
  }

  const openStats = async (id: number) => {
    // 简化：跳转统计对话框（此处仅提示，完整统计见页面扩展）
    message.info(t('pages.landing.msgStatsInfo', { id }))
  }

  return (
    <>
      <ProTable<LandingPage>
        headerTitle={t('pages.landing.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchLandingPages(params.keyword, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.landing.btnCreate')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.landing.modalEdit') : t('pages.landing.modalCreate')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.landing.btnSave')}
        destroyOnClose
        width={520}
      >
        <Form form={form} name="landingPageForm" layout="vertical">
          <Form.Item name="title" label={t('pages.landing.formTitle')} rules={[{ required: true, message: t('pages.landing.msgTitleRequired') }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="subtitle" label={t('pages.landing.formSubtitle')}>
            <Input maxLength={500} />
          </Form.Item>
          <Form.Item name="description" label={t('pages.landing.formDescription')}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="themeColor" label={t('pages.landing.formThemeColor')}>
            <ColorPicker showText />
          </Form.Item>
          <Form.Item name="formId" label={t('pages.landing.formFormId')} rules={[{ required: true, message: t('pages.landing.msgFormRequired') }]}>
            <Select options={formOptions} placeholder={t('pages.landing.formFormIdPlaceholder')} />
          </Form.Item>
          <Form.Item name="enabled" label={t('pages.landing.formEnabled')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
