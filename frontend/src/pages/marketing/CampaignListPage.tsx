import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Col, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  createCampaign,
  deleteCampaign,
  endCampaign,
  fetchCampaigns,
  startCampaign,
  updateCampaign,
  type CampaignPayload,
} from '../../services/marketingService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  CAMPAIGN_STATUS_COLORS,
  type CampaignChannel,
  type CampaignStatus,
  type MarketingCampaign,
} from '../../types/marketing'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'

interface FormValues {
  name: string
  channel: CampaignChannel
  budget?: number
  cost?: number
  startDate?: dayjs.Dayjs
  endDate?: dayjs.Dayjs
}

export default function CampaignListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<MarketingCampaign | null>(null)
  const [form] = Form.useForm<FormValues>()

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: MarketingCampaign) => {
    setEditing(row)
    form.setFieldsValue({
      name: row.name,
      channel: row.channel,
      budget: row.budget,
      cost: row.cost,
      startDate: row.startDate ? dayjs(row.startDate) : undefined,
      endDate: row.endDate ? dayjs(row.endDate) : undefined,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: CampaignPayload = {
      name: values.name.trim(),
      channel: values.channel,
      budget: values.budget,
      cost: values.cost,
      startDate: values.startDate ? values.startDate.format('YYYY-MM-DD') : undefined,
      endDate: values.endDate ? values.endDate.format('YYYY-MM-DD') : undefined,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateCampaign(editing.id, { ...payload, version: editing.version })
        message.success(t('pages.marketing.campaign.msgSaved'))
      } else {
        await createCampaign(payload)
        message.success(t('pages.marketing.campaign.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.marketing.campaign.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onStart = async (row: MarketingCampaign) => {
    try {
      await startCampaign(row.id)
      message.success(t('pages.marketing.campaign.msgStarted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.marketing.campaign.msgOperationFailed')))
    }
  }

  const onEnd = async (row: MarketingCampaign) => {
    try {
      await endCampaign(row.id)
      message.success(t('pages.marketing.campaign.msgEnded'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.marketing.campaign.msgOperationFailed')))
    }
  }

  const onDelete = async (row: MarketingCampaign) => {
    try {
      await deleteCampaign(row.id)
      message.success(t('pages.marketing.campaign.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.marketing.campaign.msgDeleteFailed')))
    }
  }

  const columns: ProColumns<MarketingCampaign>[] = [
    { title: t('pages.marketing.campaign.colName'), dataIndex: 'name' },
    {
      title: t('pages.marketing.campaign.colChannel'),
      dataIndex: 'channel',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.campaignChannel).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.campaignChannel, code) },
        ]),
      ),
      render: (_, row) => <Tag color="blue">{labelOf(t, ENUM_KEYS.campaignChannel, row.channel)}</Tag>,
    },
    {
      title: t('pages.marketing.campaign.colStatus'),
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.campaignStatus).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.campaignStatus, code), status: CAMPAIGN_STATUS_COLORS[code as CampaignStatus] },
        ]),
      ),
    },
    { title: t('pages.marketing.campaign.colBudget'), dataIndex: 'budget', search: false },
    { title: t('pages.marketing.campaign.colCost'), dataIndex: 'cost', search: false },
    {
      title: t('pages.marketing.campaign.colAttributionLeads'),
      dataIndex: 'leadCount',
      search: false,
      render: (_, row) => row.leadCount ?? 0,
    },
    {
      title: t('pages.marketing.campaign.colAttributionCustomers'),
      dataIndex: 'customerCount',
      search: false,
      render: (_, row) => row.customerCount ?? 0,
    },
    {
      title: t('pages.marketing.campaign.colStartDate'),
      dataIndex: 'startDate',
      search: false,
      valueType: 'date',
    },
    {
      title: t('pages.marketing.campaign.colEndDate'),
      dataIndex: 'endDate',
      search: false,
      valueType: 'date',
    },
    {
      title: t('pages.marketing.campaign.colAction'),
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        row.status === 'PLANNING' && (
          <a key="start" onClick={() => onStart(row)}>
            {t('pages.marketing.campaign.btnStart')}
          </a>
        ),
        row.status === 'RUNNING' && (
          <a key="end" onClick={() => onEnd(row)}>
            {t('pages.marketing.campaign.btnEnd')}
          </a>
        ),
        row.status !== 'ENDED' && (
          <a key="edit" onClick={() => openEdit(row)}>
            {t('pages.marketing.campaign.btnEdit')}
          </a>
        ),
        <Popconfirm
          key="delete"
          title={t('pages.marketing.campaign.confirmDelete', { name: row.name })}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>{t('pages.marketing.campaign.btnDelete')}</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<MarketingCampaign>
        size="small"
        headerTitle={t('pages.marketing.campaign.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchCampaigns({
            keyword: params.keyword,
            channel: params.channel,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Link key="roi" to="/marketing/roi">
            <Button>{t('pages.marketing.campaign.btnRoi')}</Button>
          </Link>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.marketing.campaign.btnCreate')}
          </Button>,
        ]}
      />

      <Modal
        title={
          editing
            ? t('pages.marketing.campaign.modalEditTitle')
            : t('pages.marketing.campaign.modalCreateTitle')
        }
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.marketing.campaign.btnSave')}
        destroyOnClose
        width={640}
      >
        <Form
          form={form}
          name="campaignForm"
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="name"
                label={t('pages.marketing.campaign.formName')}
                rules={[{ required: true, message: t('pages.marketing.campaign.msgNameRequired') }]}
              >
                <Input maxLength={100} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="channel"
                label={t('pages.marketing.campaign.formChannel')}
                rules={[{ required: true, message: t('pages.marketing.campaign.msgChannelRequired') }]}
              >
                <Select
                  options={Object.keys(ENUM_KEYS.campaignChannel).map((code) => ({
                    value: code,
                    label: labelOf(t, ENUM_KEYS.campaignChannel, code),
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="budget" label={t('pages.marketing.campaign.formBudget')}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="cost" label={t('pages.marketing.campaign.formCost')}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="startDate" label={t('pages.marketing.campaign.formStartDate')}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="endDate" label={t('pages.marketing.campaign.formEndDate')}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
