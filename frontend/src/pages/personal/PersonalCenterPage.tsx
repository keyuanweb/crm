import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  App,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Form,
  Input,
  Modal,
  Space,
  Tag,
  Typography,
  Alert,
} from 'antd'
import {
  UserOutlined,
  KeyOutlined,
  EditOutlined,
  SaveOutlined,
  CloseOutlined,
  SafetyOutlined,
} from '@ant-design/icons'
import { fetchPersonalInfo, updateDisplayName } from '../../services/personalService'
import { changeOwnPassword } from '../../services/userService'
import {
  disableMfa,
  enableMfa,
  fetchMfaStatus,
  regenerateRecoveryCodes,
  setupMfa,
  type MfaSetupResult,
  type MfaStatus,
} from '../../services/mfaService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import type { PersonalInfo as PersonalInfoType, UpdateDisplayNamePayload } from '../../types/personal'
import { FormGrid, FormModal, VERTICAL_MIN_ITEM_WIDTH } from '../../components/ui'

/**
 * 恢复码清单（082）。
 *
 * <p>这十个码是**一次性的明文**：只在 `enable` / `regenerate` 的那一次响应里出现，之后服务端
 * 只留加盐哈希，**任何接口都取不回来**。所以这一块的两个设计点都是"既然只有这一次，就不要
 * 把它搞丢"：① 等宽字体 + 逐行排布（让人能一眼抄准，不会把 `0`/`O` 看混——字母表本身已经去掉
 * 了 `I/L/O`）；② 明说"关闭后无法再次查看"，而不是给一个看起来能再点开的入口。
 */
function RecoveryCodeList({ codes }: { codes: string[] }) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))',
        gap: 8,
        padding: 12,
        background: '#fafafa',
        border: '1px solid #e8e8e8',
        borderRadius: 8,
      }}
    >
      {codes.map((code) => (
        <Typography.Text
          key={code}
          code
          style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 14 }}
        >
          {code}
        </Typography.Text>
      ))}
    </div>
  )
}

const ROLE_COLORS: Record<string, string> = {
  ADMIN: 'gold',
  SALES: 'blue',
  SUPPORT: 'green',
}

export default function PersonalCenterPage() {
  const { t } = useTranslation()
  const { message: messageApi } = App.useApp()
  const navigate = useNavigate()
  const setUser = useAuthStore((s) => s.setUser)

  // 角色 / 数据权限的显示名（原为模块级常量，含中文需 t()，故搬入组件内）
  const roleLabels: Record<string, string> = {
    ADMIN: t('pages.personalCenter.roleAdmin'),
    SALES: t('pages.personalCenter.roleSales'),
    SUPPORT: t('pages.personalCenter.roleSupport'),
  }

  const dataScopeLabels: Record<string, string> = {
    SELF: t('pages.personalCenter.scopeSelf'),
    DEPT: t('pages.personalCenter.scopeDept'),
    DEPT_AND_CHILD: t('pages.personalCenter.scopeDeptAndChild'),
    ALL: t('pages.personalCenter.scopeAll'),
  }

  const [loading, setLoading] = useState(false)
  const [personalInfo, setPersonalInfo] = useState<PersonalInfoType | null>(null)
  const [editMode, setEditMode] = useState(false)
  const [editForm] = Form.useForm<{ displayName: string }>()
  const [passwordModalOpen, setPasswordModalOpen] = useState(false)
  const [passwordForm] = Form.useForm<{ oldPassword: string; newPassword: string; confirm: string }>()
  const [passwordLoading, setPasswordLoading] = useState(false)
  const [passwordError, setPasswordError] = useState('')

  // ---- 双因素认证（082） ----
  // `mfaStatus === null` 与 `mfaStatus.enabled === false` 是**两件不同的事**：前者是"没问到"
  // （接口失败 / 还没加载完），后者是"问到了，没启用"。界面上必须分得开——把"没问到"画成
  // "未启用"是在用一个安全状态去填一个信息空白，用户会以为自己的第二因素被关掉了。
  const [mfaStatus, setMfaStatus] = useState<MfaStatus | null>(null)
  const [mfaBusy, setMfaBusy] = useState(false)
  const [enableOpen, setEnableOpen] = useState(false)
  const [setupResult, setSetupResult] = useState<MfaSetupResult | null>(null)
  const [enableForm] = Form.useForm<{ code: string }>()
  const [regenOpen, setRegenOpen] = useState(false)
  const [regenForm] = Form.useForm<{ password: string }>()
  const [disableOpen, setDisableOpen] = useState(false)
  const [disableForm] = Form.useForm<{ password: string; code: string }>()
  // 新出炉的恢复码（启用成功 / 重新生成之后）。它同时是"要不要显示恢复码页"的开关：
  // 有值 ⇒ 整屏都是恢复码清单，此时 OK 按钮的含义变成"我抄好了"。
  const [freshRecoveryCodes, setFreshRecoveryCodes] = useState<string[] | null>(null)
  const [recoveryAck, setRecoveryAck] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const data = await fetchPersonalInfo()
      setPersonalInfo(data)
    } catch (err) {
      messageApi.error(extractErrorMessage(err, t('pages.personalCenter.msgLoadFailed')))
    } finally {
      setLoading(false)
    }
  }

  const loadMfaStatus = async () => {
    try {
      // `?? null`：把"拿不到状态"的**两种**形态（抛错、以及没给出对象）都归到"没问到"这一支。
      // 类型上 `MfaStatus` 非空，但真落成 `undefined` 时下面的"未知"分支判不到，
      // 紧接着读 `mfaStatus.enabled` 会直接抛 —— 一个空响应就能把整张安全卡打白。
      setMfaStatus((await fetchMfaStatus()) ?? null)
    } catch (err) {
      setMfaStatus(null)
      messageApi.error(extractErrorMessage(err, t('pages.personalCenter.msgMfaLoadFailed')))
    }
  }

  useEffect(() => {
    void load()
    void loadMfaStatus()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /** 收起启用向导：把这一次的密钥材料与恢复码一并丢掉（它们不该在内存里多留一秒）。 */
  const closeEnable = () => {
    setEnableOpen(false)
    setSetupResult(null)
    setFreshRecoveryCodes(null)
    setRecoveryAck(false)
  }

  /** 收起"重新生成恢复码"弹窗。 */
  const closeRegen = () => {
    setRegenOpen(false)
    setFreshRecoveryCodes(null)
    setRecoveryAck(false)
  }

  /** 恢复码页的 OK 按钮：只是"我抄好了"，不再提交任何东西。 */
  const closeRegenAfterCodes = () => {
    closeRegen()
    messageApi.success(t('pages.personalCenter.msgMfaRegenerated'))
    void loadMfaStatus()
  }

  const handleStartEnable = async () => {
    setMfaBusy(true)
    try {
      const setup = await setupMfa()
      setSetupResult(setup)
      setFreshRecoveryCodes(null)
      setRecoveryAck(false)
      setEnableOpen(true)
    } catch (err) {
      messageApi.error(extractErrorMessage(err, t('pages.personalCenter.msgMfaSetupFailed')))
    } finally {
      setMfaBusy(false)
    }
  }

  /**
   * 启用向导的 OK 按钮：**两种含义**，取决于当前停在哪一步。
   *
   * <p>第一步（扫码 + 输码）走 `enable`；第二步（看恢复码）只是"我抄好了"，什么都不提交。
   * 把两件事挂在同一个按钮上是刻意的：这一步没有"取消"可言——恢复码已经生成了，
   * 点取消既不会让它们消失，也不会让它重新显示一次。
   */
  const handleEnableOk = async () => {
    if (freshRecoveryCodes) {
      closeEnable()
      messageApi.success(t('pages.personalCenter.msgMfaEnabled'))
      await loadMfaStatus()
      return
    }
    const values = await enableForm.validateFields()
    setMfaBusy(true)
    try {
      const res = await enableMfa(values.code.trim())
      setFreshRecoveryCodes(res.recoveryCodes)
    } catch (err) {
      // 码错（含剩余次数）或已锁定时弹窗**留在原地**：用户手上就是那个认证器，
      // 关掉弹窗再重来一遍只会让他在失败计数上多走一步。
      messageApi.error(extractErrorMessage(err, t('pages.personalCenter.msgMfaEnableFailed')))
    } finally {
      setMfaBusy(false)
    }
  }

  const handleRegenerate = async () => {
    const values = await regenForm.validateFields()
    setMfaBusy(true)
    try {
      const codes = await regenerateRecoveryCodes(values.password)
      setFreshRecoveryCodes(codes)
      setRecoveryAck(false)
    } catch (err) {
      messageApi.error(extractErrorMessage(err, t('pages.personalCenter.msgMfaRegenerateFailed')))
    } finally {
      setMfaBusy(false)
    }
  }

  const handleDisable = async () => {
    const values = await disableForm.validateFields()
    setMfaBusy(true)
    try {
      await disableMfa({ password: values.password, code: values.code.trim() })
      setDisableOpen(false)
      messageApi.success(t('pages.personalCenter.msgMfaDisabled'))
      await loadMfaStatus()
    } catch (err) {
      messageApi.error(extractErrorMessage(err, t('pages.personalCenter.msgMfaDisableFailed')))
    } finally {
      setMfaBusy(false)
    }
  }

  const handleSaveDisplayName = async () => {
    try {
      const values = await editForm.validateFields()
      const payload: UpdateDisplayNamePayload = { displayName: values.displayName.trim() }
      await updateDisplayName(payload)
      messageApi.success(t('pages.personalCenter.msgSaved'))
      setEditMode(false)
      await load()
      // 更新本地存储的用户信息
      const currentUser = useAuthStore.getState().user
      if (currentUser) {
        setUser({ ...currentUser, displayName: values.displayName.trim() })
      }
    } catch (err) {
      if (!(err instanceof Error && err.message.includes('displayName'))) {
        messageApi.error(extractErrorMessage(err, t('pages.personalCenter.msgSaveFailed')))
      }
    }
  }

  const handleCancelEdit = () => {
    setEditMode(false)
    editForm.resetFields()
  }

  const handlePasswordChange = async () => {
    if (passwordLoading) return
    
    try {
      const values = await passwordForm.validateFields()
      if (values.newPassword !== values.confirm) {
        messageApi.error(t('pages.changePassword.msgPasswordMismatch'))
        return
      }
      setPasswordError('')
      setPasswordLoading(true)
      await changeOwnPassword(values.oldPassword, values.newPassword)
      messageApi.success(t('pages.personalCenter.msgPasswordChanged'))
      setPasswordModalOpen(false)
      passwordForm.resetFields()
      // 清除认证状态并跳转到登录页
      setTimeout(() => {
        useAuthStore.getState().clear()
        navigate('/login', { replace: true })
      }, 1500)
    } catch (err) {
      setPasswordError(extractErrorMessage(err, t('pages.changePassword.msgFailed')))
    } finally {
      setPasswordLoading(false)
    }
  }

  const formatDateTime = (datetime?: string) => {
    if (!datetime) return t('pages.personalCenter.neverLoggedIn')
    return new Date(datetime).toLocaleString('zh-CN')
  }

  return (
    <div
      style={{ maxWidth: 900, margin: '0 auto', padding: '24px 16px' }}
      role="main"
      aria-label={t('pages.personalCenter.ariaPageLabel')}
    >
      <Typography.Title level={3} style={{ marginBottom: 24 }}>
        <UserOutlined /> {t('pages.personalCenter.title')}
      </Typography.Title>

      {/* 基本信息 */}
      <Card
        title={t('pages.personalCenter.cardBasicInfo')}
        style={{ marginBottom: 24 }}
        data-testid="basic-info-card"
        loading={loading}
        extra={
          !editMode ? (
            <Button icon={<EditOutlined />} onClick={() => setEditMode(true)}>
              {t('pages.personalCenter.btnEditDisplayName')}
            </Button>
          ) : (
            <Space>
              <Button icon={<CloseOutlined />} onClick={handleCancelEdit}>
                {t('common.button.cancel')}
              </Button>
              <Button type="primary" icon={<SaveOutlined />} onClick={handleSaveDisplayName}>
                {t('pages.personalCenter.btnSave')}
              </Button>
            </Space>
          )
        }
      >
        {editMode ? (
          <Form form={editForm} layout="vertical" initialValues={{ displayName: personalInfo?.displayName }}>
            <Form.Item
              name="displayName"
              label={t('pages.personalCenter.colDisplayName')}
              rules={[
                { required: true, message: t('pages.personalCenter.msgDisplayNameRequired') },
                { min: 3, max: 50, message: t('pages.personalCenter.msgDisplayNameLength') },
              ]}
            >
              <Input size="large" prefix={<UserOutlined />} />
            </Form.Item>
          </Form>
        ) : (
          <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small">
            <Descriptions.Item label={t('pages.personalCenter.colUsername')} span={2}>
              <UserOutlined /> {personalInfo?.username}
            </Descriptions.Item>
            <Descriptions.Item label={t('pages.personalCenter.colDisplayName')} span={1}>
              {personalInfo?.displayName}
            </Descriptions.Item>
            <Descriptions.Item label={t('pages.personalCenter.colRole')} span={1}>
              <Tag color={ROLE_COLORS[personalInfo?.role || '']}>{roleLabels[personalInfo?.role || ''] || personalInfo?.role}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('pages.personalCenter.colDepartment')} span={1}>
              {personalInfo?.departmentName || t('pages.personalCenter.unassigned')}
            </Descriptions.Item>
            <Descriptions.Item label={t('pages.personalCenter.colDataScope')} span={1}>
              {dataScopeLabels[personalInfo?.dataScope || ''] || personalInfo?.dataScope}
            </Descriptions.Item>
            <Descriptions.Item label={t('pages.personalCenter.colStatus')} span={1}>
              {personalInfo?.enabled ? (
                <Tag color="green">{t('common.status.active')}</Tag>
              ) : (
                <Tag color="red">{t('common.status.inactive')}</Tag>
              )}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>

      {/* 安全设置 */}
      <Card
        title={t('pages.personalCenter.cardSecurity')}
        style={{ marginBottom: 24 }}
        data-testid="security-card"
        extra={
          <Button type="primary" icon={<KeyOutlined />} onClick={() => setPasswordModalOpen(true)}>
            {t('pages.personalCenter.btnChangePassword')}
          </Button>
        }
      >
        <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small">
          <Descriptions.Item label={t('pages.personalCenter.colLastLoginAt')} span={2}>
            {formatDateTime(personalInfo?.lastLoginAt || undefined)}
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.personalCenter.colPasswordUpdatedAt')} span={2}>
            {formatDateTime(personalInfo?.passwordUpdatedAt || undefined)}
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.personalCenter.colTwoFactor')} span={2}>
            {mfaStatus === null ? (
              <Space size={8}>
                <Typography.Text type="secondary">
                  {t('pages.personalCenter.mfaUnknown')}
                </Typography.Text>
                <Button type="link" size="small" style={{ padding: 0 }} onClick={() => void loadMfaStatus()}>
                  {t('pages.personalCenter.btnRetry')}
                </Button>
              </Space>
            ) : mfaStatus.enabled ? (
              <Space size={8} wrap>
                <Tag color="green" icon={<SafetyOutlined />}>
                  {t('pages.personalCenter.mfaEnabled')}
                </Tag>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {/* 绑定时间不走 formatDateTime：它把"没值"渲染成"从未"（那是给 lastLoginAt 用的），
                      而"已启用却没有绑定时间"是数据异常，不该伪装成一句正常的话。 */}
                  {t('pages.personalCenter.mfaEnabledAt', {
                    time: mfaStatus.enabledAt ? formatDateTime(mfaStatus.enabledAt) : '—',
                  })}
                  ，{t('pages.personalCenter.mfaRecoveryRemaining', { count: mfaStatus.recoveryCodesRemaining })}
                </Typography.Text>
              </Space>
            ) : (
              <Tag>{t('pages.personalCenter.mfaDisabled')}</Tag>
            )}
          </Descriptions.Item>
        </Descriptions>

        {/* 入口按状态分岔，而不是把三个按钮都摆出来让用户自己判断哪个可用：
            "关闭 2FA"与"重新生成恢复码"在未启用时点了都是 400，摆出来只会让人以为点错了。 */}
        {mfaStatus !== null && (
          <Space style={{ marginTop: 16 }} wrap>
            {mfaStatus.enabled ? (
              <>
                <Button onClick={() => setRegenOpen(true)}>
                  {t('pages.personalCenter.btnRegenerateRecovery')}
                </Button>
                <Button danger onClick={() => setDisableOpen(true)}>
                  {t('pages.personalCenter.btnDisableMfa')}
                </Button>
              </>
            ) : (
              <Button type="primary" loading={mfaBusy} onClick={() => void handleStartEnable()}>
                {t('pages.personalCenter.btnEnableMfa')}
              </Button>
            )}
          </Space>
        )}
      </Card>

      {/* 启用向导：两步 —— 扫码输码，然后一次性展示恢复码 */}
      <FormModal
        title={t('pages.personalCenter.mfaSetupTitle')}
        open={enableOpen}
        onCancel={closeEnable}
        onSubmit={handleEnableOk}
        okText={
          freshRecoveryCodes ? t('pages.personalCenter.mfaRecoveryDone') : t('pages.personalCenter.mfaEnableOk')
        }
        okButtonProps={freshRecoveryCodes ? { disabled: !recoveryAck } : undefined}
        cancelButtonProps={freshRecoveryCodes ? { style: { display: 'none' } } : undefined}
        maskClosable={false}
      >
        {freshRecoveryCodes ? (
          <>
            <Alert
              type="warning"
              showIcon
              message={t('pages.personalCenter.mfaRecoveryNotice')}
              style={{ marginBottom: 16 }}
            />
            <RecoveryCodeList codes={freshRecoveryCodes} />
            <Checkbox
              style={{ marginTop: 16 }}
              checked={recoveryAck}
              onChange={(e) => setRecoveryAck(e.target.checked)}
            >
              {t('pages.personalCenter.mfaRecoveryAck')}
            </Checkbox>
          </>
        ) : (
          <>
            <Alert
              type="info"
              showIcon
              message={t('pages.personalCenter.mfaScanHint')}
              style={{ marginBottom: 16 }}
            />
            <div style={{ textAlign: 'center', marginBottom: 16 }}>
              {setupResult && (
                <img
                  src={setupResult.qrCodeDataUrl}
                  alt={t('pages.personalCenter.mfaQrAlt')}
                  aria-label={t('pages.personalCenter.mfaQrAlt')}
                  style={{ width: 180, height: 180, border: '1px solid #e8e8e8', borderRadius: 8 }}
                />
              )}
            </div>
            {/* 手动输入的密钥与二维码是同一份东西：扫码失败（摄像头坏了、用的是手机端 App）
                时没有第二条路可走。密钥同样是明文凭证，故只在本次弹窗内显示。*/}
            <Typography.Paragraph
              type="secondary"
              style={{ fontSize: 12, textAlign: 'center', marginBottom: 16 }}
            >
              {t('pages.personalCenter.mfaManualHint')}
              <Typography.Text code copyable style={{ marginLeft: 4 }}>
                {setupResult?.secret}
              </Typography.Text>
            </Typography.Paragraph>
            <Form form={enableForm} layout="vertical" preserve={false}>
              <Form.Item
                name="code"
                label={t('pages.personalCenter.mfaCodeLabel')}
                rules={[
                  { required: true, message: t('pages.personalCenter.mfaCodeRequired') },
                  { pattern: /^[0-9]{6}$/, message: t('pages.personalCenter.mfaCodeFormat') },
                ]}
              >
                <Input size="large" maxLength={6} inputMode="numeric" autoComplete="one-time-code" />
              </Form.Item>
            </Form>
          </>
        )}
      </FormModal>

      {/* 重新生成恢复码 */}
      <FormModal
        title={t('pages.personalCenter.mfaRegenerateTitle')}
        open={regenOpen}
        onCancel={closeRegen}
        onSubmit={freshRecoveryCodes ? closeRegenAfterCodes : handleRegenerate}
        okText={
          freshRecoveryCodes ? t('pages.personalCenter.mfaRecoveryDone') : t('pages.personalCenter.mfaRegenerateOk')
        }
        okButtonProps={freshRecoveryCodes ? { disabled: !recoveryAck } : undefined}
        cancelButtonProps={freshRecoveryCodes ? { style: { display: 'none' } } : undefined}
        maskClosable={false}
      >
        {freshRecoveryCodes ? (
          <>
            <Alert
              type="warning"
              showIcon
              message={t('pages.personalCenter.mfaRecoveryNotice')}
              style={{ marginBottom: 16 }}
            />
            <RecoveryCodeList codes={freshRecoveryCodes} />
            <Checkbox
              style={{ marginTop: 16 }}
              checked={recoveryAck}
              onChange={(e) => setRecoveryAck(e.target.checked)}
            >
              {t('pages.personalCenter.mfaRecoveryAck')}
            </Checkbox>
          </>
        ) : (
          <>
            <Alert
              type="warning"
              showIcon
              message={t('pages.personalCenter.mfaRegenerateWarning')}
              style={{ marginBottom: 16 }}
            />
            <Form form={regenForm} layout="vertical" preserve={false}>
              <Form.Item
                name="password"
                label={t('pages.personalCenter.mfaPasswordLabel')}
                rules={[{ required: true, message: t('pages.personalCenter.mfaPasswordRequired') }]}
              >
                <Input.Password size="large" autoComplete="current-password" />
              </Form.Item>
            </Form>
          </>
        )}
      </FormModal>

      {/* 关闭双因素认证：密码 + 第二因素两样都要，见 mfaService.disableMfa 的注释 */}
      <FormModal
        title={t('pages.personalCenter.mfaDisableTitle')}
        open={disableOpen}
        onCancel={() => setDisableOpen(false)}
        onSubmit={handleDisable}
        okText={t('pages.personalCenter.mfaDisableOk')}
        okButtonProps={{ danger: true }}
      >
        <Alert
          type="warning"
          showIcon
          message={t('pages.personalCenter.mfaDisableWarning')}
          style={{ marginBottom: 16 }}
        />
        <Form form={disableForm} layout="vertical" preserve={false}>
          <Form.Item
            name="password"
            label={t('pages.personalCenter.mfaPasswordLabel')}
            rules={[{ required: true, message: t('pages.personalCenter.mfaPasswordRequired') }]}
          >
            <Input.Password size="large" autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            name="code"
            label={t('pages.personalCenter.mfaCodeOrRecoveryLabel')}
            rules={[{ required: true, message: t('pages.personalCenter.mfaCodeRequired') }]}
          >
            <Input size="large" autoComplete="one-time-code" />
          </Form.Item>
        </Form>
      </FormModal>

      {/* 修改密码 Modal */}
      <Modal
        title={t('pages.personalCenter.btnChangePassword')}
        open={passwordModalOpen}
        onOk={handlePasswordChange}
        onCancel={() => {
          setPasswordModalOpen(false)
          passwordForm.resetFields()
          setPasswordError('')
        }}
        confirmLoading={passwordLoading}
        okText={t('pages.changePassword.btnSubmit')}
        destroyOnClose
        width={640}
      >
        <Form form={passwordForm} name="passwordChangeForm" layout="vertical">
          {passwordError && (
            <Alert type="error" message={passwordError} style={{ marginBottom: 16 }} />
          )}
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
            <Form.Item
              name="oldPassword"
              label={t('pages.changePassword.currentPassword')}
              rules={[{ required: true, message: t('pages.changePassword.msgCurrentRequired') }]}
            >
              <Input.Password size="large" placeholder={t('pages.changePassword.msgCurrentRequired')} />
            </Form.Item>
            <Form.Item
              name="newPassword"
              label={t('pages.changePassword.newPassword')}
              rules={[
                { required: true, message: t('pages.changePassword.msgNewRequired') },
                { min: 8, max: 64, message: t('pages.changePassword.passwordLength') },
              ]}
              extra={t('pages.changePassword.passwordRules')}
            >
              <Input.Password size="large" placeholder={t('pages.changePassword.msgNewRequired')} />
            </Form.Item>
            <Form.Item
              name="confirm"
              label={t('pages.changePassword.confirmPassword')}
              dependencies={['newPassword']}
              rules={[
                { required: true, message: t('pages.changePassword.msgConfirmRequired') },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('newPassword') === value) {
                      return Promise.resolve()
                    }
                    return Promise.reject(new Error(t('pages.changePassword.msgPasswordMismatch')))
                  },
                }),
              ]}
            >
              <Input.Password size="large" placeholder={t('pages.changePassword.msgConfirmRequired')} />
            </Form.Item>
          </FormGrid>
        </Form>
        <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0, marginTop: 16 }}>
          {t('pages.changePassword.reloginNotice')}
        </Typography.Paragraph>
      </Modal>
    </div>
  )
}
