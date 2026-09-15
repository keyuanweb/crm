import { useTranslation } from 'react-i18next'
import { App, Form, Input, InputNumber, Modal } from 'antd'
import { convertLead, type ConvertPayload } from '../services/leadService'
import { extractErrorMessage } from '../services/apiClient'
import { FormGrid, VERTICAL_MIN_ITEM_WIDTH } from './ui'

interface Props {
  open: boolean
  leadId?: number
  onCancel: () => void
  onSuccess: () => void
}

interface FormValues {
  opportunityName: string
  expectedAmount: number
  remark?: string
}

export default function LeadConvertModal({ open, leadId, onCancel, onSuccess }: Props) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<FormValues>()

  const handleOk = async () => {
    if (!leadId) return
    const values = await form.validateFields()
    const payload: ConvertPayload = {
      opportunityName: values.opportunityName,
      expectedAmount: values.expectedAmount,
      remark: values.remark,
    }
    try {
      await convertLead(leadId, payload)
      message.success(t('pages.leadConvertModal.msgConverted'))
      form.resetFields()
      onSuccess()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.leadConvertModal.msgConvertFailed')))
    }
  }

  return (
    <Modal
      title={t('pages.leadConvertModal.title')}
      open={open}
      onOk={() => void handleOk()}
      onCancel={() => {
        form.resetFields()
        onCancel()
      }}
      okText={t('pages.leadConvertModal.btnConfirm')}
      destroyOnClose
      width={640}
    >
      <Form form={form} layout="vertical">
        <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
          <Form.Item
            name="opportunityName"
            label={t('pages.leadConvertModal.labelOpportunityName')}
            rules={[{ required: true, message: t('pages.leadConvertModal.labelOpportunityName') }]}
          >
            <Input placeholder={t('pages.leadConvertModal.placeholderOpportunityName')} />
          </Form.Item>
          <Form.Item
            name="expectedAmount"
            label={t('pages.leadConvertModal.labelExpectedAmount')}
            rules={[{ required: true, message: t('pages.leadConvertModal.labelExpectedAmount') }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} placeholder={t('pages.leadConvertModal.placeholderExpectedAmount')} />
          </Form.Item>
        </FormGrid>
        <Form.Item name="remark" label={t('pages.leadConvertModal.labelRemark')}>
          <Input.TextArea rows={2} placeholder={t('pages.leadConvertModal.placeholderRemark')} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
