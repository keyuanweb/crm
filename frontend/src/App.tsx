import { useEffect, useState } from 'react'
import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ProLayout } from '@ant-design/pro-components'
import { Dropdown, Spin } from 'antd'
import {
  AuditOutlined,
  BarChartOutlined,
  DeploymentUnitOutlined,
  FundOutlined,
  KeyOutlined,
  LogoutOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { fetchMe, logout } from './services/authService'
import { useAuthStore } from './store/authStore'
import LoginPage from './pages/LoginPage'
import CustomerListPage from './pages/customers/CustomerListPage'
import CustomerDetailPage from './pages/customers/CustomerDetailPage'
import OpportunityListPage from './pages/opportunities/OpportunityListPage'
import SalesOpportunityListPage from './pages/sales-opportunities/SalesOpportunityListPage'
import OpportunityPipelinePage from './pages/stats/OpportunityPipelinePage'
import UserManagementPage from './pages/users/UserManagementPage'
import ChangePasswordPage from './pages/account/ChangePasswordPage'
import AuditLogPage from './pages/audit/AuditLogPage'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated())
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <>{children}</>
}

function Shell() {
  const { user, setUser, clear, getAccessToken } = useAuthStore()
  const navigate = useNavigate()
  const location = useLocation()
  const [booted, setBooted] = useState(false)

  useEffect(() => {
    if (getAccessToken()) {
      fetchMe()
        .then(setUser)
        .catch(() => clear())
        .finally(() => setBooted(true))
    } else {
      setBooted(true)
    }
  }, [getAccessToken, setUser, clear])

  if (!booted) {
    return (
      <div
        style={{
          display: 'flex',
          minHeight: '100vh',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Spin size="large" />
      </div>
    )
  }

  const onLogout = async () => {
    const refreshToken = localStorage.getItem('refreshToken')
    if (refreshToken) {
      try {
        await logout(refreshToken)
      } catch {
        // 忽略登出接口异常，本地状态必须清理
      }
    }
    clear()
    navigate('/login', { replace: true })
  }

  const isAdmin = user?.role === 'ADMIN'
  const menuRoutes = [
    { path: '/customers', name: '客户', icon: <TeamOutlined /> },
    { path: '/opportunities', name: '商机', icon: <FundOutlined /> },
    { path: '/sales-opportunities', name: '销售机会', icon: <DeploymentUnitOutlined /> },
    { path: '/stats', name: '统计', icon: <BarChartOutlined /> },
    ...(isAdmin ? [{ path: '/users', name: '用户管理', icon: <UserOutlined /> }] : []),
    ...(isAdmin ? [{ path: '/audit-logs', name: '审计日志', icon: <AuditOutlined /> }] : []),
  ]

  return (
    <ProLayout
      title="CRM 客户关系管理系统"
      logo={<TeamOutlined style={{ fontSize: 22, color: '#1677ff' }} />}
      layout="mix"
      location={{ pathname: location.pathname }}
      route={{ routes: menuRoutes }}
      menuItemRender={(item, dom) => <Link to={item.path ?? '/'}>{dom}</Link>}
      avatarProps={{
        title: `${user?.displayName ?? user?.username}（${user?.role ?? ''}）`,
        render: (_, dom) => (
          <Dropdown
            menu={{
              items: [
                {
                  key: 'password',
                  icon: <KeyOutlined />,
                  label: '修改密码',
                  onClick: () => navigate('/account/password'),
                },
                { type: 'divider' },
                {
                  key: 'logout',
                  icon: <LogoutOutlined />,
                  label: '退出登录',
                  onClick: onLogout,
                },
              ],
            }}
          >
            {dom}
          </Dropdown>
        ),
      }}
      onMenuHeaderClick={() => navigate('/customers')}
    >
      <Outlet />
    </ProLayout>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Shell />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/customers" replace />} />
        <Route path="customers" element={<CustomerListPage />} />
        <Route path="customers/:id" element={<CustomerDetailPage />} />
        <Route path="opportunities" element={<OpportunityListPage />} />
        <Route path="sales-opportunities" element={<SalesOpportunityListPage />} />
        <Route path="stats" element={<OpportunityPipelinePage />} />
        <Route path="users" element={<UserManagementPage />} />
        <Route path="audit-logs" element={<AuditLogPage />} />
        <Route path="account/password" element={<ChangePasswordPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
