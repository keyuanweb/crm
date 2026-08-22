import { useEffect, useState } from 'react'
import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Dropdown, Layout, Menu, Spin } from 'antd'
import {
  AuditOutlined,
  BarChartOutlined,
  ContactsOutlined,
  DeploymentUnitOutlined,
  FundOutlined,
  IdcardOutlined,
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
import ContactListPage from './pages/contacts/ContactListPage'
import LeadListPage from './pages/leads/LeadListPage'
import LeadDetailPage from './pages/leads/LeadDetailPage'
import OpportunityListPage from './pages/opportunities/OpportunityListPage'
import SalesOpportunityListPage from './pages/sales-opportunities/SalesOpportunityListPage'
import DashboardPage from './pages/stats/DashboardPage'
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
    { path: '/leads', name: '线索', icon: <ContactsOutlined /> },
    { path: '/customers', name: '客户', icon: <TeamOutlined /> },
    { path: '/contacts', name: '联系人', icon: <IdcardOutlined /> },
    { path: '/opportunities', name: '商机', icon: <FundOutlined /> },
    { path: '/sales-opportunities', name: '销售机会', icon: <DeploymentUnitOutlined /> },
    { path: '/stats', name: '统计', icon: <BarChartOutlined /> },
    ...(isAdmin ? [{ path: '/users', name: '用户管理', icon: <UserOutlined /> }] : []),
    ...(isAdmin ? [{ path: '/audit-logs', name: '审计日志', icon: <AuditOutlined /> }] : []),
  ]
  const menuItems = menuRoutes.map((r) => ({ key: r.path, icon: r.icon, label: r.name }))
  // 高亮当前页对应的菜单项（详情页按前缀匹配最长路径）
  const selectedKey =
    menuRoutes
      .map((r) => r.path)
      .filter((p) => location.pathname === p || location.pathname.startsWith(p + '/'))
      .sort((a, b) => b.length - a.length)[0] ?? '/customers'

  const { Header, Sider, Content, Footer } = Layout

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* 顶部栏（ant-layout-header，横跨 100%）：左=系统名称，右=当前用户下拉，内容垂直居中 */}
      <Header
        style={{
          width: '100%',
          height: 56,
          lineHeight: 1,
          padding: '0 20px',
          background: '#fff',
          borderBottom: '1px solid #f0f0f0',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            height: '100%',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <TeamOutlined style={{ fontSize: 22, color: '#1677ff' }} />
            <span style={{ fontSize: 17, fontWeight: 600, color: '#1f1f1f' }}>
              CRM 客户关系管理系统
            </span>
          </div>
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
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
              <Avatar style={{ backgroundColor: '#1677ff' }} size="small">
                {(user?.displayName ?? user?.username ?? '?').charAt(0).toUpperCase()}
              </Avatar>
              <span style={{ fontSize: 14, color: '#1f1f1f' }}>
                {user?.displayName ?? user?.username}
              </span>
            </div>
          </Dropdown>
        </div>
      </Header>

      {/* header 下方：左侧菜单栏 + 右侧内容区 */}
      <Layout style={{ flex: 1, minHeight: 0 }}>
        <Sider
          width={200}
          theme="light"
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}
        >
          <Menu
            mode="inline"
            style={{ height: '100%', borderInlineEnd: 'none' }}
            items={menuItems}
            selectedKeys={[selectedKey]}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
        <Layout style={{ flexDirection: 'column' }}>
          <Content style={{ background: '#f0f2f5', padding: 16, overflow: 'auto' }}>
            <Outlet />
          </Content>
          <Footer
            style={{
              textAlign: 'center',
              padding: '12px 0',
              background: '#f0f2f5',
              color: '#8c8c8c',
              fontSize: 13,
            }}
          >
            CRM 客户关系管理系统 © {new Date().getFullYear()}
          </Footer>
        </Layout>
      </Layout>
    </Layout>
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
        <Route path="leads" element={<LeadListPage />} />
        <Route path="leads/:id" element={<LeadDetailPage />} />
        <Route path="customers" element={<CustomerListPage />} />
        <Route path="customers/:id" element={<CustomerDetailPage />} />
        <Route path="contacts" element={<ContactListPage />} />
        <Route path="opportunities" element={<OpportunityListPage />} />
        <Route path="sales-opportunities" element={<SalesOpportunityListPage />} />
        <Route path="stats" element={<DashboardPage />} />
        <Route path="users" element={<UserManagementPage />} />
        <Route path="audit-logs" element={<AuditLogPage />} />
        <Route path="account/password" element={<ChangePasswordPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
