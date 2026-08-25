import { useEffect, useState } from 'react'
import { App, Button, Card, Checkbox, DatePicker, Form, Space, Switch, TimePicker, Typography } from 'antd'
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { fetchSlaCalendar, updateSlaCalendar } from '../../services/slaCalendarService'
import { extractErrorMessage } from '../../services/apiClient'

interface SlotValue {
  start?: unknown
  end?: unknown
}

/** SLA 日历配置页（054，仅 ADMIN）：工作时间/工作周/节假日。 */
export default function SlaCalendarPage() {
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    void fetchSlaCalendar()
      .then((config) => {
        if (config) {
          form.setFieldsValue({
            enabled: config.enabled,
            workDays: config.workDays,
            workSlots: (config.workSlots ?? []).map((s) => ({
              start: dayjs(s.start, 'HH:mm'),
              end: dayjs(s.end, 'HH:mm'),
            })),
            holidays: (config.holidays ?? []).map((h) => dayjs(h)),
          })
        } else {
          form.setFieldsValue({ enabled: false, workDays: [1, 2, 3, 4, 5], workSlots: [{ start: dayjs('09:00', 'HH:mm'), end: dayjs('18:00', 'HH:mm') }] })
        }
      })
      .catch((err) => message.error(extractErrorMessage(err, '加载失败')))
      .finally(() => setLoading(false))
  }, [form, message])

  const onSave = async () => {
    const values = await form.validateFields()
    const payload = {
      enabled: values.enabled ?? false,
      workDays: values.workDays ?? [],
      workSlots: (values.workSlots ?? []).map((s: SlotValue) => {
        const start = s.start as dayjs.Dayjs
        const end = s.end as dayjs.Dayjs
        return { start: start.format('HH:mm'), end: end.format('HH:mm') }
      }),
      holidays: (values.holidays ?? []).map((h: dayjs.Dayjs) => h.format('YYYY-MM-DD')),
    }
    setSaving(true)
    try {
      await updateSlaCalendar(payload)
      message.success('已保存')
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const weekDays = [
    { label: '周一', value: 1 },
    { label: '周二', value: 2 },
    { label: '周三', value: 3 },
    { label: '周四', value: 4 },
    { label: '周五', value: 5 },
    { label: '周六', value: 6 },
    { label: '周日', value: 7 },
  ]

  return (
    <Card title="SLA 工作日历" style={{ borderRadius: 10 }} loading={loading}>
      <Form form={form} layout="vertical" style={{ maxWidth: 560 }}>
        <Form.Item name="enabled" label="启用工作日历" valuePropName="checked" initialValue={false}>
          <Switch />
        </Form.Item>
        <Typography.Paragraph type="secondary">
          启用后，新工单 SLA 到期时间按工作时间窗口计算（跳过非工作时间与节假日）；未启用回退全天 24h 计算。
        </Typography.Paragraph>
        <Form.Item name="workDays" label="工作周" initialValue={[1, 2, 3, 4, 5]}>
          <Checkbox.Group options={weekDays} />
        </Form.Item>
        <Form.List name="workSlots" initialValue={[{ start: dayjs('09:00', 'HH:mm'), end: dayjs('18:00', 'HH:mm') }]}>
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <Space key={field.key} align="baseline">
                  <Form.Item name={[field.name, 'start']} label="开始">
                    <TimePicker format="HH:mm" />
                  </Form.Item>
                  <Form.Item name={[field.name, 'end']} label="结束">
                    <TimePicker format="HH:mm" />
                  </Form.Item>
                  <MinusCircleOutlined onClick={() => remove(field.name)} />
                </Space>
              ))}
              <Button type="dashed" onClick={() => add({ start: dayjs('09:00', 'HH:mm'), end: dayjs('18:00', 'HH:mm') })} icon={<PlusOutlined />} block>
                添加工作时间段
              </Button>
            </>
          )}
        </Form.List>
        <Form.Item name="holidays" label="节假日">
          <DatePicker multiple maxTagCount={8} />
        </Form.Item>
        <Button type="primary" loading={saving} onClick={() => void onSave()}>
          保存
        </Button>
      </Form>
    </Card>
  )
}
