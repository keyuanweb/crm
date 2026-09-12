import { useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createStageAction,
  deleteStageAction,
  fetchStageActions,
  updateStageAction,
  type ActionTemplatePayload,
} from '../../services/playbookService'
import { extractErrorMessage } from '../../services/apiClient'
import type { StageActionTemplate } from '../../types/playbook'
import { useOpportunityStages } from '../../hooks/useOpportunityStages'

interface FormValues {
  stage: string
  actionName: string
  description?: string
  sortOrder?: number
  required: boolean
}

/**
 * 销售剧本阶段动作模板。
 *
 * <p><b>阶段选项不在这里写死</b>：1.2 起阶段是字典数据，服务端 `StageActionTemplateService.validateStage`
 * 用 `activeCodes()` 校验（非终态，**含已停用**）。前端写死两个编码的后果是双向的——新增阶段后筛选器里
 * 没有它，而列表里明明列着该阶段的模板；已停用阶段上的存量模板在编辑时下拉框还会显示成空值。
 *
 * <p>文案同理走 `useOpportunityStages().stageLabel`，**不要用 `Object.keys(ENUM_KEYS.opportunityStage)`**：
 * 那份登记表是整个商机阶段词汇，其中的终态（CLOSED_WON / CLOSED_LOST）后端不接受，照它生成选项会多出
 * 两个必然被拒的取值。
 */
export default function StageActionTemplatePage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const { activeStages, selectableStages, stageLabel, isLoading: stagesLoading } =
    useOpportunityStages()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<StageActionTemplate | null>(null)
  const [form] = Form.useForm<FormValues>()

  const reload = () => actionRef.current?.reload()

  /**
   * 表单里的阶段选项。取 `activeStages`（含已停用）而不是 `selectableStages`：后者会让「阶段已停用但模板
   * 还在」的存量数据在编辑时下拉框显示空值，用户一保存就把阶段改成了别的。
   *
   * <p>另外把「已被移出字典」的阶段补回列表——阶段可从配置页删除，模板却不会跟着消失，缺了这一步同样是空值。
   */
  const stageOptions = useMemo(() => {
    const options = activeStages.map((s) => ({ value: s.code, label: stageLabel(s.code) }))
    if (editing && !options.some((o) => o.value === editing.stage)) {
      options.unshift({ value: editing.stage, label: stageLabel(editing.stage) })
    }
    return options
  }, [activeStages, editing, stageLabel])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    // 默认落在第一个「可新选入」的阶段；字典还没加载完时退回第一个非终态阶段，都没有就留空由必填校验拦住
    form.setFieldsValue({
      stage: (selectableStages[0] ?? activeStages[0])?.code,
      required: false,
    })
    setModalOpen(true)
  }

  const openEdit = (row: StageActionTemplate) => {
    setEditing(row)
    form.setFieldsValue({
      stage: row.stage,
      actionName: row.actionName,
      description: row.description,
      sortOrder: row.sortOrder,
      required: row.required,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: ActionTemplatePayload = {
      stage: values.stage,
      actionName: values.actionName.trim(),
      description: values.description,
      sortOrder: values.sortOrder,
      required: values.required,
    }
    try {
      if (editing) {
        await updateStageAction(editing.id, { ...payload, version: editing.version })
        message.success(t('common.message.saved'))
      } else {
        await createStageAction(payload)
        message.success(t('pages.playbook.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const onDelete = async (row: StageActionTemplate) => {
    try {
      await deleteStageAction(row.id)
      message.success(t('pages.playbook.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.playbook.msgDeleteFailed')))
    }
  }

  const columns: ProColumns<StageActionTemplate>[] = [
    {
      title: t('pages.playbook.colStage'),
      dataIndex: 'stage',
      valueType: 'select',
      // 筛选项列全部非终态阶段（含已停用）：模板是按阶段存的，已停用阶段上的模板照样要筛得出来
      valueEnum: Object.fromEntries(activeStages.map((s) => [s.code, { text: stageLabel(s.code) }])),
      render: (_, row) => <Tag color="blue">{stageLabel(row.stage)}</Tag>,
    },
    { title: t('pages.playbook.colActionName'), dataIndex: 'actionName' },
    { title: t('pages.playbook.colDescription'), dataIndex: 'description', search: false },
    { title: t('pages.playbook.colSortOrder'), dataIndex: 'sortOrder', search: false },
    {
      title: t('pages.playbook.colRequired'),
      dataIndex: 'required',
      search: false,
      render: (_, row) =>
        row.required ? <Tag color="red">{t('pages.playbook.required')}</Tag> : <Tag>{t('pages.playbook.optional')}</Tag>,
    },
    {
      title: t('pages.playbook.colEnabled'),
      dataIndex: 'enabled',
      search: false,
      render: (_, row) =>
        row.enabled ? (
          <Tag color="green">{t('common.status.active')}</Tag>
        ) : (
          <Tag>{t('common.status.inactive')}</Tag>
        ),
    },
    {
      title: t('pages.playbook.colAction'),
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          {t('common.button.edit')}
        </a>,
        <Popconfirm
          key="delete"
          title={t('pages.playbook.confirmDelete', { name: row.actionName })}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>{t('common.button.delete')}</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<StageActionTemplate>
        headerTitle={t('pages.playbook.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchStageActions(params.stage, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.playbook.btnCreate')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.playbook.modalEditTitle') : t('pages.playbook.modalCreateTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
        width={520}
      >
        <Form form={form} name="stageActionForm" layout="vertical">
          <Form.Item name="stage" label={t('pages.playbook.formStageLabel')} rules={[{ required: true, message: t('pages.playbook.msgStageRequired') }]}>
            <Select disabled={!!editing} loading={stagesLoading} options={stageOptions} />
          </Form.Item>
          <Form.Item name="actionName" label={t('pages.playbook.formActionNameLabel')} rules={[{ required: true, message: t('pages.playbook.msgActionNameRequired') }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="description" label={t('pages.playbook.formDescriptionLabel')}>
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
            <Form.Item name="sortOrder" label={t('pages.playbook.formSortOrderLabel')} style={{ flex: 1 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="required" label={t('pages.playbook.formRequiredLabel')} valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </>
  )
}
