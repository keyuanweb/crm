import { useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  App,
  Button,
  DatePicker,
  Form,
  InputNumber,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Tag,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  closeSalesOpportunity,
  createSalesOpportunity,
  fetchOpportunities,
  fetchSalesOpportunities,
  type SalesOpportunityPayload,
} from '../../services/opportunityService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { formatAmount, type OpportunityStage, type SalesOpportunity } from '../../types/opportunity'
import { useOpportunityStages } from '../../hooks/useOpportunityStages'
import OpportunityBoard from './OpportunityBoard'
import { useQuery } from '@tanstack/react-query'
import type { Dayjs } from 'dayjs'
import { FormGrid, useFormMetrics } from '../../components/ui'

interface FormValues {
  opportunityId: number
  amount?: number
  stage: OpportunityStage
  expectedCloseDate?: Dayjs
}

export default function SalesOpportunityListPage() {
  const { t } = useTranslation()
  const metrics = useFormMetrics()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [view, setView] = useState<'list' | 'board'>('list')
  const [form] = Form.useForm<FormValues>()
  const { stages, selectableStages, stageLabel, isTerminal } = useOpportunityStages()
  // 赢单/输单打的是 POST /sales-opportunities/{id}/close，挂 opportunity:update（商机终态不可逆，收口）。
  // 看板拖拽改阶段打的是同一个 PUT /sales-opportunities/{id}（同码），但**刻意不收**——
  // 拖拽可逆、且是销售最高频操作，挂判据只添堵（086 决策 7）。
  const can = usePerms([PERMS.opportunityUpdate])

  /** 编码 → 阶段定义。用 Map 而不是每格 `stages.find`：列表一页 20 行 × 每行一次线性查找没必要。 */
  const stageByCode = useMemo(() => new Map(stages.map((s) => [s.code, s])), [stages])

  /** 阶段标签颜色由 stage_type 决定，而不是写死一张编码表——自建阶段也要有颜色。 */
  const stageColor = (code: string) => {
    const type = stageByCode.get(code)?.stageType
    if (type === 'WON') return 'success'
    if (type === 'LOST') return 'error'
    return 'processing'
  }

  const opportunities = useQuery({
    queryKey: ['opportunities-options'],
    queryFn: () => fetchOpportunities({ page: 1, pageSize: 100 }),
  })

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    form.resetFields()
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: SalesOpportunityPayload = {
      opportunityId: values.opportunityId,
      amount: values.amount ? values.amount * 100 : undefined,
      stage: values.stage,
      expectedCloseDate: values.expectedCloseDate?.format('YYYY-MM-DD'),
    }
    setSaving(true)
    try {
      await createSalesOpportunity(payload)
      message.success(t('pages.salesOpportunity.msgCreated'))
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.salesOpportunity.msgCreateFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onClose = async (row: SalesOpportunity, result: 'WON' | 'LOST') => {
    try {
      await closeSalesOpportunity(row.id, result, row.version)
      message.success(result === 'WON' ? t('pages.salesOpportunity.msgWon') : t('pages.salesOpportunity.msgLost'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.salesOpportunity.msgCloseFailed')))
    }
  }

  const columns: ProColumns<SalesOpportunity>[] = [
    { title: t('pages.salesOpportunity.colOpportunityName'), dataIndex: 'opportunityName', search: false },
    { title: t('pages.salesOpportunity.colCustomerName'), dataIndex: 'customerName', search: false },
    {
      title: t('pages.salesOpportunity.colAmountYuan'),
      dataIndex: 'amount',
      search: false,
      render: (_, row) => formatAmount(row.amount),
    },
    {
      title: t('pages.salesOpportunity.colStage'),
      dataIndex: 'stage',
      valueType: 'select',
      // 筛选项取自阶段字典（含已停用）：已停用阶段里的存量商机也要筛得出来，
      // 只列「可新选入」的阶段会让这些商机在筛选器里消失。
      valueEnum: Object.fromEntries(
        stages.map((s) => [s.code, { text: stageLabel(s.code) }]),
      ),
      render: (_, row) => <Tag color={stageColor(row.stage)}>{stageLabel(row.stage)}</Tag>,
    },
    {
      title: t('pages.salesOpportunity.colExpectedClose'),
      dataIndex: 'expectedCloseDate',
      search: false,
      render: (_, row) => row.expectedCloseDate ?? '-',
    },
    {
      title: t('pages.salesOpportunity.colAction'),
      valueType: 'option',
      width: 150,
      render: (_, row) =>
        // 终态不可再关单（服务端会抛 ALREADY_CLOSED）。判定用字典的 stage_type，
        // 而不是比对两个写死的编码——否则自建阶段会被当成「已关闭」而不给操作入口。
        !isTerminal(row.stage)
          ? can[PERMS.opportunityUpdate]
            ? [
                <Popconfirm
                  key="won"
                  title={t('pages.salesOpportunity.confirmWon')}
                  onConfirm={() => onClose(row, 'WON')}
                >
                  <a style={{ color: '#52c41a' }}>{t('pages.salesOpportunity.btnWon')}</a>
                </Popconfirm>,
                <Popconfirm
                  key="lost"
                  title={t('pages.salesOpportunity.confirmLost')}
                  onConfirm={() => onClose(row, 'LOST')}
                >
                  <a style={{ color: '#ff4d4f' }}>{t('pages.salesOpportunity.btnLost')}</a>
                </Popconfirm>,
              ]
            : null
          : [<span key="closed" style={{ color: '#999' }}>{t('pages.salesOpportunity.statusClosed')}</span>],
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Segmented
          value={view}
          onChange={(value) => setView(value as 'list' | 'board')}
          options={[
            { label: t('pages.salesOpportunity.tabList'), value: 'list' },
            { label: t('pages.salesOpportunity.tabBoard'), value: 'board' },
          ]}
        />
      </div>
      {view === 'board' ? (
        <OpportunityBoard />
      ) : (
      <ProTable<SalesOpportunity>
        size="small"
        headerTitle={t('pages.salesOpportunity.titlePipeline')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchSalesOpportunities({
            stage: params.stage,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.salesOpportunity.btnCreateNew')}
          </Button>,
        ]}
      />
      )}
      <Modal
        title={t('pages.salesOpportunity.titleCreate')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.salesOpportunity.btnCreate')}
        confirmLoading={saving}
        destroyOnClose
        width={640}
      >
        <Form
          form={form}
          name="salesOpportunityForm"
          layout="horizontal"
          labelCol={{ flex: `${metrics.labelWidth}px` }}
          wrapperCol={{ flex: 1 }}
        >
          {/* 4 个成对字段进栅格（640px 弹窗里可用宽 ≥ 2 × 256 ⇒ 两列，窄了自动退一列）。
              原先的 `<Row gutter={16}>` + 4 个 `<Col span={12}>` 是**写死两列、无任何断点**的。 */}
          <FormGrid>
            <Form.Item
              name="opportunityId"
              label={t('pages.salesOpportunity.colOpportunityName')}
              rules={[{ required: true, message: t('pages.salesOpportunity.messageSelectOpportunity') }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={(opportunities.data?.items ?? []).map((o) => ({
                  value: o.id,
                  label: `${o.name}（${o.customerName ?? o.customerId}）`,
                }))}
                placeholder={t('pages.salesOpportunity.placeholderSelectOpportunity')}
              />
            </Form.Item>
            <Form.Item name="amount" label={t('pages.salesOpportunity.colAmountYuan')}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="stage"
              label={t('pages.salesOpportunity.colStage')}
              rules={[{ required: true, message: t('pages.salesOpportunity.messageSelectStage') }]}
            >
              <Select
                // 只列「可新选入」的阶段：停用中的阶段不该出现在这里（服务端同样会拒）
                options={selectableStages.map((s) => ({
                  value: s.code,
                  label: stageLabel(s.code),
                }))}
                placeholder={t('pages.salesOpportunity.placeholderSelectStage')}
              />
            </Form.Item>
            <Form.Item name="expectedCloseDate" label={t('pages.salesOpportunity.labelExpectedCloseDate')}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </FormGrid>
        </Form>
      </Modal>
    </>
  )
}
