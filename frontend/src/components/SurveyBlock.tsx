import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
      message.success(t('pages.surveyBlock.msgSubmitted'))
      onSubmitted()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.surveyBlock.msgSubmitFailed')))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card
      title={
        <span>
          <SmileOutlined style={{ marginRight: 8 }} />
          {t('pages.surveyBlock.title')}
        </span>
      }
      style={{ marginBottom: 16 }}
    >
      {record ? (
        <Descriptions column={{ xs: 1, sm: 2, md: 3 }} size="small">
          <Descriptions.Item label={t('pages.surveyBlock.labelScore')}>
            <Rate disabled value={record.rating} />
            <span style={{ marginLeft: 8 }}>{record.rating} / 5</span>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.surveyBlock.labelSubmitTime')}>
            {record.createdAt.replace('T', ' ').slice(0, 19)}
          </Descriptions.Item>
          {record.comment && (
            <Descriptions.Item label={t('pages.surveyBlock.labelComment')} span={3}>
              {record.comment}
            </Descriptions.Item>
          )}
        </Descriptions>
      ) : (
        <Typography.Text type="secondary">
          {isClosed ? t('pages.surveyBlock.textClosed') : t('pages.surveyBlock.textNotClosed')}
        </Typography.Text>
      )}
      {isClosed && !record && (
        <div style={{ marginTop: 12 }}>
          <Button type="primary" onClick={() => setModalOpen(true)}>
            {t('pages.surveyBlock.btnSubmit')}
          </Button>
        </div>
      )}
      <Modal
        title={t('pages.surveyBlock.modalTitle')}
        open={modalOpen}
        onOk={() => void submit()}
        onCancel={() => {
          setModalOpen(false)
          setComment('')
        }}
        okText={t('pages.surveyBlock.btnSubmitScore')}
        confirmLoading={saving}
        destroyOnClose
        width={420}
      >
        <div style={{ marginBottom: 16 }}>
          <Typography.Text>{t('pages.surveyBlock.labelScoreQuestion')}</Typography.Text>
          <div style={{ marginTop: 8 }}>
            <Rate value={rating} onChange={setRating} />
            <span style={{ marginLeft: 8 }}>{rating} / 5</span>
          </div>
        </div>
        <Input.TextArea
          rows={3}
          maxLength={500}
          placeholder={t('pages.surveyBlock.placeholderComment')}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
      </Modal>
    </Card>
  )
}
