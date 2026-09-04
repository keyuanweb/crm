import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App, Button, Card, Descriptions, Form, Input, Modal, Space, Tag, Typography, Alert } from 'antd'
import { UserOutlined, KeyOutlined, EditOutlined, SaveOutlined, CloseOutlined } from '@ant-design/icons'
import { fetchPersonalInfo, updateDisplayName } from '../../services/personalService'
import { changeOwnPassword } from '../../services/userService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import type { PersonalInfo as PersonalInfoType, UpdateDisplayNamePayload } from '../../types/personal'

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员',
  SALES: '销售',
  SUPPORT: '客服',
}

const ROLE_COLORS: Record<string, string> = {
  ADMIN: 'gold',
  SALES: 'blue',
  SUPPORT: 'green',
}

const DATA_SCOPE_LABELS: Record<string, string> = {
  SELF: '本人',
  DEPT: '本部门',
  DEPT_AND_CHILD: '本部门及下级',
  ALL: '全部',
}

export default function PersonalCenterPage() {
  const { message: messageApi } = App.useApp()
  const navigate = useNavigate()
  const setUser = useAuthStore((s) => s.setUser)

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
      messageApi.error(extractErrorMessage(err, '加载个人信息失败'))
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
      messageApi.success('显示名已更新')
      setEditMode(false)
      await load()
      // 更新本地存储的用户信息
      const currentUser = useAuthStore.getState().user
      if (currentUser) {
        setUser({ ...currentUser, displayName: values.displayName.trim() })
      }
    } catch (err) {
      if (!(err instanceof Error && err.message.includes('displayName'))) {
        messageApi.error(extractErrorMessage(err, '保存失败'))
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
        messageApi.error('两次输入的新密码不一致')
        return
      }
      setPasswordError('')
      setPasswordLoading(true)
      await changeOwnPassword(values.oldPassword, values.newPassword)
      messageApi.success('密码修改成功，请重新登录')
      setPasswordModalOpen(false)
      passwordForm.resetFields()
      // 清除认证状态并跳转到登录页
      setTimeout(() => {
        useAuthStore.getState().clear()
        navigate('/login', { replace: true })
      }, 1500)
    } catch (err) {
      setPasswordError(extractErrorMessage(err, '密码修改失败'))
    } finally {
      setPasswordLoading(false)
    }
  }

  const formatDateTime = (datetime?: string) => {
    if (!datetime) return '从未'
    return new Date(datetime).toLocaleString('zh-CN')
  }

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '24px 16px' }} role="main" aria-label="个人中心页面">
      <Typography.Title level={3} style={{ marginBottom: 24 }}>
        <UserOutlined /> 个人中心
      </Typography.Title>

      {/* 基本信息 */}
      <Card
        title="基本信息"
        style={{ marginBottom: 24, borderRadius: 10 }}
        data-testid="basic-info-card"
        loading={loading}
        extra={
          !editMode ? (
            <Button icon={<EditOutlined />} onClick={() => setEditMode(true)}>
              编辑显示名
            </Button>
          ) : (
            <Space>
              <Button icon={<CloseOutlined />} onClick={handleCancelEdit}>
                取消
              </Button>
              <Button type="primary" icon={<SaveOutlined />} onClick={handleSaveDisplayName}>
                保存
              </Button>
            </Space>
          )
        }
      >
        {editMode ? (
          <Form form={editForm} layout="vertical" initialValues={{ displayName: personalInfo?.displayName }}>
            <Form.Item
              name="displayName"
              label="显示名"
              rules={[
                { required: true, message: '请输入显示名' },
                { min: 3, max: 50, message: '显示名长度必须在 3~50 位之间' },
              ]}
            >
              <Input size="large" prefix={<UserOutlined />} />
            </Form.Item>
          </Form>
        ) : (
          <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small">
            <Descriptions.Item label="用户名" span={2}>
              <UserOutlined /> {personalInfo?.username}
            </Descriptions.Item>
            <Descriptions.Item label="显示名" span={1}>
              {personalInfo?.displayName}
            </Descriptions.Item>
            <Descriptions.Item label="角色" span={1}>
              <Tag color={ROLE_COLORS[personalInfo?.role || '']}>{ROLE_LABELS[personalInfo?.role || ''] || personalInfo?.role}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="所属部门" span={1}>
              {personalInfo?.departmentName || '未分配'}
            </Descriptions.Item>
            <Descriptions.Item label="数据权限" span={1}>
              {DATA_SCOPE_LABELS[personalInfo?.dataScope || ''] || personalInfo?.dataScope}
            </Descriptions.Item>
            <Descriptions.Item label="状态" span={1}>
              {personalInfo?.enabled ? (
                <Tag color="green">启用</Tag>
              ) : (
                <Tag color="red">停用</Tag>
              )}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>

      {/* 安全设置 */}
      <Card
        title="安全设置"
        style={{ marginBottom: 24, borderRadius: 10 }}
        data-testid="security-card"
        extra={
          <Button type="primary" icon={<KeyOutlined />} onClick={() => setPasswordModalOpen(true)}>
            修改密码
          </Button>
        }
      >
        <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small">
          <Descriptions.Item label="最后登录时间" span={2}>
            {formatDateTime(personalInfo?.lastLoginAt || undefined)}
          </Descriptions.Item>
          <Descriptions.Item label="密码最后修改时间" span={2}>
            {formatDateTime(personalInfo?.passwordUpdatedAt || undefined)}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 修改密码 Modal */}
      <Modal
        title="修改密码"
        open={passwordModalOpen}
        onOk={handlePasswordChange}
        onCancel={() => {
          setPasswordModalOpen(false)
          passwordForm.resetFields()
          setPasswordError('')
        }}
        confirmLoading={passwordLoading}
        okText="确认修改"
        destroyOnClose
      >
        <Form form={passwordForm} name="passwordChangeForm" layout="vertical">
          {passwordError && (
            <Alert type="error" message={passwordError} style={{ marginBottom: 16 }} />
          )}
          <Form.Item
            name="oldPassword"
            label="旧密码"
            rules={[{ required: true, message: '请输入旧密码' }]}
          >
            <Input.Password size="large" placeholder="请输入旧密码" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, max: 64, message: '8~64 位' },
            ]}
            extra="须同时包含字母与数字"
          >
            <Input.Password size="large" placeholder="请输入新密码" />
          </Form.Item>
          <Form.Item
            name="confirm"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('两次输入的新密码不一致'))
                },
              }),
            ]}
          >
            <Input.Password size="large" placeholder="请再次输入新密码" />
          </Form.Item>
        </Form>
        <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0, marginTop: 16 }}>
          修改成功后需要重新登录（旧访问令牌立即失效）
        </Typography.Paragraph>
      </Modal>
    </div>
  )
}
