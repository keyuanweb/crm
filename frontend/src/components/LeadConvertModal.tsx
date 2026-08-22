import { App, Form, Input, InputNumber, Modal } from 'antd'
import { convertLead, type ConvertPayload } from '../services/leadService'
import { extractErrorMessage } from '../services/apiClient'

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
      message.success('转化成功')
      form.resetFields()
      onSuccess()
    } catch (err) {
      message.error(extractErrorMessage(err, '转化失败'))
    }
  }

  return (
    <Modal
      title="转化线索"
      open={open}
      onOk={() => void handleOk()}
      onCancel={() => {
        form.resetFields()
        onCancel()
      }}
      okText="确认转化"
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="opportunityName"
          label="商机名称"
          rules={[{ required: true, message: '请输入商机名称' }]}
        >
          <Input placeholder="请输入商机名称" />
        </Form.Item>
        <Form.Item
          name="expectedAmount"
          label="预期金额（元）"
          rules={[{ required: true, message: '请输入预期金额' }]}
        >
          <InputNumber style={{ width: '100%' }} min={0} placeholder="请输入预期金额" />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} placeholder="可选备注" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
