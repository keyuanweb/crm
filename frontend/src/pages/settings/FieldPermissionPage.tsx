import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Modal, Popconfirm, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  deleteFieldPermission,
  fetchAvailableFields,
  fetchFieldPermissions,
  fieldOptionValue,
  toUpsertField,
  upsertFieldPermission,
} from '../../services/fieldPermissionService'
import { fetchRoleOptions } from '../../services/roleService'
import { extractErrorMessage } from '../../services/apiClient'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { type FieldPermission } from '../../types/fieldPermission'
import { FormGrid, VERTICAL_MIN_ITEM_WIDTH } from '../../components/ui'

interface FormValues {
  roleCode: string
  entityType: string
  /** 由 `fieldOptionValue` 编出来的选项值（`fieldId|fieldKey`），不是字段 id。 */
  field: string
  permission: string
}

/**
 * 字段权限配置页（056，仅 ADMIN）。
 *
 * <p><b>102：本页从「只能配自定义字段」扩到内置字段。</b>字段下拉改调
 * `GET /field-permissions/available-fields`（内置 + 自定义同源，分两组），选项值把 `fieldId` 与
 * `fieldKey` 编在一起、提交时拆开（见 `services/fieldPermissionService.ts` 的两个编解码函数）；
 * 列表的「字段」列改渲染 `fieldName` 而不是那串数字 id。
 *
 * <p><b>1.5：角色下拉改为以后端为准。</b>改造前它是硬编码的
 * `['ADMIN','SALES','SUPPORT','SERVICE']`，两处都错：`SERVICE` **这个角色从来不存在**（`role` 表里
 * 查不到，后端的角色判断里也只有一处写成 `hasAnyRole('ADMIN','SERVICE','SALES')` 的笔误），
 * 而 081 新增的 10 个角色（销售总监/销售代表/客服主管/客服专员/市场/财务/分析师/只读）一个都没有。
 * 净效果是：给「客服专员」配字段权限在界面上根本做不到，而唯一能选的客服角色 `SUPPORT`
 * 恰好是旧的内建名——配出来的规则落在客服实际使用的角色上还是落不到，只能靠试。
 * 现在读 `/roles/options`（含管理员自建角色），首帧用 {@link ENUM_KEYS.userRole} 兜底，
 * 与用户管理页同一套写法。
 */
export default function FieldPermissionPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [fieldOptions, setFieldOptions] = useState<
    { label: string; options: { value: string; label: string }[] }[]
  >([])
  const [roleOptions, setRoleOptions] = useState<{ value: string; label: string }[]>([])
  const [form] = Form.useForm<FormValues>()
  // 删除字段权限配置走 DELETE /field-permissions/{id}，FieldPermissionController 上标的是
  // field_permission:manage（与 upsert 是同一个码）。
  const can = usePerms([PERMS.fieldPermissionManage])

  useEffect(() => {
    void (async () => {
      try {
        const roles = await fetchRoleOptions()
        setRoleOptions(roles.map((r) => ({ value: r.code, label: r.name })))
      } catch {
        // 角色列表拉不到不阻塞页面：首帧兜底（下面那个空数组分支）覆盖 13 个内建角色
      }
    })()
  }, [])

  // 加载完成前 roleOptions 是空数组，直接绑给 Select 会先渲染一个空下拉，
  // 所以用内建枚举兜底首帧；加载完成后自动切到含自建角色的完整列表。
  const roleSelectOptions = roleOptions.length
    ? roleOptions
    : Object.keys(ENUM_KEYS.userRole).map((code) => ({ value: code, label: labelOf(t, ENUM_KEYS.userRole, code) }))

  /**
   * 载入该实体的可配字段（内置 + 自定义，102）。
   *
   * <p>改动点：此前读的是**自定义字段定义列表**，故内置字段（客户电话/商机金额…）在配置面上根本选不到，
   * 后端那套内置字段权限等于没有入口。
   *
   * <p>失败时**不能静默清空**：后端对「没有任何可配字段的实体」返回 422（契约 §3.1），
   * 静默清空会把「实体名/参数不对」显示成一个空下拉，管理员只会以为这个实体没有字段。
   */
  const loadFields = async (entityType: string) => {
    try {
      const res = await fetchAvailableFields(entityType)
      const groups = [
        {
          label: t('pages.fieldPermission.groupBuiltin'),
          options: res.items.filter((f) => f.builtin).map((f) => ({ value: fieldOptionValue(f), label: f.fieldName })),
        },
        {
          label: t('pages.fieldPermission.groupCustom'),
          options: res.items.filter((f) => !f.builtin).map((f) => ({ value: fieldOptionValue(f), label: f.fieldName })),
        },
      ].filter((g) => g.options.length > 0) // 空分组会渲染出一个光秃秃的分组标题
      setFieldOptions(groups)
    } catch (err) {
      setFieldOptions([])
      message.error(extractErrorMessage(err, t('pages.fieldPermission.msgFieldsFailed')))
    }
  }

  const columns: ProColumns<FieldPermission>[] = [
    {
      title: t('pages.fieldPermission.colRole'),
      dataIndex: 'roleCode',
      valueType: 'select',
      // 筛选项与表单同源（roleSelectOptions）：只改表单不改这里，会出现"能配不能筛"的错位。
      valueEnum: Object.fromEntries(roleSelectOptions.map((r) => [r.value, { text: r.label }])),
      render: (_, row) => labelOf(t, ENUM_KEYS.userRole, row.roleCode, row.roleCode),
    },
    { title: t('pages.fieldPermission.colEntity'), dataIndex: 'entityType', render: (_, row) => labelOf(t, ENUM_KEYS.fieldEntity, row.entityType) },
    {
      title: t('pages.fieldPermission.colField'),
      dataIndex: 'fieldName',
      search: false,
      // 102：此前这一列直出数字 fieldId —— 配置列表里一排「8842」既看不出是哪个字段，
      // 也分不出内置/自定义。兜底链仍保留 fieldId：字段定义被删除后 fieldName 与 fieldKey 都可能是空，
      // 那时数字比一片空白更可辨（也正是「这条配置指向的字段已不存在」的证据）。
      render: (_, row) =>
        row.fieldName || row.fieldKey || (row.fieldId == null ? '-' : String(row.fieldId)),
    },
    {
      title: t('pages.fieldPermission.colPermission'),
      dataIndex: 'permission',
      search: false,
      render: (_, row) => {
        const color = row.permission === 'HIDDEN' ? 'red' : row.permission === 'READ_ONLY' ? 'orange' : 'green'
        return <Tag color={color}>{labelOf(t, ENUM_KEYS.fieldPermission, row.permission)}</Tag>
      },
    },
    {
      title: t('pages.fieldPermission.colAction'),
      valueType: 'option',
      render: (_, row) => [
        can[PERMS.fieldPermissionManage] && (
          <Popconfirm key="del" title={t('pages.fieldPermission.confirmDelete')} onConfirm={() => void onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('pages.fieldPermission.btnDelete')}</a>
          </Popconfirm>
        ),
      ],
    },
  ]

  const onDelete = async (row: FieldPermission) => {
    try {
      await deleteFieldPermission(row.id)
      message.success(t('pages.fieldPermission.msgDeleted'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.fieldPermission.msgDeleteFailed')))
    }
  }

  const onCreate = async () => {
    const values = await form.validateFields()
    try {
      // 选项值 → 请求体：内置字段发 fieldKey、自定义字段发 fieldId（后端强制「恰好一个」）
      await upsertFieldPermission({
        roleCode: values.roleCode,
        entityType: values.entityType,
        permission: values.permission,
        ...toUpsertField(values.field),
      })
      message.success(t('pages.fieldPermission.msgSaved'))
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.fieldPermission.msgSaveFailed')))
    }
  }

  return (
    <>
      <ProTable<FieldPermission>
        headerTitle={t('pages.fieldPermission.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchFieldPermissions(params.roleCode, params.entityType, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.resetFields()
              setModalOpen(true)
            }}
          >
            {t('pages.fieldPermission.btnAdd')}
          </Button>,
        ]}
      />
      <Modal title={t('pages.fieldPermission.modalTitle')} open={modalOpen} onOk={() => void onCreate()} onCancel={() => setModalOpen(false)} okText={t('pages.fieldPermission.btnSave')} destroyOnClose width={640}>
        <Form form={form} layout="vertical">
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
            <Form.Item name="roleCode" label={t('pages.fieldPermission.formRoleLabel')} rules={[{ required: true, message: t('pages.fieldPermission.formRoleRequired') }]}>
              <Select options={roleSelectOptions} placeholder={t('pages.fieldPermission.formRolePlaceholder')} />
            </Form.Item>
            <Form.Item name="entityType" label={t('pages.fieldPermission.formEntityLabel')} rules={[{ required: true, message: t('pages.fieldPermission.formEntityRequired') }]}>
              <Select
                options={Object.keys(ENUM_KEYS.fieldEntity).map((code) => ({ value: code, label: labelOf(t, ENUM_KEYS.fieldEntity, code) }))}
                onChange={(v: string) => void loadFields(v)}
                placeholder={t('pages.fieldPermission.formEntityPlaceholder')}
              />
            </Form.Item>
            <Form.Item name="field" label={t('pages.fieldPermission.formFieldLabel')} rules={[{ required: true, message: t('pages.fieldPermission.formFieldRequired') }]}>
              <Select options={fieldOptions} placeholder={t('pages.fieldPermission.formFieldPlaceholder')} />
            </Form.Item>
            <Form.Item name="permission" label={t('pages.fieldPermission.formPermissionLabel')} rules={[{ required: true, message: t('pages.fieldPermission.formPermissionRequired') }]} initialValue="READ_ONLY">
              <Select
                options={Object.keys(ENUM_KEYS.fieldPermission).map((code) => ({ value: code, label: labelOf(t, ENUM_KEYS.fieldPermission, code) }))}
              />
            </Form.Item>
          </FormGrid>
        </Form>
      </Modal>
    </>
  )
}
