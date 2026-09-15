import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Select, Tag } from 'antd'
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
import { formatAmount } from '../../types/opportunity'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { FormGrid, useFormMetrics } from '../../components/ui'

interface FormValues {
  name: string
  channel: CampaignChannel
  budget?: number
  cost?: number
  startDate?: dayjs.Dayjs
  endDate?: dayjs.Dayjs
}

/*
  金额的**分 ↔ 元**边界。

  后端 `marketing_campaign.budget`/`cost` 是分（`V32__marketing_campaign.sql:9-10` 的列注释写着
  「预算（分）」「成本（分）」），而用户脑子里和输入框里都是元。本页原先两端都不换算：
  表里 500 元显示成 `50000`，编辑时又把 `50000` 填进"元"的框里，保存时原样回传——
  这笔账**只在用户没碰过金额字段时才碰巧对**，一旦动手改就成了 100 倍的静默金额错误。

  三处换算：显示用仓库既有的 `formatAmount`（`types/opportunity.ts:72`）；回填与提交按仓库
  通行写法内联（`ContractListPage.tsx:104`、`OrderListPage.tsx:116` 都是
  `x === undefined ? undefined : Math.round(x * 100)`），不另造 helper。
*/

export default function CampaignListPage() {
  const { t } = useTranslation()
  const metrics = useFormMetrics()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<MarketingCampaign | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 按权限码判断（1.5 批 3 起 MarketingController 的写挂 campaign:*，此前是 hasAnyRole('ADMIN','SALES')）。
  // 三个码同批补授给了 SALES（判据①：不补就等于把销售建活动打成 403），另有 MARKETING_* 早已持有；
  // 持有「营销活动」菜单却不持有这些码的角色（如 SALES_MANAGER）从此不再看到必然 403 的按钮。
  const can = usePerms([PERMS.campaignCreate, PERMS.campaignUpdate, PERMS.campaignDelete])

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
      // 分 → 元（表单里填的是元）。与下面的提交是同一个边界的另一半，两处必须成对存在：
      // 只转一处会让同一页上有两个"预算"，且编辑一次就把金额改掉 100 倍。
      budget: row.budget == null ? undefined : row.budget / 100,
      cost: row.cost == null ? undefined : row.cost / 100,
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
      // 元 → 分。`Math.round` 是必需的：`199.99 * 100` 在浮点下是 `19998.999…`。
      budget: values.budget == null ? undefined : Math.round(values.budget * 100),
      cost: values.cost == null ? undefined : Math.round(values.cost * 100),
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
    {
      title: t('pages.marketing.campaign.colBudget'),
      dataIndex: 'budget',
      search: false,
      render: (_, row) => formatAmount(row.budget),
    },
    {
      title: t('pages.marketing.campaign.colCost'),
      dataIndex: 'cost',
      search: false,
      render: (_, row) => formatAmount(row.cost),
    },
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
        can[PERMS.campaignUpdate] && row.status === 'PLANNING' && (
          <a key="start" onClick={() => onStart(row)}>
            {t('pages.marketing.campaign.btnStart')}
          </a>
        ),
        can[PERMS.campaignUpdate] && row.status === 'RUNNING' && (
          <a key="end" onClick={() => onEnd(row)}>
            {t('pages.marketing.campaign.btnEnd')}
          </a>
        ),
        can[PERMS.campaignUpdate] && row.status !== 'ENDED' && (
          <a key="edit" onClick={() => openEdit(row)}>
            {t('pages.marketing.campaign.btnEdit')}
          </a>
        ),
        can[PERMS.campaignDelete] && (
          <Popconfirm
            key="delete"
            title={t('pages.marketing.campaign.confirmDelete', { name: row.name })}
            onConfirm={() => onDelete(row)}
          >
            <a style={{ color: '#ff4d4f' }}>{t('pages.marketing.campaign.btnDelete')}</a>
          </Popconfirm>
        ),
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
          // 渠道 ROI 是读（GET /campaigns/channel-roi 不设码），所以只有「新建」按码收。
          ...(can[PERMS.campaignCreate]
            ? [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  {t('pages.marketing.campaign.btnCreate')}
                </Button>,
              ]
            : []),
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
          labelCol={{ flex: `${metrics.labelWidth}px` }}
          wrapperCol={{ flex: 1 }}
        >
          {/* 6 个成对字段进栅格（720px 弹窗里可用宽 ≥ 2 × 256 ⇒ 两列，窄了自动退一列）。
              原先的 `<Row gutter={16}>` + 6 个 `<Col span={12}>` 是**写死两列、无任何断点**的。 */}
          <FormGrid>
            <Form.Item
              name="name"
              label={t('pages.marketing.campaign.formName')}
              rules={[{ required: true, message: t('pages.marketing.campaign.msgNameRequired') }]}
            >
              <Input maxLength={100} />
            </Form.Item>
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
            <Form.Item name="budget" label={t('pages.marketing.campaign.formBudget')}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="cost" label={t('pages.marketing.campaign.formCost')}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="startDate" label={t('pages.marketing.campaign.formStartDate')}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="endDate" label={t('pages.marketing.campaign.formEndDate')}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </FormGrid>
        </Form>
      </Modal>
    </>
  )
}
