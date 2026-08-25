import { useEffect, useState } from 'react'
import { App, Button, Card, Descriptions, Input, Modal, Rate, Typography } from 'antd'
import { SmileOutlined } from '@ant-design/icons'
import { extractErrorMessage } from '../services/apiClient'
import { fetchTicketSurvey, submitTicketSurvey } from '../services/ticketSurveyService'
import type { TicketSurvey } from '../types/survey'

interface Props {
  ticketId: number
  /** 工单是否已关闭（仅 CLOSED 可评分）。 */
  isClosed: boolean
  onSubmitted: () => void
}

/** 工单满意度区块（051）：CLOSED 工单可评分，已评分展示记录。 */
export default function SurveyBlock({ ticketId, isClosed, onSubmitted }: Props) {
  const { message } = App.useApp()
  const [record, setRecord] = useState<TicketSurvey | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [rating, setRating] = useState(5)
  const [comment, setComment] = useState('')

  useEffect(() => {
    let cancelled = false
    void fetchTicketSurvey(ticketId)
      .then((r) => {
        if (!cancelled) setRecord(r)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [ticketId])

  const submit = async () => {
    setSaving(true)
    try {
      const rec = await submitTicketSurvey(ticketId, rating, comment)
      setRecord(rec)
      setModalOpen(false)
      setComment('')
      message.success('评分已提交')
      onSubmitted()
    } catch (err) {
      message.error(extractErrorMessage(err, '提交失败'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card
      title={
        <span>
          <SmileOutlined style={{ marginRight: 8 }} />
          满意度调查
        </span>
      }
      style={{ marginBottom: 16, borderRadius: 10 }}
    >
      {record ? (
        <Descriptions column={2} size="small">
          <Descriptions.Item label="评分">
            <Rate disabled value={record.rating} />
            <span style={{ marginLeft: 8 }}>{record.rating} / 5</span>
          </Descriptions.Item>
          <Descriptions.Item label="提交时间">
            {record.createdAt.replace('T', ' ').slice(0, 19)}
          </Descriptions.Item>
          {record.comment && (
            <Descriptions.Item label="评语" span={2}>
              {record.comment}
            </Descriptions.Item>
          )}
        </Descriptions>
      ) : (
        <Typography.Text type="secondary">
          {isClosed ? '该工单已关闭，可为本次服务评分。' : '工单关闭后可提交满意度评分。'}
        </Typography.Text>
      )}
      {isClosed && !record && (
        <div style={{ marginTop: 12 }}>
          <Button type="primary" onClick={() => setModalOpen(true)}>
            提交评分
          </Button>
        </div>
      )}
      <Modal
        title="满意度评分"
        open={modalOpen}
        onOk={() => void submit()}
        onCancel={() => {
          setModalOpen(false)
          setComment('')
        }}
        okText="提交"
        confirmLoading={saving}
        destroyOnClose
        width={420}
      >
        <div style={{ marginBottom: 16 }}>
          <Typography.Text>本次服务评分：</Typography.Text>
          <div style={{ marginTop: 8 }}>
            <Rate value={rating} onChange={setRating} />
            <span style={{ marginLeft: 8 }}>{rating} / 5</span>
          </div>
        </div>
        <Input.TextArea
          rows={3}
          maxLength={500}
          placeholder="评语（可选）"
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
      </Modal>
    </Card>
  )
}
