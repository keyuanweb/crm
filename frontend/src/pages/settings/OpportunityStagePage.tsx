import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { App, Alert, Button, Form, Input, InputNumber, Modal, Popconfirm, Space, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createOpportunityStage,
  deleteOpportunityStage,
  setOpportunityStageEnabled,
  updateOpportunityStage,
  type OpportunityStagePayload,
} from '../../services/opportunityStageService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  OPPORTUNITY_STAGES_QUERY_KEY,
  useOpportunityStages,
} from '../../hooks/useOpportunityStages'
import { ENUM_KEYS } from '../../constants/enumLabels'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { OpportunityStageDef } from '../../types/opportunity'

interface StageFormValues {
  name: string
  code?: string
  sortOrder?: number
  probability?: number
}

/**
 * 商机阶段配置（1.2）。
 *
 * <p><b>页面能做的事必须与服务端一致</b>：内建终态不可删、不可改编码；停用中的阶段不能直接删（要先停用，
 * 否则看板列会当场消失而商机还在）；新建阶段一律是「进行中」。这些规则后端都强制，界面只是提前把按钮藏掉，
 * 免得用户点了才知道——但**不替代**后端校验。
 */
export default function OpportunityStagePage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { stages, isLoading } = useOpportunityStages()
  const [editing, setEditing] = useState<OpportunityStageDef | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm<StageFormValues>()
  // 停用/启用走 POST /opportunity-stages/{id}/enabled、删除走 DELETE /opportunity-stages/{id}，
  // OpportunityStageController 上两个方法挂的都是 stage:manage。
  const can = usePerms([PERMS.stageManage])

  const invalidate = () => queryClient.invalidateQueries({ queryKey: OPPORTUNITY_STAGES_QUERY_KEY })

  const fail = (key: string) => (err: unknown) =>
    message.error(extractErrorMessage(err, t(`pages.opportunityStageSettings.${key}`)))

  const save = useMutation({
    mutationFn: (vars: { id?: number; payload: OpportunityStagePayload }) =>
      vars.id === undefined
        ? createOpportunityStage(vars.payload)
        : updateOpportunityStage(vars.id, vars.payload),
    onSuccess: (_data, vars) => {
      message.success(
        t(
          vars.id === undefined
            ? 'pages.opportunityStageSettings.msgCreated'
            : 'pages.opportunityStageSettings.msgUpdated',
        ),
      )
      setModalOpen(false)
      void invalidate()
    },
    onError: fail('msgSaveFailed'),
  })

  const toggle = useMutation({
    mutationFn: (vars: { id: number; enabled: boolean }) =>
      setOpportunityStageEnabled(vars.id, vars.enabled),
    onSuccess: (_data, vars) => {
      message.success(
        t(
          vars.enabled
            ? 'pages.opportunityStageSettings.msgEnabled'
            : 'pages.opportunityStageSettings.msgDisabled',
        ),
      )
      void invalidate()
    },
    onError: fail('msgEnableFailed'),
  })

  const remove = useMutation({
    mutationFn: (id: number) => deleteOpportunityStage(id),
    onSuccess: () => {
      message.success(t('pages.opportunityStageSettings.msgDeleted'))
      void invalidate()
    },
    onError: fail('msgDeleteFailed'),
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: OpportunityStageDef) => {
    setEditing(row)
    form.setFieldsValue({
      name: row.name,
      code: row.code,
      sortOrder: row.sortOrder,
      probability: row.probability,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    save.mutate({
      id: editing?.id,
      payload: {
        // 编辑时不提交 code：服务端忽略它，提交了反而会让人以为编码可改
        code: editing ? undefined : values.code,
        name: values.name,
        sortOrder: values.sortOrder,
        probability: values.probability,
      },
    })
  }

  const columns: ProColumns<OpportunityStageDef>[] = [
    { title: t('pages.opportunityStageSettings.colSortOrder'), dataIndex: 'sortOrder', width: 70 },
    {
      title: t('pages.opportunityStageSettings.colName'),
      dataIndex: 'name',
      render: (_, row) => (
        <Space size={4}>
          {/* 内建阶段在英文界面下显示英文名，自建阶段只能显示它自己的名字 */}
          <span>
            {ENUM_KEYS.opportunityStage[row.code as keyof typeof ENUM_KEYS.opportunityStage]
              ? t(ENUM_KEYS.opportunityStage[row.code as keyof typeof ENUM_KEYS.opportunityStage])
              : row.name}
          </span>
          {row.builtIn ? <Tag color="default">{t('pages.opportunityStageSettings.builtIn')}</Tag> : null}
        </Space>
      ),
    },
    { title: t('pages.opportunityStageSettings.colCode'), dataIndex: 'code', copyable: true },
    {
      title: t('pages.opportunityStageSettings.colProbability'),
      dataIndex: 'probability',
      width: 110,
      render: (_, row) =>
        row.stageType === 'ACTIVE' ? `${Math.round((row.probability ?? 0) * 100)}%` : '—',
    },
    {
      title: t('pages.opportunityStageSettings.colType'),
      dataIndex: 'stageType',
      width: 130,
      render: (_, row) => (
        <Tag color={row.stageType === 'WON' ? 'success' : row.stageType === 'LOST' ? 'error' : 'blue'}>
          {t(
            row.stageType === 'WON'
              ? 'pages.opportunityStageSettings.typeWon'
              : row.stageType === 'LOST'
                ? 'pages.opportunityStageSettings.typeLost'
                : 'pages.opportunityStageSettings.typeActive',
          )}
        </Tag>
      ),
    },
    {
      title: t('pages.opportunityStageSettings.colEnabled'),
      dataIndex: 'enabled',
      width: 100,
      render: (_, row) => (
        <Tag color={row.enabled === 1 ? 'green' : 'default'}>
          {t(
            row.enabled === 1
              ? 'pages.opportunityStageSettings.statusEnabled'
              : 'pages.opportunityStageSettings.statusDisabled',
          )}
        </Tag>
      ),
    },
    {
      title: t('pages.opportunityStageSettings.colAction'),
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.opportunityStageSettings.btnEdit')}
        </a>,
        can[PERMS.stageManage] && row.enabled === 1 && (
          <Popconfirm
            key="disable"
            title={t('pages.opportunityStageSettings.confirmDisable', { name: row.name })}
            onConfirm={() => toggle.mutate({ id: row.id, enabled: false })}
          >
            <a>{t('pages.opportunityStageSettings.btnDisable')}</a>
          </Popconfirm>
        ),
        can[PERMS.stageManage] && row.enabled !== 1 && (
          <a key="enable" onClick={() => toggle.mutate({ id: row.id, enabled: true })}>
            {t('pages.opportunityStageSettings.btnEnable')}
          </a>
        ),
        // 内建终态不可删；启用中的阶段也不可删（须先停用）——两处都只是提前藏掉按钮，服务端同样会拒
        row.builtIn || row.enabled === 1 ? (
          <span key="delete" style={{ color: '#bbb' }}>
            {t('pages.opportunityStageSettings.btnDelete')}
          </span>
        ) : can[PERMS.stageManage] ? (
          <Popconfirm
            key="delete"
            title={t('pages.opportunityStageSettings.confirmDelete', { name: row.name })}
            onConfirm={() => remove.mutate(row.id)}
          >
            <a style={{ color: '#ff4d4f' }}>{t('pages.opportunityStageSettings.btnDelete')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  return (
    <>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message={t('pages.opportunityStageSettings.ruleHint')}
      />
      <ProTable<OpportunityStageDef>
        size="small"
        rowKey="id"
        headerTitle={t('pages.opportunityStageSettings.title')}
        columns={columns}
        dataSource={stages}
        loading={isLoading}
        search={false}
        pagination={false}
        options={false}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.opportunityStageSettings.btnCreate')}
          </Button>,
        ]}
      />
      <Modal
        title={t(
          editing
            ? 'pages.opportunityStageSettings.titleEdit'
            : 'pages.opportunityStageSettings.titleCreate',
        )}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        confirmLoading={save.isPending}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label={t('pages.opportunityStageSettings.formName')}
            rules={[
              { required: true, message: t('pages.opportunityStageSettings.messageNameRequired') },
            ]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="code"
            label={t('pages.opportunityStageSettings.formCode')}
            extra={t('pages.opportunityStageSettings.formCodeHint')}
            rules={
              editing
                ? []
                : [
                    {
                      required: true,
                      message: t('pages.opportunityStageSettings.messageCodeRequired'),
                    },
                    {
                      // 与后端 OpportunityStageRequest 的 @Pattern 一致：以字母开头，2–63 位大写/数字/下划线
                      pattern: /^[A-Z][A-Z0-9_]{1,62}$/,
                      message: t('pages.opportunityStageSettings.messageCodePattern'),
                    },
                  ]
            }
          >
            {/* 编码创建后不可改：历史商机的 stage 列按它关联 */}
            <Input disabled={editing !== null} placeholder="BUDGET_APPROVAL" />
          </Form.Item>
          <Form.Item
            name="sortOrder"
            label={t('pages.opportunityStageSettings.formSortOrder')}
            extra={t('pages.opportunityStageSettings.formSortOrderHint')}
          >
            <InputNumber min={0} max={9999} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="probability"
            label={t('pages.opportunityStageSettings.formProbability')}
            extra={t('pages.opportunityStageSettings.formProbabilityHint')}
            rules={[{ type: 'number', min: 0, max: 1 }]}
          >
            {/* 终态赢率由服务端按 stage_type 固定为 1 / 0，不开放配置 */}
            <InputNumber
              min={0}
              max={1}
              step={0.05}
              style={{ width: '100%' }}
              disabled={editing !== null && editing.stageType !== 'ACTIVE'}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
