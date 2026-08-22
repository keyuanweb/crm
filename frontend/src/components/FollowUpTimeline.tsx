import { useState } from 'react'
import { App, Button, Card, DatePicker, Empty, Form, Input, Modal, Select, Timeline, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import {
  createFollowUp,
  fetchFollowUps,
  updateFollowUp,
  type FollowUpPayload,
} from '../services/followUpService'
import { extractErrorMessage } from '../services/apiClient'
import { METHOD_LABELS, type FollowUp, type FollowUpMethod } from '../types/followUp'
import { useAuthStore } from '../store/authStore'

interface Props {
  customerId: number
}

interface FormValues {
  method: FollowUpMethod
  content: string
  nextFollowUpAt?: Dayjs
}

export default function FollowUpTimeline({ customerId }: Props) {
  const { message } = App.useApp()
  const [items, setItems] = useState<FollowUp[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<FollowUp | null>(null)
  const [form] = Form.useForm<FormValues>()
  const user = useAuthStore((s) => s.user)

  const load = async () => {
    setLoading(true)
    try {
      const res = await fetchFollowUps({ customerId, page: 1, pageSize: 50 })
      setItems(res.items)
    } catch (err) {
      message.error(extractErrorMessage(err, '加载跟进记录失败'))
    } finally {
      setLoading(false)
    }
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
    void load()
  }

  const openEdit = (f: FollowUp) => {
    setEditing(f)
    form.setFieldsValue({
      method: f.method,
      content: f.content,
      nextFollowUpAt: f.nextFollowUpAt ? dayjs(f.nextFollowUpAt) : undefined,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: FollowUpPayload = {
      customerId,
      method: values.method,
      content: values.content,
      nextFollowUpAt: values.nextFollowUpAt?.toISOString(),
      version: editing?.version,
    }
    try {
      if (editing) {
        await updateFollowUp(editing.id, payload)
        message.success('已保存')
      } else {
        await createFollowUp(payload)
        message.success('已添加')
      }
      setModalOpen(false)
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const canEdit = (f: FollowUp) => user?.role === 'ADMIN' || f.followUpBy === user?.id

  return (
    <Card
      title="跟进记录"
      loading={loading}
      extra={
        <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCreate}>
          添加跟进
        </Button>
      }
    >
      {items.length === 0 ? (
        <Empty description="暂无跟进记录" />
      ) : (
        <Timeline
          items={items.map((f) => ({
            key: f.id,
            color: f.method === 'PHONE' ? 'blue' : f.method === 'EMAIL' ? 'green' : 'gray',
            children: (
              <div>
                <Typography.Text strong>{METHOD_LABELS[f.method]}</Typography.Text>{' '}
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {f.followUpByName ?? '未知'} ·{' '}
                  {f.createdAt ? dayjs(f.createdAt).format('YYYY-MM-DD HH:mm') : ''}
                  {f.nextFollowUpAt
                    ? ` · 下次跟进 ${dayjs(f.nextFollowUpAt).format('YYYY-MM-DD HH:mm')}`
                    : ''}
                </Typography.Text>
                {canEdit(f) && (
                  <a style={{ marginLeft: 8 }} onClick={() => openEdit(f)}>
                    编辑
                  </a>
                )}
                <div style={{ marginTop: 4 }}>{f.content}</div>
              </div>
            ),
          }))}
        />
      )}

      <Modal
        title={editing ? '编辑跟进记录' : '添加跟进记录'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存" destroyOnClose>
        <Form form={form} name="followUpForm" layout="vertical">
          <Form.Item name="method" label="方式" rules={[{ required: true, message: '请选择方式' }]}>
            <Select
              options={Object.entries(METHOD_LABELS).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item name="content" label="内容" rules={[{ required: true, message: '请输入内容' }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="nextFollowUpAt" label="下次跟进">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
