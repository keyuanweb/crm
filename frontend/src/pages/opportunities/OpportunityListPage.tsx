import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { App, Button, Form, Input, InputNumber, Modal, Popconfirm, Select } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  createOpportunity,
  deleteOpportunity,
  fetchOpportunities,
  updateOpportunity,
  type OpportunityPayload,
} from '../../services/opportunityService'
import { fetchCustomers } from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { formatAmount, type Opportunity } from '../../types/opportunity'
import { useQuery } from '@tanstack/react-query'
import { extractCfParams, useCustomFieldFilterColumns } from '../../hooks/useCustomFieldFilters'
import {
  fromCustomFieldValues,
  toCustomFieldPayload,
} from '../../utils/customField'
import { CustomFieldFormItems } from '../../components/CustomFieldItems'
import { FormGrid, useFormMetrics } from '../../components/ui'

interface FormValues {
  customerId: number
  name: string
  expectedAmountMin?: number
  expectedAmountMax?: number
  customFieldValues?: Record<string, string | number | undefined>
  remark?: string
}

export default function OpportunityListPage() {
  const { t } = useTranslation()
  const metrics = useFormMetrics()
  const actionRef = useRef<ActionType>()
  const customFieldFilterColumns = useCustomFieldFilterColumns('OPPORTUNITY')
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Opportunity | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<FormValues>()
  // 删除商机走 DELETE /opportunities/{id}，OpportunityController 上标的是 opportunity:delete。
  const can = usePerms([PERMS.opportunityDelete])

  const customers = useQuery({
    queryKey: ['customers-options'],
    queryFn: () => fetchCustomers({ page: 1, pageSize: 100 }),
  })

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: Opportunity) => {
    setEditing(row)
    form.setFieldsValue({
      customerId: row.customerId,
      name: row.name,
      expectedAmountMin: row.expectedAmountMin ? row.expectedAmountMin / 100 : undefined,
      expectedAmountMax: row.expectedAmountMax ? row.expectedAmountMax / 100 : undefined,
      remark: row.remark,
    })
    const cf = fromCustomFieldValues(row.customFieldValues)
    if (cf) form.setFieldsValue({ customFieldValues: cf })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: OpportunityPayload = {
      customerId: values.customerId,
      name: values.name,
      expectedAmountMin: values.expectedAmountMin ? values.expectedAmountMin * 100 : undefined,
      expectedAmountMax: values.expectedAmountMax ? values.expectedAmountMax * 100 : undefined,
      remark: values.remark,
      customFieldValues: toCustomFieldPayload(values.customFieldValues as Record<string, unknown>),
    }
    setSaving(true)
    try {
      if (editing) {
        await updateOpportunity(editing.id, { ...payload, version: editing.version })
        message.success(t('pages.opportunity.list.msgSaved'))
      } else {
        await createOpportunity(payload)
        message.success(t('pages.opportunity.list.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Opportunity) => {
    try {
      await deleteOpportunity(row.id)
      message.success(t('pages.opportunity.list.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const columns: ProColumns<Opportunity>[] = [
    { title: t('pages.opportunity.list.colName'), dataIndex: 'name' },
    { title: t('pages.opportunity.list.colCustomer'), dataIndex: 'customerName', search: false },
    {
      title: t('pages.opportunity.list.colAmount'),
      search: false,
      render: (_, row) =>
        `${formatAmount(row.expectedAmountMin)} ~ ${formatAmount(row.expectedAmountMax)}`,
    },
    { title: t('pages.opportunity.list.colSalesCount'), dataIndex: 'salesOpportunityCount', search: false },
    {
      title: t('pages.opportunity.list.colStatus'),
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: {
        ACTIVE: { text: t('pages.opportunity.list.active'), status: 'Processing' },
        ARCHIVED: { text: t('pages.opportunity.list.archived'), status: 'Default' },
      },
    },
    {
      title: t('pages.opportunity.list.colAction'),
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          {t('common.button.edit')}
        </a>,
        can[PERMS.opportunityDelete] ? (
          <Popconfirm
            key="delete"
            title={t('pages.opportunity.list.deleteConfirm', { name: row.name })}
            onConfirm={() => onDelete(row)}
          >
            <a style={{ color: '#ff4d4f' }}>{t('common.button.delete')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  return (
    <>
      <ProTable<Opportunity>
        size="small"
        headerTitle={t('pages.opportunity.list.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={[...columns, ...customFieldFilterColumns]}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchOpportunities({
            keyword: params.keyword,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
            ...extractCfParams(params as Record<string, unknown>),
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.opportunity.list.create')}
          </Button>,
        ]}
      />
      <Modal
        title={editing ? t('pages.opportunity.list.editModal') : t('pages.opportunity.list.createModal')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        confirmLoading={saving}
        destroyOnClose
        width={640}
      >
        <Form
          form={form}
          name="opportunityForm"
          layout="horizontal"
          labelCol={{ flex: `${metrics.labelWidth}px` }}
          wrapperCol={{ flex: 1 }}
        >
          {/* 2 个成对字段进栅格；「预算下限 / 上限」「自定义字段」「备注」四项是整行独占，
              按使用纪律第 1 条留在栅格之外（放进栅格会被压成某一列）。
              原先的 `<Row gutter={16}>` + 2 个 `<Col span={12}>` + 4 个 `<Col span={24}>`
              是**写死两列、无任何断点**的。 */}
          <FormGrid>
            <Form.Item
              name="customerId"
              label={t('pages.opportunity.list.formCustomer')}
              rules={[{ required: true, message: t('pages.opportunity.list.msgCustomerRequired') }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={(customers.data?.items ?? []).map((c) => ({
                  value: c.id,
                  label: `${c.name}（${c.company}）`,
                }))}
                placeholder={t('pages.opportunity.list.msgCustomerRequired')}
              />
            </Form.Item>
            <Form.Item
              name="name"
              label={t('pages.opportunity.list.formName')}
              rules={[{ required: true, message: t('pages.opportunity.list.msgNameRequired') }]}
            >
              <Input />
            </Form.Item>
          </FormGrid>
          <Form.Item name="expectedAmountMin" label={t('pages.opportunity.list.formAmountMin')}>
            <InputNumber min={0} suffix="元" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="expectedAmountMax" label={t('pages.opportunity.list.formAmountMax')}>
            <InputNumber min={0} suffix="元" style={{ width: '100%' }} />
          </Form.Item>
          <CustomFieldFormItems entityType="OPPORTUNITY" />
          <Form.Item name="remark" label={t('pages.opportunity.list.formRemark')}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
