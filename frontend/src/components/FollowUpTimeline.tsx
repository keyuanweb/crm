import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { App, Button, Card, Checkbox, DatePicker, Empty, Form, Input, Modal, Select, Timeline, Typography } from 'antd'
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
  customerId?: number
  leadId?: number
}

interface FormValues {
  method: FollowUpMethod
  content: string
  nextFollowUpAt?: Dayjs
  createTask?: boolean
}

export default function FollowUpTimeline({ customerId, leadId }: Props) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [items, setItems] = useState<FollowUp[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<FollowUp | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<FormValues>()
  const user = useAuthStore((s) => s.user)

  const load = async () => {
    setLoading(true)
    try {
      const res = await fetchFollowUps({ customerId, leadId, page: 1, pageSize: 50 })
      setItems(res.items)
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.followUpTimeline.msgLoadFailed')))
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
      leadId,
      method: values.method,
      content: values.content,
      nextFollowUpAt: values.nextFollowUpAt?.toISOString(),
      createTask: values.createTask ?? false,
      version: editing?.version,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateFollowUp(editing.id, payload)
        message.success(t('pages.followUpTimeline.msgSaved'))
      } else {
        await createFollowUp(payload)
        message.success(t('pages.followUpTimeline.msgAdded'))
      }
      setModalOpen(false)
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.followUpTimeline.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const canEdit = (f: FollowUp) => user?.role === 'ADMIN' || f.followUpBy === user?.id

  return (
    <Card
      title={t('pages.followUpTimeline.title')}
      loading={loading}
      style={{ borderRadius: 10 }}
      headStyle={{ borderBottom: '1px solid #f0f0f0' }}
      extra={
        <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCreate}>
          {t('pages.followUpTimeline.btnAdd')}
        </Button>
      }
    >
      {items.length === 0 ? (
        <Empty description={t('pages.followUpTimeline.empty')} />
      ) : (
        <Timeline
          items={items.map((f) => ({
            key: f.id,
            color: f.method === 'PHONE' ? 'blue' : f.method === 'EMAIL' ? 'green' : 'gray',
            children: (
              <div>
                <Typography.Text strong>{METHOD_LABELS[f.method]}</Typography.Text>{' '}
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {f.followUpByName ?? t('pages.followUpTimeline.unknown')} ·{' '}
                  {f.createdAt ? dayjs(f.createdAt).format('YYYY-MM-DD HH:mm') : ''}
                  {f.nextFollowUpAt
                    ? ` · 下次跟进 ${dayjs(f.nextFollowUpAt).format('YYYY-MM-DD HH:mm')}`
                    : ''}
                </Typography.Text>
                {canEdit(f) && (
                  <a style={{ marginLeft: 8 }} onClick={() => openEdit(f)}>
                    {t('pages.followUpTimeline.btnEdit')}
                  </a>
                )}
                <div style={{ marginTop: 4 }}>{f.content}</div>
              </div>
            ),
          }))}
        />
      )}

      <Modal
        title={editing ? t('pages.followUpTimeline.modalEdit') : t('pages.followUpTimeline.modalAdd')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.followUpTimeline.btnSave')}
        confirmLoading={saving}
        destroyOnClose>
        <Form form={form} name="followUpForm" layout="vertical">
          <Form.Item name="method" label={t('pages.followUpTimeline.labelMethod')} rules={[{ required: true, message: t('pages.followUpTimeline.labelMethod') }]}>
            <Select
              options={Object.entries(METHOD_LABELS).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item name="content" label={t('pages.followUpTimeline.labelContent')} rules={[{ required: true, message: t('pages.followUpTimeline.labelContent') }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="nextFollowUpAt" label={t('pages.followUpTimeline.labelNextFollowUp')}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          {!editing && (
            <Form.Item name="createTask" valuePropName="checked" initialValue={false}>
              <Checkbox>{t('pages.followUpTimeline.checkboxCreateTask')}</Checkbox>
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  )
}
