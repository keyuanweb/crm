import { Suspense, lazy, startTransition, useEffect, useState } from 'react'
import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Button, Dropdown, Layout, Menu, Spin, type MenuProps } from 'antd'
import {
  AlertOutlined,
  AuditOutlined,
  ApartmentOutlined,
  BarChartOutlined,
  BulbOutlined,
  CalendarOutlined,
  CompassOutlined,
  FundProjectionScreenOutlined,
  ContactsOutlined,
  CustomerServiceOutlined,
  DeleteOutlined,
  DeploymentUnitOutlined,
  DownloadOutlined,
  EnvironmentOutlined,
  FileProtectOutlined,
  FileTextOutlined,
  FormOutlined,
  FundOutlined,
  HomeOutlined,
  IdcardOutlined,
  KeyOutlined,
  LogoutOutlined,
  MailOutlined,
  NotificationOutlined,
  ProfileOutlined,
  RiseOutlined,
  SettingOutlined,
  ShoppingOutlined,
  SafetyCertificateOutlined,
  TagsOutlined,
  ThunderboltOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { fetchMe, logout } from './services/authService'
import { useAuthStore } from './store/authStore'
import LoginPage from './pages/LoginPage'
const CustomerListPage = lazy(() => import('./pages/customers/CustomerListPage'))
const CustomerDetailPage = lazy(() => import('./pages/customers/CustomerDetailPage'))
const AtRiskCustomersPage = lazy(() => import('./pages/customers/AtRiskCustomersPage'))
const ContactListPage = lazy(() => import('./pages/contacts/ContactListPage'))
const LeadListPage = lazy(() => import('./pages/leads/LeadListPage'))
const LeadDetailPage = lazy(() => import('./pages/leads/LeadDetailPage'))
const OpportunityListPage = lazy(() => import('./pages/opportunities/OpportunityListPage'))
const SalesOpportunityListPage = lazy(() => import('./pages/sales-opportunities/SalesOpportunityListPage'))
const DashboardPage = lazy(() => import('./pages/stats/DashboardPage'))
const TeamLeaderboardPage = lazy(() => import('./pages/stats/TeamLeaderboardPage'))
const ReportCenterPage = lazy(() => import('./pages/reports/ReportCenterPage'))
const SuggestionCenterPage = lazy(() => import('./pages/assistant/SuggestionCenterPage'))
const KpiBoardPage = lazy(() => import('./pages/board/KpiBoardPage'))
const RecycleBinPage = lazy(() => import('./pages/recycle/RecycleBinPage'))
const RoleListPage = lazy(() => import('./pages/roles/RoleListPage'))
const UsageMapPage = lazy(() => import('./pages/map/UsageMapPage'))
const TagSegmentPage = lazy(() => import('./pages/tags/TagSegmentPage'))
const EmailMarketingPage = lazy(() => import('./pages/marketing/EmailMarketingPage'))
import GlobalSearch from './components/GlobalSearch'
const SearchResultPage = lazy(() => import('./pages/search/SearchResultPage'))
const ApprovalFlowPage = lazy(() => import('./pages/approval/ApprovalFlowPage'))
const ApprovalCenterPage = lazy(() => import('./pages/approval/ApprovalCenterPage'))
const DuplicateMergePage = lazy(() => import('./pages/customers/DuplicateMergePage'))
const VisitListPage = lazy(() => import('./pages/visits/VisitListPage'))
const OnlineFormPage = lazy(() => import('./pages/marketing/OnlineFormPage'))
const PublicFormPage = lazy(() => import('./pages/marketing/PublicFormPage'))
const AnnouncementPage = lazy(() => import('./pages/announcements/AnnouncementPage'))
const InvoiceListPage = lazy(() => import('./pages/invoices/InvoiceListPage'))
import InstallPrompt from './components/InstallPrompt'
import BreadcrumbNav from './components/BreadcrumbNav'
const ProductListPage = lazy(() => import('./pages/products/ProductListPage'))
const QuoteListPage = lazy(() => import('./pages/quotes/QuoteListPage'))
const QuoteDetailPage = lazy(() => import('./pages/quotes/QuoteDetailPage'))
const ContractListPage = lazy(() => import('./pages/contracts/ContractListPage'))
const ContractDetailPage = lazy(() => import('./pages/contracts/ContractDetailPage'))
const ContractTemplateListPage = lazy(() => import('./pages/contract-templates/ContractTemplateListPage'))
const OrderListPage = lazy(() => import('./pages/orders/OrderListPage'))
const OrderDetailPage = lazy(() => import('./pages/orders/OrderDetailPage'))
const TaskListPage = lazy(() => import('./pages/tasks/TaskListPage'))
const TaskCalendarPage = lazy(() => import('./pages/tasks/TaskCalendarPage'))
const DepartmentListPage = lazy(() => import('./pages/departments/DepartmentListPage'))
const WorkflowRuleListPage = lazy(() => import('./pages/workflows/WorkflowRuleListPage'))
const WorkflowLogListPage = lazy(() => import('./pages/workflows/WorkflowLogListPage'))
const UserManagementPage = lazy(() => import('./pages/users/UserManagementPage'))
const ChangePasswordPage = lazy(() => import('./pages/account/ChangePasswordPage'))
const AuditLogPage = lazy(() => import('./pages/audit/AuditLogPage'))
const CampaignListPage = lazy(() => import('./pages/marketing/CampaignListPage'))
const ChannelRoiPage = lazy(() => import('./pages/marketing/ChannelRoiPage'))
const TicketListPage = lazy(() => import('./pages/tickets/TicketListPage'))
const TicketDetailPage = lazy(() => import('./pages/tickets/TicketDetailPage'))
const KnowledgeArticleListPage = lazy(() => import('./pages/knowledge/KnowledgeArticleListPage'))
const SlaPolicyListPage = lazy(() => import('./pages/sla/SlaPolicyListPage'))
const CustomFieldListPage = lazy(() => import('./pages/settings/CustomFieldListPage'))
const ExportCenterPage = lazy(() => import('./pages/exports/ExportCenterPage'))
import NotificationCenter from './components/NotificationCenter'

/** 菜单项结构（复用 antd Menu items 元素类型，支持多级 submenu，041）。 */
type MenuItemLike = NonNullable<MenuProps['items']>[number]

/**
 * 所有懒加载页面 chunk 的预加载函数。
 * 挂载后在空闲时统一预热，点击菜单时 chunk 已在浏览器缓存中，
 * Suspense 直接渲染目标页，避免首次切换时 fallback 闪烁。
 * import() 是幂等的：首次调用下载并缓存模块，后续调用立即 resolve。
 */
const PRELOAD_PAGES: Array<() => Promise<unknown>> = [
  () => import('./pages/customers/CustomerListPage'),
  () => import('./pages/customers/CustomerDetailPage'),
  () => import('./pages/customers/AtRiskCustomersPage'),
  () => import('./pages/contacts/ContactListPage'),
  () => import('./pages/leads/LeadListPage'),
  () => import('./pages/leads/LeadDetailPage'),
  () => import('./pages/opportunities/OpportunityListPage'),
  () => import('./pages/sales-opportunities/SalesOpportunityListPage'),
  () => import('./pages/stats/DashboardPage'),
  () => import('./pages/stats/TeamLeaderboardPage'),
  () => import('./pages/reports/ReportCenterPage'),
  () => import('./pages/assistant/SuggestionCenterPage'),
  () => import('./pages/board/KpiBoardPage'),
  () => import('./pages/recycle/RecycleBinPage'),
  () => import('./pages/roles/RoleListPage'),
  () => import('./pages/map/UsageMapPage'),
  () => import('./pages/tags/TagSegmentPage'),
  () => import('./pages/marketing/EmailMarketingPage'),
  () => import('./pages/search/SearchResultPage'),
  () => import('./pages/approval/ApprovalFlowPage'),
  () => import('./pages/approval/ApprovalCenterPage'),
  () => import('./pages/customers/DuplicateMergePage'),
  () => import('./pages/visits/VisitListPage'),
  () => import('./pages/marketing/OnlineFormPage'),
  () => import('./pages/marketing/PublicFormPage'),
  () => import('./pages/announcements/AnnouncementPage'),
  () => import('./pages/invoices/InvoiceListPage'),
  () => import('./pages/products/ProductListPage'),
  () => import('./pages/quotes/QuoteListPage'),
  () => import('./pages/quotes/QuoteDetailPage'),
  () => import('./pages/contracts/ContractListPage'),
  () => import('./pages/contracts/ContractDetailPage'),
  () => import('./pages/contract-templates/ContractTemplateListPage'),
  () => import('./pages/orders/OrderListPage'),
  () => import('./pages/orders/OrderDetailPage'),
  () => import('./pages/tasks/TaskListPage'),
  () => import('./pages/tasks/TaskCalendarPage'),
  () => import('./pages/departments/DepartmentListPage'),
  () => import('./pages/workflows/WorkflowRuleListPage'),
  () => import('./pages/workflows/WorkflowLogListPage'),
  () => import('./pages/users/UserManagementPage'),
  () => import('./pages/account/ChangePasswordPage'),
  () => import('./pages/audit/AuditLogPage'),
  () => import('./pages/marketing/CampaignListPage'),
  () => import('./pages/marketing/ChannelRoiPage'),
  () => import('./pages/tickets/TicketListPage'),
  () => import('./pages/tickets/TicketDetailPage'),
  () => import('./pages/knowledge/KnowledgeArticleListPage'),
  () => import('./pages/sla/SlaPolicyListPage'),
  () => import('./pages/settings/CustomFieldListPage'),
  () => import('./pages/exports/ExportCenterPage'),
]

function preloadPages() {
  for (const load of PRELOAD_PAGES) {
    void load().catch(() => {
      // 预加载失败不影响主流程（点击时仍会按需加载）
    })
  }
}

/** 内容区占位骨架：与页面同宽高的灰色占位，避免切换时居中 Spin 造成视觉跳动。 */
function PageSkeleton() {
  return (
    <div style={{ padding: 16 }}>
      <div style={{ height: 32, width: '40%', background: '#f0f2f5', borderRadius: 6, marginBottom: 16 }} />
      {[0, 1, 2, 3, 4].map((i) => (
        <div
          key={i}
          style={{ height: 44, background: i % 2 === 0 ? '#fafafa' : '#fff', borderRadius: 4, marginBottom: 8 }}
        />
      ))}
    </div>
  )
}

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
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)
  // 041：系统管理二级子组展开状态（受控 openKeys）
  const [openKeys, setOpenKeys] = useState<string[]>([])

  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

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

  // 挂载后立即预加载全部页面 chunk（不等空闲）：点击菜单时目标页已缓存，
  // Suspense 直接渲染，杜绝切换瞬间整页闪烁
  useEffect(() => {
    if (!booted) return
    preloadPages()
  }, [booted])

  // 042：进入系统管理/流程与配置/审计与维护一级分组页面时自动展开对应分组（受控 openKeys）
  useEffect(() => {
    const path = location.pathname
    const next: string[] = []
    if (path.startsWith('/users') || path.startsWith('/roles') || path.startsWith('/departments')) next.push('g-admin')
    if (
      path.startsWith('/workflows') ||
      path.startsWith('/approval-flows') ||
      path.startsWith('/settings/custom-fields') ||
      path.startsWith('/contract-templates') ||
      path.startsWith('/sla-policies')
    ) {
      next.push('g-config')
    }
    if (path.startsWith('/tags') || path.startsWith('/audit-logs') || path.startsWith('/recycle-bin')) next.push('g-audit')
    if (next.length) {
      setOpenKeys((prev) => Array.from(new Set([...prev, ...next])))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname])

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
  // 040：左侧菜单按业务域分组（8 组 + 首页置顶），每组 2~5 项
  const customerRoutes = [
    { path: '/leads', name: '线索', icon: <ContactsOutlined /> },
    { path: '/customers', name: '客户', icon: <TeamOutlined /> },
    { path: '/contacts', name: '联系人', icon: <IdcardOutlined /> },
    { path: '/customer-merge', name: '查重合并', icon: <TeamOutlined /> },
    { path: '/customers/at-risk', name: '流失预警', icon: <AlertOutlined /> },
  ]
  const salesRoutes = [
    { path: '/opportunities', name: '商机', icon: <FundOutlined /> },
    { path: '/sales-opportunities', name: '销售机会', icon: <DeploymentUnitOutlined /> },
    { path: '/quotes', name: '报价单', icon: <FileTextOutlined /> },
    { path: '/visits', name: '外勤拜访', icon: <EnvironmentOutlined /> },
    { path: '/products', name: '产品', icon: <ShoppingOutlined /> },
  ]
  const dealRoutes = [
    { path: '/contracts', name: '合同', icon: <FileProtectOutlined /> },
    { path: '/orders', name: '订单', icon: <ProfileOutlined /> },
    { path: '/invoices', name: '发票', icon: <FileTextOutlined /> },
  ]
  const marketingRoutes = [
    { path: '/marketing', name: '营销活动', icon: <NotificationOutlined /> },
    { path: '/marketing/email', name: '邮件营销', icon: <MailOutlined /> },
    { path: '/online-forms', name: '在线表单', icon: <FormOutlined /> },
  ]
  const serviceRoutes = [
    { path: '/tickets', name: '客户服务', icon: <CustomerServiceOutlined /> },
    { path: '/knowledge', name: '知识库', icon: <FileTextOutlined /> },
    { path: '/announcements', name: '公告管理', icon: <NotificationOutlined /> },
    { path: '/approvals', name: '我的审批', icon: <AuditOutlined /> },
  ]
  const workbenchRoutes = [
    { path: '/tasks', name: '任务', icon: <CalendarOutlined /> },
    { path: '/suggestions', name: '智能建议', icon: <BulbOutlined /> },
    { path: '/board', name: '数据大屏', icon: <FundProjectionScreenOutlined /> },
  ]
  const dataRoutes = [
    { path: '/reports', name: '自定义报表', icon: <BarChartOutlined /> },
    { path: '/stats/leaderboard', name: '团队排行', icon: <RiseOutlined /> },
    { path: '/exports', name: '导出中心', icon: <DownloadOutlined /> },
  ]
  // 首页置顶（首位独立菜单项，指向统计仪表盘 /stats）
  const statsRoute = { path: '/stats', name: '首页', icon: <HomeOutlined /> }
  // 041：系统管理二级子组（组织与权限/流程与配置/审计与维护）
  const adminOrgRoutes = [
    { path: '/users', name: '用户管理', icon: <UserOutlined /> },
    { path: '/roles', name: '角色权限', icon: <SafetyCertificateOutlined /> },
    { path: '/departments', name: '部门', icon: <ApartmentOutlined /> },
  ]
  const adminConfigRoutes = [
    { path: '/workflows', name: '工作流', icon: <ThunderboltOutlined /> },
    { path: '/approval-flows', name: '审批流配置', icon: <AuditOutlined /> },
    { path: '/sla-policies', name: 'SLA 策略', icon: <AuditOutlined /> },
    { path: '/contract-templates', name: '合同模板', icon: <FileTextOutlined /> },
    { path: '/settings/custom-fields', name: '自定义字段', icon: <SettingOutlined /> },
  ]
  const adminAuditRoutes = [
    { path: '/tags', name: '标签与细分', icon: <TagsOutlined /> },
    { path: '/audit-logs', name: '审计日志', icon: <AuditOutlined /> },
    { path: '/recycle-bin', name: '回收站', icon: <DeleteOutlined /> },
  ]
  const adminRoutes = [...adminOrgRoutes, ...adminConfigRoutes, ...adminAuditRoutes]
  const menuRoutes = [
    statsRoute,
    ...customerRoutes,
    ...salesRoutes,
    ...dealRoutes,
    ...marketingRoutes,
    ...serviceRoutes,
    ...workbenchRoutes,
    ...dataRoutes,
    ...(isAdmin ? adminRoutes : []),
  ]
  const toItems = (routes: typeof menuRoutes) =>
    routes.map((r) => ({ key: r.path, icon: r.icon, label: r.name }))
  /** 递归拍平菜单项（移动端不支持分组/二级子组，全部拍平为普通项）。 */
  const flattenMenuItems = (items: MenuItemLike[]): MenuItemLike[] =>
    items.flatMap((item) => {
      if (item && 'children' in item && Array.isArray(item.children)) {
        return flattenMenuItems(item.children as MenuItemLike[])
      }
      return [item]
    })
  const statsMenuItem = { key: statsRoute.path, icon: statsRoute.icon, label: statsRoute.name }
  // 028：按角色可见菜单过滤（ADMIN 全量；其他角色按 user.menus；path→menuKey 映射兼容多段路径）
  const visibleMenus = user?.role === 'ADMIN' ? undefined : new Set(user?.menus ?? [])
  const menuKeyOf = (path: string) => {
    const map: Record<string, string> = {
      '/stats': 'stats',
      '/customers/at-risk': 'at-risk',
      '/stats/leaderboard': 'leaderboard',
      '/sales-opportunities': 'sales-opportunities',
      '/settings/custom-fields': 'custom-fields',
      '/contract-templates': 'contract-templates',
      '/recycle-bin': 'recycle-bin',
      '/sla-policies': 'sla-policies',
      '/audit-logs': 'audit-logs',
      '/announcements': 'announcements',
      '/customers': 'customers',
      '/customer-merge': 'customers',
      '/leads': 'leads',
      '/contacts': 'contacts',
      '/opportunities': 'opportunities',
      '/quotes': 'quotes',
      '/visits': 'sales',
      '/contracts': 'contracts',
      '/orders': 'orders',
      '/invoices': 'orders',
      '/tasks': 'tasks',
      '/products': 'products',
      '/marketing': 'marketing',
      '/marketing/email': 'marketing',
      '/online-forms': 'marketing',
      '/tickets': 'tickets',
      '/knowledge': 'knowledge',
      '/exports': 'exports',
      '/reports': 'reports',
      '/suggestions': 'suggestions',
      '/board': 'board',
      '/users': 'users',
      '/departments': 'departments',
      '/workflows': 'workflows',
      '/approvals': 'workflows',
      '/approval-flows': 'workflows',
    }
    return map[path] ?? path.replace(/^\//, '')
  }
  const filterByMenus = (routes: typeof menuRoutes) =>
    visibleMenus ? routes.filter((r) => visibleMenus.has(menuKeyOf(r.path))) : routes
  const groupedMenuItems: MenuItemLike[] = [
    ...(filterByMenus(customerRoutes).length
      ? [{ type: 'submenu' as const, key: 'g-customer', label: '客户管理', children: toItems(filterByMenus(customerRoutes)) }]
      : []),
    ...(filterByMenus(salesRoutes).length
      ? [{ type: 'submenu' as const, key: 'g-sales', label: '销售管理', children: toItems(filterByMenus(salesRoutes)) }]
      : []),
    ...(filterByMenus(dealRoutes).length
      ? [{ type: 'submenu' as const, key: 'g-deal', label: '交易管理', children: toItems(filterByMenus(dealRoutes)) }]
      : []),
    ...(filterByMenus(marketingRoutes).length
      ? [{ type: 'submenu' as const, key: 'g-marketing', label: '营销中心', children: toItems(filterByMenus(marketingRoutes)) }]
      : []),
    ...(filterByMenus(serviceRoutes).length
      ? [{ type: 'submenu' as const, key: 'g-service', label: '服务协作', children: toItems(filterByMenus(serviceRoutes)) }]
      : []),
    ...(filterByMenus(workbenchRoutes).length
      ? [{ type: 'submenu' as const, key: 'g-workbench', label: '工作台', children: toItems(filterByMenus(workbenchRoutes)) }]
      : []),
    ...(filterByMenus(dataRoutes).length
      ? [{ type: 'submenu' as const, key: 'g-data', label: '数据分析', children: toItems(filterByMenus(dataRoutes)) }]
      : []),
    // 042：系统管理扁平化——三个一级分组（系统管理/流程与配置/审计与维护）
    ...(filterByMenus(adminOrgRoutes).length
      ? [{ type: 'submenu' as const, key: 'g-admin', label: '系统管理', children: toItems(filterByMenus(adminOrgRoutes)) }]
      : []),
    ...(filterByMenus(adminConfigRoutes).length
      ? [{ type: 'submenu' as const, key: 'g-config', label: '流程与配置', children: toItems(filterByMenus(adminConfigRoutes)) }]
      : []),
    ...(filterByMenus(adminAuditRoutes).length
      ? [{ type: 'submenu' as const, key: 'g-audit', label: '审计与维护', children: toItems(filterByMenus(adminAuditRoutes)) }]
      : []),
  ]
  // 所有分组默认收起（FR-S14 默认行为）；点击分组标签可收起/展开
  // 统计分析置顶为独立菜单项；移动端横向菜单不支持分组，递归拍平为普通项
  const menuItems = isMobile
    ? [statsMenuItem, ...flattenMenuItems(groupedMenuItems)]
    : [statsMenuItem, ...groupedMenuItems]
  // 高亮当前页对应的菜单项（详情页按前缀匹配最长路径）
  const selectedKey =
    menuRoutes
      .map((r) => r.path)
      .filter((p) => location.pathname === p || location.pathname.startsWith(p + '/'))
      .sort((a, b) => b.length - a.length)[0] ?? '/stats'
  const { Header, Sider, Content, Footer } = Layout

  return (
    // 外层固定视口高度 + 隐藏溢出：顶部栏不参与滚动，内容区独立滚动（FR-018 顶栏固定）
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      {/* 027：PWA 安装提示 */}
      <InstallPrompt />
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
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {/* 032：全局搜索框 */}
            <GlobalSearch />
            {/* 029：右上角使用地图快捷入口 */}
            <Button type="text" icon={<CompassOutlined style={{ fontSize: 17 }} />} onClick={() => navigate('/usage-map')}>
              使用地图
            </Button>
            <NotificationCenter />
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
        </div>
      </Header>

      {/* header 下方：左侧菜单栏 + 右侧内容区（窄屏折叠菜单为顶部横向滚动） */}
      <Layout style={{ flex: 1, minHeight: 0, flexDirection: isMobile ? 'column' : 'row' }}>
        <Sider
          width={isMobile ? undefined : 200}
          theme="light"
          collapsed={isMobile}
          collapsedWidth={isMobile ? '100%' : undefined}
          style={{
            background: '#fff',
            borderRight: '1px solid #f0f0f0',
            ...(isMobile ? { height: 48 } : {}),
          }}
        >
          <Menu
            mode={isMobile ? 'horizontal' : 'inline'}
            style={{ height: '100%', borderInlineEnd: 'none', overflow: 'auto' }}
            items={menuItems}
            openKeys={isMobile ? undefined : openKeys}
            onOpenChange={(keys) => setOpenKeys(keys as string[])}
            selectedKeys={[selectedKey]}
            onClick={({ key }) => startTransition(() => navigate(key))}
          />
        </Sider>
        <Layout style={{ flexDirection: 'column' }}>
          <Content style={{ background: '#f0f2f5', padding: isMobile ? 8 : 16, overflow: 'auto' }}>
            <div style={{ minHeight: 'calc(100vh - 56px - 64px)' }}>
              <BreadcrumbNav />
              <Outlet />
            </div>
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
    <Suspense fallback={<PageSkeleton />}>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      {/* 036：公开表单提交页（匿名） */}
      <Route path="/f/:id" element={<PublicFormPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Shell />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/stats" replace />} />
        <Route path="leads" element={<LeadListPage />} />
        <Route path="leads/:id" element={<LeadDetailPage />} />
        <Route path="customers" element={<CustomerListPage />} />
        <Route path="customers/at-risk" element={<AtRiskCustomersPage />} />
        <Route path="customers/:id" element={<CustomerDetailPage />} />
        <Route path="contacts" element={<ContactListPage />} />
        <Route path="opportunities" element={<OpportunityListPage />} />
        <Route path="sales-opportunities" element={<SalesOpportunityListPage />} />
        <Route path="quotes" element={<QuoteListPage />} />
        <Route path="quotes/:id" element={<QuoteDetailPage />} />
        <Route path="contracts" element={<ContractListPage />} />
        <Route path="contracts/:id" element={<ContractDetailPage />} />
        <Route path="contract-templates" element={<ContractTemplateListPage />} />
        <Route path="orders" element={<OrderListPage />} />
        <Route path="orders/:id" element={<OrderDetailPage />} />
        <Route path="tasks" element={<TaskListPage />} />
        <Route path="usage-map" element={<UsageMapPage />} />
        <Route path="search" element={<SearchResultPage />} />
        <Route path="approvals" element={<ApprovalCenterPage />} />
        <Route path="approval-flows" element={<ApprovalFlowPage />} />
        <Route path="customer-merge" element={<DuplicateMergePage />} />
        <Route path="visits" element={<VisitListPage />} />
        <Route path="online-forms" element={<OnlineFormPage />} />
        <Route path="announcements" element={<AnnouncementPage />} />
        <Route path="invoices" element={<InvoiceListPage />} />
        <Route path="tasks/calendar" element={<TaskCalendarPage />} />
        <Route path="products" element={<ProductListPage />} />
        <Route path="marketing" element={<CampaignListPage />} />
        <Route path="marketing/email" element={<EmailMarketingPage />} />
        <Route path="marketing/roi" element={<ChannelRoiPage />} />
        <Route path="tickets" element={<TicketListPage />} />
        <Route path="tickets/:id" element={<TicketDetailPage />} />
        <Route path="knowledge" element={<KnowledgeArticleListPage />} />
        <Route path="sla-policies" element={<SlaPolicyListPage />} />
        <Route path="exports" element={<ExportCenterPage />} />
        <Route path="settings/custom-fields" element={<CustomFieldListPage />} />
        <Route path="stats" element={<DashboardPage />} />
        <Route path="stats/leaderboard" element={<TeamLeaderboardPage />} />
        <Route path="reports" element={<ReportCenterPage />} />
        <Route path="suggestions" element={<SuggestionCenterPage />} />
        <Route path="board" element={<KpiBoardPage />} />
        <Route path="users" element={<UserManagementPage />} />
        <Route path="roles" element={<RoleListPage />} />
        <Route path="tags" element={<TagSegmentPage />} />
        <Route path="departments" element={<DepartmentListPage />} />
        <Route path="workflows" element={<WorkflowRuleListPage />} />
        <Route path="workflows/logs" element={<WorkflowLogListPage />} />
        <Route path="audit-logs" element={<AuditLogPage />} />
        <Route path="recycle-bin" element={<RecycleBinPage />} />
        <Route path="account/password" element={<ChangePasswordPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </Suspense>
  )
}
