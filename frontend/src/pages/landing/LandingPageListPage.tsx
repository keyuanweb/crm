import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, ColorPicker, Form, Input, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createLandingPage,
  deleteLandingPage,
  fetchLandingPages,
  updateLandingPage,
  type LandingPagePayload,
} from '../../services/landingPageService'
import { fetchForms } from '../../services/formService'
import { extractErrorMessage } from '../../services/apiClient'
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
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<LandingPage | null>(null)
  const [formOptions, setFormOptions] = useState<{ value: number; label: string }[]>([])
  const [form] = Form.useForm<FormValues>()

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
        message.success('已保存')
      } else {
        await createLandingPage(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const columns: ProColumns<LandingPage>[] = [
    {
      title: '标题',
      dataIndex: 'title',
      render: (_, row) => <Link to={`/lp/${row.id}`} target="_blank">{row.title}</Link>,
    },
    { title: '副标题', dataIndex: 'subtitle', search: false },
    { title: '关联表单', dataIndex: 'formName', search: false },
    {
      title: '状态',
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      render: (_, row) => [
        <a key="stats" onClick={() => openStats(row.id)}>
          统计
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm key="del" title="确定删除？" onConfirm={() => void onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  const onDelete = async (row: LandingPage) => {
    try {
      await deleteLandingPage(row.id)
      message.success('已删除')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const openStats = async (id: number) => {
    // 简化：跳转统计对话框（此处仅提示，完整统计见页面扩展）
    message.info(`落地页 #${id} 统计请见营销报表（后续扩展）`)
  }

  return (
    <>
      <ProTable<LandingPage>
        headerTitle="托管落地页"
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
            新建落地页
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑落地页' : '新建落地页'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={520}
      >
        <Form form={form} name="landingPageForm" layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="subtitle" label="副标题">
            <Input maxLength={500} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="themeColor" label="品牌色">
            <ColorPicker showText />
          </Form.Item>
          <Form.Item name="formId" label="关联表单" rules={[{ required: true, message: '请选择表单' }]}>
            <Select options={formOptions} placeholder="选择启用中的在线表单" />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
