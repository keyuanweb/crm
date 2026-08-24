import { useEffect, useState } from 'react'
import { App, Button, Card, Descriptions, Image, Modal, Typography } from 'antd'
import { FileDoneOutlined } from '@ant-design/icons'
import SignaturePad from './SignaturePad'
import { extractErrorMessage } from '../services/apiClient'
import type { SignatureRecord } from '../types/signature'

interface Props {
  businessType: 'QUOTE' | 'CONTRACT'
  businessId: number
  /** 当前状态是否允许签署（如 APPROVED）。 */
  canSign: boolean
  signFn: (id: number, base64: string) => Promise<SignatureRecord>
  fetchFn: (id: number) => Promise<SignatureRecord | null>
  /** 签署成功后刷新单据。 */
  onSigned: () => void
}

/** 电子签署区块（047）：未签署时可发起；已签署展示记录。 */
export default function SignSection({
  businessType,
  businessId,
  canSign,
  signFn,
  fetchFn,
  onSigned,
}: Props) {
  const { message } = App.useApp()
  const [record, setRecord] = useState<SignatureRecord | null>(null)
  const [signOpen, setSignOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [signature, setSignature] = useState<string | null>(null)

  // 加载签署记录
  useEffect(() => {
    let cancelled = false
    void fetchFn(businessId)
      .then((r) => {
        if (!cancelled) setRecord(r)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [businessId])

  const submitSign = async () => {
    if (!signature) {
      message.warning('请先绘制或上传签名')
      return
    }
    setSaving(true)
    try {
      const rec = await signFn(businessId, signature)
      setRecord(rec)
      setSignOpen(false)
      message.success('签署成功')
      onSigned()
    } catch (err) {
      message.error(extractErrorMessage(err, '签署失败'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card
      title={
        <span>
          <FileDoneOutlined style={{ marginRight: 8 }} />
          电子签署
        </span>
      }
      style={{ borderRadius: 10 }}
    >
      {record ? (
        <Descriptions column={2} size="small">
          <Descriptions.Item label="签署人">{record.signerName ?? `用户#${record.signerId}`}</Descriptions.Item>
          <Descriptions.Item label="签署时间">
            {record.signedAt.replace('T', ' ').slice(0, 19)}
          </Descriptions.Item>
          <Descriptions.Item label="签名图" span={2}>
            <Image
              src={record.signatureImage}
              alt="签名"
              width={160}
              style={{ border: '1px solid #f0f0f0', borderRadius: 4, padding: 4 }}
            />
          </Descriptions.Item>
        </Descriptions>
      ) : (
        <Typography.Text type="secondary">
          该{businessType === 'QUOTE' ? '报价单' : '合同'}尚未签署。
          {canSign ? '审批通过后可由内部或客户确认签署。' : '仅审批通过后可发起签署。'}
        </Typography.Text>
      )}
      {canSign && !record && (
        <div style={{ marginTop: 12 }}>
          <Button type="primary" onClick={() => setSignOpen(true)}>
            发起签署
          </Button>
        </div>
      )}
      <Modal
        title="电子签署"
        open={signOpen}
        onOk={() => void submitSign()}
        onCancel={() => {
          setSignOpen(false)
          setSignature(null)
        }}
        okText="确认签署"
        confirmLoading={saving}
        destroyOnClose
        width={480}
      >
        <SignaturePad onChange={setSignature} />
      </Modal>
    </Card>
  )
}
