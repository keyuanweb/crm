import { useEffect, useState } from 'react'
import { Navigate, NavLink, Outlet, Route, Routes, useLocation } from 'react-router-dom'
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
    return <div className="p-8 text-center text-gray-400">加载中…</div>
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
  }

  const navClass = ({ isActive }: { isActive: boolean }) =>
    isActive ? 'px-3 py-2 text-white' : 'px-3 py-2 text-gray-300 hover:text-white'

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-gray-800 text-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-1">
            <span className="mr-4 font-semibold">CRM 系统</span>
            <NavLink to="/customers" className={navClass}>
              客户
            </NavLink>
            <NavLink to="/opportunities" className={navClass}>
              商机
            </NavLink>
            <NavLink to="/sales-opportunities" className={navClass}>
              销售机会
            </NavLink>
            <NavLink to="/stats" className={navClass}>
              统计
            </NavLink>
            {user?.role === 'ADMIN' && (
              <NavLink to="/users" className={navClass}>
                用户管理
              </NavLink>
            )}
            {user?.role === 'ADMIN' && (
              <NavLink to="/audit-logs" className={navClass}>
                审计日志
              </NavLink>
            )}
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-gray-300">
              {user?.displayName ?? user?.username}（{user?.role ?? ''}）
            </span>
            <NavLink to="/account/password" className="text-sm text-gray-300 hover:text-white">
              修改密码
            </NavLink>
            <button onClick={onLogout} className="text-sm text-gray-300 hover:text-white">
              退出
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6">
        <Outlet />
      </main>
    </div>
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
