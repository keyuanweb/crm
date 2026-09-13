import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, InputNumber, Modal, Popconfirm, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createCurrency,
  deleteCurrency,
  fetchCurrencies,
  updateCurrency,
} from '../../services/currencyService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { CurrencyRate } from '../../types/currency'

interface FormValues {
  code: string
  name: string
  rate: number
  enabled: boolean
}

/** 汇率管理页（057，仅 ADMIN）。 */
export default function CurrencyRatePage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<CurrencyRate | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 删除汇率走 DELETE /currencies/{id}，CurrencyRateController 上标的是 currency:manage。
  const can = usePerms([PERMS.currencyManage])

  const columns: ProColumns<CurrencyRate>[] = [
    { title: t('pages.currency.colCode'), dataIndex: 'code', render: (_, row) => <Tag color={row.isBase ? 'gold' : 'blue'}>{row.code}</Tag> },
    { title: t('pages.currency.colName'), dataIndex: 'name' },
    { title: t('pages.currency.colRate'), dataIndex: 'rate', search: false },
    { title: t('pages.currency.colIsBase'), dataIndex: 'isBase', search: false, render: (_, row) => (row.isBase ? <Tag color="gold">{t('pages.currency.isBase')}</Tag> : '-') },
    {
      title: t('pages.currency.colEnabled'),
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">{t('pages.currency.enabled')}</Tag> : <Tag>{t('pages.currency.disabled')}</Tag>),
    },
    {
      title: t('pages.currency.colAction'),
      valueType: 'option',
      render: (_, row) =>
        row.isBase ? (
          <span style={{ color: '#bbb' }}>{t('pages.currency.baseNotEditable')}</span>
        ) : (
          <>
            <a key="edit" onClick={() => openEdit(row)}>
              {t('pages.currency.btnEdit')}
            </a>
            {can[PERMS.currencyManage] && (
              <Popconfirm key="del" title={t('pages.currency.confirmDelete')} onConfirm={() => void onDelete(row)}>
                <a style={{ color: '#ff4d4f', marginLeft: 8 }}>{t('pages.currency.btnDelete')}</a>
              </Popconfirm>
            )}
          </>
        ),
    },
  ]

  const openEdit = (row: CurrencyRate) => {
    setEditing(row)
    form.setFieldsValue({ code: row.code, name: row.name, rate: row.rate, enabled: row.enabled })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload = {
      code: values.code.trim().toUpperCase(),
      name: values.name.trim(),
      rate: values.rate,
      enabled: values.enabled,
    }
    try {
      if (editing) {
        await updateCurrency(editing.id, { ...payload, version: editing.version })
        message.success(t('pages.currency.msgSaved'))
      } else {
        await createCurrency(payload)
        message.success(t('pages.currency.msgCreated'))
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.currency.msgSaveFailed')))
    }
  }

  const onDelete = async (row: CurrencyRate) => {
    try {
      await deleteCurrency(row.id)
      message.success(t('pages.currency.msgDeleted'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.currency.msgDeleteFailed')))
    }
  }

  return (
    <>
      <ProTable<CurrencyRate>
        headerTitle={t('pages.currency.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        request={async () => {
          const items = await fetchCurrencies()
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null)
              form.resetFields()
              form.setFieldsValue({ enabled: true })
              setModalOpen(true)
            }}
          >
            {t('pages.currency.btnAdd')}
          </Button>,
        ]}
      />
      <Modal
        title={editing ? t('pages.currency.modalEditTitle') : t('pages.currency.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.currency.btnSave')}
        destroyOnClose
        width={420}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="code" label={t('pages.currency.formCodeLabel')} rules={[{ required: true, message: t('pages.currency.formCodeRequired') }]}>
            <Input maxLength={10} placeholder={t('pages.currency.formCodePlaceholder')} disabled={!!editing} />
          </Form.Item>
          <Form.Item name="name" label={t('pages.currency.formNameLabel')} rules={[{ required: true, message: t('pages.currency.formNameRequired') }]}>
            <Input maxLength={50} placeholder={t('pages.currency.formNamePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="rate"
            label={t('pages.currency.formRateLabel')}
            rules={[{ required: true, message: t('pages.currency.formRateRequired') }]}
          >
            <InputNumber min={0.000001} precision={6} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="enabled" label={t('pages.currency.formEnabledLabel')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
