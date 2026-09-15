import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { App, Button, Card, Descriptions, Form, Input, Modal, Space, Tag, Typography, Alert } from 'antd'
import { UserOutlined, KeyOutlined, EditOutlined, SaveOutlined, CloseOutlined } from '@ant-design/icons'
import { fetchPersonalInfo, updateDisplayName } from '../../services/personalService'
import { changeOwnPassword } from '../../services/userService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import type { PersonalInfo as PersonalInfoType, UpdateDisplayNamePayload } from '../../types/personal'

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

  useEffect(() => {
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

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
        </Descriptions>
      </Card>

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
        </Form>
        <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0, marginTop: 16 }}>
          {t('pages.changePassword.reloginNotice')}
        </Typography.Paragraph>
      </Modal>
    </div>
  )
}
