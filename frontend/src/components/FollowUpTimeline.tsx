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
import { type FollowUp, type FollowUpMethod } from '../types/followUp'
import { ENUM_KEYS, labelOf } from '../constants/enumLabels'
import { useAuthStore } from '../store/authStore'
import { hasPerm } from '../hooks/usePermission'
import { PERMS } from '../constants/permissions'
import AiFollowUpPolishButton from './AiFollowUpPolishButton'
import { FormGrid, VERTICAL_MIN_ITEM_WIDTH } from './ui'

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
  /**
   * 表单里**当前**的跟进原文（P3 的出站输入）。
   *
   * <p>⚠️ 用 `Form.useWatch` 而不是在 `openEdit`/`onChange` 里存一份 state：后者要自己在两条路径上
   * 同步（打开编辑时填入、用户每次敲键时更新），而**漏掉任何一条**都会让润色拿到的是一份旧的原文
   * ——那种错不会报任何错，只会让用户觉得"它没按我写的润色"。
   */
  const contentValue = Form.useWatch('content', form)
  // 权限门与客户详情页上那两个 AI 按钮同源（同一个 `ai:generate`）。见下方 Form.Item 的注释。
  const canGenerateAiText = hasPerm(PERMS.aiGenerate, user)

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
                <Typography.Text strong>{labelOf(t, ENUM_KEYS.followUpMethod, f.method)}</Typography.Text>{' '}
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
        destroyOnClose
        width={640}
      >
        <Form form={form} name="followUpForm" layout="vertical">
          <Form.Item name="method" label={t('pages.followUpTimeline.labelMethod')} rules={[{ required: true, message: t('pages.followUpTimeline.labelMethod') }]}>
            <Select
              options={Object.keys(ENUM_KEYS.followUpMethod).map((code) => ({
                value: code,
                label: labelOf(t, ENUM_KEYS.followUpMethod, code),
              }))}
            />
          </Form.Item>
          <Form.Item
            name="content"
            label={t('pages.followUpTimeline.labelContent')}
            rules={[{ required: true, message: t('pages.followUpTimeline.labelContent') }]}
            extra={
              // 104 P3（跟进润色 / 总结）。三处判据在这里落地：
              // ① **权限门**：与客户详情页上那两个 AI 按钮同一个 `ai:generate`（三个端点同一笔花费、
              //    同一个日预算桶，见 `AiCustomerSummaryButton` 的 ⚠️）。不持码的用户**看不到**这个按钮，
              //    而不是点了再收一条 403——后端的 403 仍是兜底。
              // ② **原文来自这里**（`Form.useWatch` 读的是表单当前值，不是快照）：用户边写边改，按钮
              //    拿到的是他此刻写的那段。故本组件不持有第二份副本。
              // ③ **结果写回表单**：`onGenerated` 把模型返回的**原始**文本写进 content 字段——退路是
              //    "不点保存"（本能力零新增表、草稿不落库：那段原文在保存之前只存在于这个表单里）。
              //    ⚠️ 这是**覆盖式**的：用户在弹窗里看到的是模型版，写回后表单里那份被替换。之所以仍
              //    这么接：润色的意义就是把这段字换掉，而"关掉弹窗不保存"是个完整的退路。
              canGenerateAiText ? (
                <AiFollowUpPolishButton
                  content={contentValue ?? ''}
                  customerId={customerId}
                  onGenerated={(text) => form.setFieldValue('content', text)}
                />
              ) : null
            }
          >
            <Input.TextArea rows={3} />
          </Form.Item>
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
            <Form.Item name="nextFollowUpAt" label={t('pages.followUpTimeline.labelNextFollowUp')}>
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
            {!editing && (
              <Form.Item name="createTask" valuePropName="checked" initialValue={false}>
                <Checkbox>{t('pages.followUpTimeline.checkboxCreateTask')}</Checkbox>
              </Form.Item>
            )}
          </FormGrid>
        </Form>
      </Modal>
    </Card>
  )
}
