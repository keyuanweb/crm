import { useEffect, useState } from 'react'
import { App, Button, Card, Checkbox, DatePicker, Form, Space, Switch, TimePicker, Typography } from 'antd'
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { useTranslation } from 'react-i18next'
import { fetchSlaCalendar, updateSlaCalendar } from '../../services/slaCalendarService'
import { extractErrorMessage } from '../../services/apiClient'

interface SlotValue {
  start?: unknown
  end?: unknown
}

/** SLA 日历配置页（054，仅 ADMIN）：工作时间/工作周/节假日。 */
export default function SlaCalendarPage() {
  const { t } = useTranslation()
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
      .catch((err) => message.error(extractErrorMessage(err, t('pages.slaCalendar.msgLoadFailed'))))
      .finally(() => setLoading(false))
  }, [form, message, t])

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
      message.success(t('pages.slaCalendar.msgSaved'))
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.slaCalendar.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const weekDays = [
    { label: t('pages.slaCalendar.weekMon'), value: 1 },
    { label: t('pages.slaCalendar.weekTue'), value: 2 },
    { label: t('pages.slaCalendar.weekWed'), value: 3 },
    { label: t('pages.slaCalendar.weekThu'), value: 4 },
    { label: t('pages.slaCalendar.weekFri'), value: 5 },
    { label: t('pages.slaCalendar.weekSat'), value: 6 },
    { label: t('pages.slaCalendar.weekSun'), value: 7 },
  ]

  return (
    <Card title={t('pages.slaCalendar.title')} style={{ borderRadius: 10 }} loading={loading}>
      <Form form={form} layout="vertical" style={{ maxWidth: 560 }}>
        <Form.Item name="enabled" label={t('pages.slaCalendar.formEnabled')} valuePropName="checked" initialValue={false}>
          <Switch />
        </Form.Item>
        <Typography.Paragraph type="secondary">
          {t('pages.slaCalendar.descEnabled')}
        </Typography.Paragraph>
        <Form.Item name="workDays" label={t('pages.slaCalendar.formWorkDays')} initialValue={[1, 2, 3, 4, 5]}>
          <Checkbox.Group options={weekDays} />
        </Form.Item>
        <Form.List name="workSlots" initialValue={[{ start: dayjs('09:00', 'HH:mm'), end: dayjs('18:00', 'HH:mm') }]}>
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <Space key={field.key} align="baseline">
                  <Form.Item name={[field.name, 'start']} label={t('pages.slaCalendar.formSlotStart')}>
                    <TimePicker format="HH:mm" />
                  </Form.Item>
                  <Form.Item name={[field.name, 'end']} label={t('pages.slaCalendar.formSlotEnd')}>
                    <TimePicker format="HH:mm" />
                  </Form.Item>
                  <MinusCircleOutlined onClick={() => remove(field.name)} />
                </Space>
              ))}
              <Button type="dashed" onClick={() => add({ start: dayjs('09:00', 'HH:mm'), end: dayjs('18:00', 'HH:mm') })} icon={<PlusOutlined />} block>
                {t('pages.slaCalendar.btnAddSlot')}
              </Button>
            </>
          )}
        </Form.List>
        <Form.Item name="holidays" label={t('pages.slaCalendar.formHolidays')}>
          <DatePicker multiple maxTagCount={8} />
        </Form.Item>
        <Button type="primary" loading={saving} onClick={() => void onSave()}>
          {t('common.button.save')}
        </Button>
      </Form>
    </Card>
  )
}
