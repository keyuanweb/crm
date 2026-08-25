import { useRef, useState } from 'react'
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
import type { CurrencyRate } from '../../types/currency'

interface FormValues {
  code: string
  name: string
  rate: number
  enabled: boolean
}

/** 汇率管理页（057，仅 ADMIN）。 */
export default function CurrencyRatePage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<CurrencyRate | null>(null)
  const [form] = Form.useForm<FormValues>()

  const columns: ProColumns<CurrencyRate>[] = [
    { title: '代码', dataIndex: 'code', render: (_, row) => <Tag color={row.isBase ? 'gold' : 'blue'}>{row.code}</Tag> },
    { title: '名称', dataIndex: 'name' },
    { title: '汇率（对人民币）', dataIndex: 'rate', search: false },
    { title: '基准', dataIndex: 'isBase', search: false, render: (_, row) => (row.isBase ? <Tag color="gold">基准</Tag> : '-') },
    {
      title: '启用',
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, row) =>
        row.isBase ? (
          <span style={{ color: '#bbb' }}>基准不可编辑</span>
        ) : (
          <>
            <a key="edit" onClick={() => openEdit(row)}>
              编辑
            </a>
            <Popconfirm key="del" title="删除该币种？" onConfirm={() => void onDelete(row)}>
              <a style={{ color: '#ff4d4f', marginLeft: 8 }}>删除</a>
            </Popconfirm>
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
        message.success('已保存')
      } else {
        await createCurrency(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: CurrencyRate) => {
    try {
      await deleteCurrency(row.id)
      message.success('已删除')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  return (
    <>
      <ProTable<CurrencyRate>
        headerTitle="币种与汇率"
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
            新增币种
          </Button>,
        ]}
      />
      <Modal
        title={editing ? '编辑币种' : '新增币种'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={420}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="code" label="币种代码" rules={[{ required: true, message: '请输入代码' }]}>
            <Input maxLength={10} placeholder="如 USD" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="name" label="币种名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={50} placeholder="如 美元" />
          </Form.Item>
          <Form.Item
            name="rate"
            label="汇率（1 单位该币种 = 多少人民币）"
            rules={[{ required: true, message: '请输入汇率' }]}
          >
            <InputNumber min={0.000001} precision={6} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
