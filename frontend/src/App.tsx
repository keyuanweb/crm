import { Suspense, lazy, startTransition, useEffect, useRef, useState, type ReactNode } from 'react'
import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Avatar, Button, Dropdown, Layout, Menu, Spin, type MenuProps } from 'antd'
import {
  AlertOutlined,
  AuditOutlined,
  ApartmentOutlined,
  BarChartOutlined,
  BulbOutlined,
  CalendarOutlined,
  CompassOutlined,
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
  GlobalOutlined,
  PhoneOutlined,
  HomeOutlined,
  IdcardOutlined,
  KeyOutlined,
  LogoutOutlined,
  MailOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
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
  WalletOutlined,
  RocketOutlined,
  DesktopOutlined,
  DatabaseOutlined,
  SafetyOutlined,
} from '@ant-design/icons'
import { fetchMe, logout } from './services/authService'
import { menuKeyOf } from './constants/menuKeys'
import { MENU_MANIFEST, type MenuManifestGroup } from './constants/menuManifest'
import { resolveVisibleMenuKeys } from './constants/menuVisibility'
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
const OpportunityStagePage = lazy(() => import('./pages/settings/OpportunityStagePage'))
const DashboardPage = lazy(() => import('./pages/stats/DashboardPage'))
const TeamLeaderboardPage = lazy(() => import('./pages/stats/TeamLeaderboardPage'))
const ReportCenterPage = lazy(() => import('./pages/reports/ReportCenterPage'))
const SuggestionCenterPage = lazy(() => import('./pages/assistant/SuggestionCenterPage'))
const DataVisionPage = lazy(() => import('./pages/dataVision/DataVisionPage'))
const RecycleBinPage = lazy(() => import('./pages/recycle/RecycleBinPage'))
const RoleListPage = lazy(() => import('./pages/roles/RoleListPage'))
const UsageMapPage = lazy(() => import('./pages/map/UsageMapPage'))
const TagSegmentPage = lazy(() => import('./pages/tags/TagSegmentPage'))
const EmailMarketingPage = lazy(() => import('./pages/marketing/EmailMarketingPage'))
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
import GlobalSearch from './components/GlobalSearch'
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
const PersonalCenterPage = lazy(() => import('./pages/personal/PersonalCenterPage'))
const QuotaListPage = lazy(() => import('./pages/quotas/QuotaListPage'))
const QuotaBreakdownPage = lazy(() => import('./pages/quotas/QuotaBreakdownPage'))
const QuotaAchievementPage = lazy(() => import('./pages/quotas/QuotaAchievementPage'))
const QuotaVersionPage = lazy(() => import('./pages/quotas/QuotaVersionPage'))
const QuotaComparisonPage = lazy(() => import('./pages/quotas/QuotaComparisonPage'))
const QuotaCreatePage = lazy(() => import('./pages/quotas/QuotaCreatePage'))
const ScheduledExportListPage = lazy(() => import('./pages/exports/ScheduledExportListPage'))
const ScheduledExportCreatePage = lazy(() => import('./pages/exports/ScheduledExportCreatePage'))
const ScheduledExportExecutionHistoryPage = lazy(() => import('./pages/exports/ScheduledExportExecutionHistoryPage'))
const DataRetentionPolicyListPage = lazy(() => import('./pages/data-retention/DataRetentionPolicyListPage'))
const DataRetentionPolicyCreatePage = lazy(() => import('./pages/data-retention/DataRetentionPolicyCreatePage'))
const DataRetentionPolicyEditPage = lazy(() => import('./pages/data-retention/DataRetentionPolicyEditPage'))
const DataRetentionExecutionHistoryPage = lazy(() => import('./pages/data-retention/DataRetentionExecutionHistoryPage'))
const ComplianceExportPage = lazy(() => import('./pages/data-retention/ComplianceExportPage'))
const AuditLogPage = lazy(() => import('./pages/audit/AuditLogPage'))
const CampaignListPage = lazy(() => import('./pages/marketing/CampaignListPage'))
const ChannelRoiPage = lazy(() => import('./pages/marketing/ChannelRoiPage'))
const TicketListPage = lazy(() => import('./pages/tickets/TicketListPage'))
const TicketDetailPage = lazy(() => import('./pages/tickets/TicketDetailPage'))
const KnowledgeArticleListPage = lazy(() => import('./pages/knowledge/KnowledgeArticleListPage'))
const SlaPolicyListPage = lazy(() => import('./pages/sla/SlaPolicyListPage'))
const CustomFieldListPage = lazy(() => import('./pages/settings/CustomFieldListPage'))
const ExportCenterPage = lazy(() => import('./pages/exports/ExportCenterPage'))
const StageActionTemplatePage = lazy(() => import('./pages/playbook/StageActionTemplatePage'))
const ContractRenewalPage = lazy(() => import('./pages/contracts/ContractRenewalPage'))
const SatisfactionStatsPage = lazy(() => import('./pages/surveys/SatisfactionStatsPage'))
const CustomerPortalPage = lazy(() => import('./pages/portal/CustomerPortalPage'))
const EmailUnsubscribePage = lazy(() => import('./pages/email/EmailUnsubscribePage'))
const SlaCalendarPage = lazy(() => import('./pages/sla/SlaCalendarPage'))
const LandingPageListPage = lazy(() => import('./pages/landing/LandingPageListPage'))
const LandingPageView = lazy(() => import('./pages/landing/LandingPageView'))
const OpenPlatformPage = lazy(() => import('./pages/open/OpenPlatformPage'))
const FieldPermissionPage = lazy(() => import('./pages/settings/FieldPermissionPage'))
const CurrencyRatePage = lazy(() => import('./pages/settings/CurrencyRatePage'))
const IntegrationHubPage = lazy(() => import('./pages/settings/IntegrationHubPage'))
const CustomObjectListPage = lazy(() => import('./pages/custom-object/CustomObjectListPage'))
const CustomObjectRecordPage = lazy(() => import('./pages/custom-object/CustomObjectRecordPage'))
const CallRecordPage = lazy(() => import('./pages/calls/CallRecordPage'))
const MailSyncPage = lazy(() => import('./pages/mail/MailSyncPage'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'))
import NotificationCenter from './components/NotificationCenter'

/** 菜单项结构（复用 antd Menu items 元素类型，支持多级 submenu，041）。 */
type MenuItemLike = NonNullable<MenuProps['items']>[number]

/**
 * 路由表的一项：路径、图标，以及兜底名称。
 *
 * <p>`name` 只在「不占菜单位的子页面」上还会被用到——真正的菜单项名称一律取自生成物，
 * 因为生成物里的 `title` 才是与角色配置页一致的权威名称（FR-N09）。
 */
type MenuRouteEntry = { path: string; name: string; icon: ReactNode }

/**
 * 084：「借分组显示」的子页面——页面上有路由，但权威定义里没有对应的菜单项，
 * 只能挂到所属分组的菜单 key 上（`constants/menuKeys.ts` 的 `COARSE_ALIASES`）。
 *
 * <p>它们照旧是侧边栏里的项（这一条常被误读为「它们不进菜单」），所以必须与生成物的项一起渲染；
 * 位置用 `path -> 它跟在哪个菜单项之后` 表达，以保持改造前的顺序。
 * **集合被 `MenuRouteAlignmentTest` 钉成恰好这两条**，不因本次归位而扩大（规格边界情况）。
 */
const SUB_PAGE_AFTER_MENU_KEY: Record<string, string> = {
  '/marketing/roi': 'marketing',
  '/workflows/logs': 'workflows',
}

/**
 * 084：分组 → 图标。
 *
 * <p>这是**刻意保留**的前端本地映射之一：antd 图标是 JSX，进不了生成物，而它既不影响
 * 「有哪些菜单项」，也不影响「属于哪个分组」或「叫什么名字」——权威处改了分组名而这里没跟上，
 * `MenuRouteAlignmentTest` 会直接报错，所以它不构成菜单定义的第二个作者。
 *
 * <p>键与 `menuManifest.ts` 的分组 `i18nKey` 一一对应；缺一个的后果是该分组渲染成无图标的组。
 */
const GROUP_ICONS: Record<string, ReactNode> = {
  customer: <TeamOutlined />,
  sales: <FundOutlined />,
  deal: <WalletOutlined />,
  marketing: <RocketOutlined />,
  service: <CustomerServiceOutlined />,
  workbench: <DesktopOutlined />,
  data: <DatabaseOutlined />,
  admin: <SafetyOutlined />,
  config: <SettingOutlined />,
  audit: <AuditOutlined />,
}

/**
 * 084：在侧边栏渲染为**独立置顶项**而非分组的分组（「首页」）。
 *
 * <p>「首页」在权威定义里是一个分组（角色页就是这么勾选的），但 040 起侧边栏把它渲染成
 * 顶部的单个菜单项。这是表现层选择，不是菜单定义，故留在前端并由护栏钉成恰好这一条。
 */
const TOP_LEVEL_GROUP_I18N_KEYS = new Set(['home'])

/**
 * 所有懒加载页面 chunk 的预加载函数。
 * 挂载后在空闲时统一预热，点击菜单时 chunk 已在浏览器缓存中，
 * Suspense 直接渲染目标页，避免首次切换时 fallback 闪烁。
 * import() 是幂等的：首次调用下载并缓存模块，后续调用立即 resolve。
 */
const PRELOAD_PAGES: Array<() => Promise<unknown>> = [
  () => import('./pages/dataVision/DataVisionPage'),
  () => import('./pages/customers/CustomerListPage'),
  () => import('./pages/customers/CustomerDetailPage'),
  () => import('./pages/customers/AtRiskCustomersPage'),
  () => import('./pages/contacts/ContactListPage'),
  () => import('./pages/leads/LeadListPage'),
  () => import('./pages/leads/LeadDetailPage'),
  () => import('./pages/opportunities/OpportunityListPage'),
  () => import('./pages/sales-opportunities/SalesOpportunityListPage'),
  () => import('./pages/playbook/StageActionTemplatePage'),
  () => import('./pages/contracts/ContractRenewalPage'),
  () => import('./pages/surveys/SatisfactionStatsPage'),
  () => import('./pages/stats/DashboardPage'),
  () => import('./pages/stats/TeamLeaderboardPage'),
  () => import('./pages/reports/ReportCenterPage'),
  () => import('./pages/assistant/SuggestionCenterPage'),
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
  const { t, i18n } = useTranslation()
  const { user, setUser, clear, getAccessToken } = useAuthStore()
  const [siderCollapsed, setSiderCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const [booted, setBooted] = useState(false)
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)
  // 数据大屏页面：隐藏菜单和顶栏，全屏独占
  const isDataVision = location.pathname === '/data-vision'
  // 041：系统管理二级子组展开状态（受控 openKeys）
  const [openKeys, setOpenKeys] = useState<string[]>([])
  // 布局优化：内容区引用（路由切换滚动复位）
  const contentRef = useRef<HTMLDivElement>(null)

  // 布局优化：路由切换时内容区滚动复位到顶部
  useEffect(() => {
    contentRef.current?.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior })
  }, [location.pathname])

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
      path.startsWith('/sla-policies') ||
      path.startsWith('/opportunity-stages')
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

  // 060：切换语言并持久化
  const changeLanguage = async (lang: string) => {
    await i18n.changeLanguage(lang)
    localStorage.setItem('app_lang', lang)
  }

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
    { path: '/playbook', name: t('menu.playbook'), icon: <FundOutlined /> },
  ]
  const dealRoutes = [
    { path: '/contracts', name: '合同', icon: <FileProtectOutlined /> },
    { path: '/contract-renewal', name: t('menu.renewal'), icon: <FileTextOutlined /> },
    { path: '/orders', name: '订单', icon: <ProfileOutlined /> },
    { path: '/invoices', name: '发票', icon: <FileTextOutlined /> },
  ]
  const marketingRoutes = [
    { path: '/marketing', name: '营销活动', icon: <NotificationOutlined /> },
    { path: '/marketing/roi', name: '渠道 ROI', icon: <BarChartOutlined /> },
    { path: '/marketing/email', name: '邮件营销', icon: <MailOutlined /> },
    { path: '/email-unsubscribes', name: '邮件退订', icon: <MailOutlined /> },
    { path: '/online-forms', name: '在线表单', icon: <FormOutlined /> },
    { path: '/landing-pages', name: '落地页', icon: <FormOutlined /> },
  ]
  const serviceRoutes = [
    { path: '/tickets', name: '客户服务', icon: <CustomerServiceOutlined /> },
    { path: '/knowledge', name: '知识库', icon: <FileTextOutlined /> },
    { path: '/announcements', name: '公告管理', icon: <NotificationOutlined /> },
    { path: '/approvals', name: '我的审批', icon: <AuditOutlined /> },
    { path: '/portal', name: '客户门户', icon: <CustomerServiceOutlined /> },
    { path: '/satisfaction', name: '满意度调查', icon: <AuditOutlined /> },
    { path: '/sla-calendar', name: 'SLA日历', icon: <CalendarOutlined /> },
  ]
  const workbenchRoutes = [
    { path: '/tasks', name: '任务', icon: <CalendarOutlined /> },
    { path: '/suggestions', name: '智能建议', icon: <BulbOutlined /> },
    { path: '/data-vision', name: '酷炫大屏', icon: <ThunderboltOutlined /> },
    { path: '/call-records', name: '通话记录', icon: <PhoneOutlined /> },
    { path: '/mail-sync', name: '邮件同步', icon: <MailOutlined /> },
  ]
  const dataRoutes = [
    { path: '/reports', name: '自定义报表', icon: <BarChartOutlined /> },
    { path: '/stats/leaderboard', name: '团队排行', icon: <RiseOutlined /> },
    { path: '/quotas', name: '销售配额', icon: <RiseOutlined /> },
    { path: '/exports', name: '导出中心', icon: <DownloadOutlined /> },
    { path: '/exports/scheduled', name: '定时导出', icon: <DownloadOutlined /> },
  ]
  // 首页置顶（首位独立菜单项，指向统计仪表盘 /stats）
  const statsRoute = { path: '/stats', name: t('menu.home'), icon: <HomeOutlined /> }
  // 042：系统管理扁平化——三个一级分组（系统管理/流程与配置/审计与维护）
  const adminOrgRoutes = [
    { path: '/users', name: '用户管理', icon: <UserOutlined /> },
    { path: '/roles', name: '角色权限', icon: <SafetyCertificateOutlined /> },
    { path: '/departments', name: '部门', icon: <ApartmentOutlined /> },
    { path: '/field-permissions', name: '字段权限', icon: <SafetyCertificateOutlined /> },
    { path: '/currencies', name: '多币种', icon: <FundOutlined /> },
  ]
  const adminConfigRoutes = [
    { path: '/workflows', name: '工作流', icon: <ThunderboltOutlined /> },
    { path: '/workflows/logs', name: '工作流日志', icon: <AuditOutlined /> },
    { path: '/approval-flows', name: '审批流配置', icon: <AuditOutlined /> },
    { path: '/sla-policies', name: 'SLA 策略', icon: <AuditOutlined /> },
    { path: '/opportunity-stages', name: '商机阶段', icon: <DeploymentUnitOutlined /> },
    { path: '/contract-templates', name: '合同模板', icon: <FileTextOutlined /> },
    { path: '/settings/custom-fields', name: '自定义字段', icon: <SettingOutlined /> },
    { path: '/custom-objects', name: '自定义对象', icon: <ApartmentOutlined /> },
    { path: '/open-platform', name: '开放平台', icon: <ApartmentOutlined /> },
    { path: '/integration-hub', name: '集成中心', icon: <ApartmentOutlined /> },
  ]
  const adminAuditRoutes = [
    { path: '/tags', name: '标签与细分', icon: <TagsOutlined /> },
    { path: '/audit-logs', name: '审计日志', icon: <AuditOutlined /> },
    { path: '/recycle-bin', name: '回收站', icon: <DeleteOutlined /> },
    { path: '/data-retention', name: '数据保留', icon: <FileProtectOutlined /> },
  ]
  const adminRoutes = [...adminOrgRoutes, ...adminConfigRoutes, ...adminAuditRoutes]
  // 084：菜单的**分组、顺序与名称**一律由生成物 `MENU_MANIFEST` 派生（单一真相源）。
  // 本文件只留下三样东西：路由表（path → 懒加载页面 + 图标）、分组图标映射，
  // 以及两个「借分组显示」的子页面——三者都不是菜单定义的第二个作者，理由见文件顶部的常量注释。
  /** 全部路由（含不占菜单位的子页面），供高亮当前项与别名查表使用。 */
  const menuRoutes = [
    statsRoute,
    ...customerRoutes,
    ...salesRoutes,
    ...dealRoutes,
    ...marketingRoutes,
    ...serviceRoutes,
    ...workbenchRoutes,
    ...dataRoutes,
    // 084 FR-N01：原先这里是 `...(isAdmin ? adminRoutes : [])`——一道按角色名整组开关的硬门，
    // 与 role_menu 授权是两套互不知情的开关，导致 V75 授给 5 个预置角色的 16 个 (角色,键) 对
    // 永远渲染不出来。现在路由对所有人可见，**能不能看到由授权决定**（见 menuVisibility.ts）。
    ...adminRoutes,
  ]
  /** path → 路由项。 */
  const routeByPath = new Map<string, MenuRouteEntry>(menuRoutes.map((r) => [r.path, r]))
  /**
   * 菜单 key → 路由项。
   *
   * <p>别名子页面与正式项会撞上同一个 key（`/marketing/roi` 与 `/marketing` 都归 `marketing`），
   * 故**精确同名者优先**：没有这一条，别名的 path/图标会把正式项挤掉。
   */
  const routeByMenuKey = new Map<string, MenuRouteEntry>()
  for (const r of menuRoutes) {
    const key = menuKeyOf(r.path)
    if (!routeByMenuKey.has(key) || r.path === `/${key}`) routeByMenuKey.set(key, r)
  }
  /**
   * 取菜单文案：缺键时降级为**权威中文名**。
   *
   * <p>`t()` 在缺键时返回键名本身（如 `menu.xxx`）——渲染出来比不翻译更糟。
   * 生成物里的 `title` 正是权威处的中文名，直接拿它降级：既不显示键名，
   * 也与角色配置页显示的名字一致（FR-N09 与边界情况「缺文案降级」）。
   */
  const labelOf = (i18nKey: string, title: string) => {
    const key = `menu.${i18nKey}`
    const text = t(key)
    return text === key ? title : text
  }
  /** 递归拍平菜单项（移动端不支持分组/二级子组，全部拍平为普通项）。 */
  const flattenMenuItems = (items: MenuItemLike[]): MenuItemLike[] =>
    items.flatMap((item) => {
      if (item && 'children' in item && Array.isArray(item.children)) {
        return flattenMenuItems(item.children as MenuItemLike[])
      }
      return [item]
    })
  // 028：按角色可见菜单过滤（ADMIN 全量；其他角色按 user.menus）。
  // 1.5：path → menuKey 的映射挪到 constants/menuKeys.ts——原先那张 40 条的手写表把
  // /invoices 归并成 'orders'、/visits 归并成 'sales'（MENU_TREE 里根本没这个 key），
  // 结果是「勾了也看不到菜单」。现在默认实现与 MENU_TREE 逐字对齐，并由
  // constants/menuKeys.test.ts 拿后端 RoleConstants.java 反过来校验。
  // 084 FR-N01–N04：可见集合由纯函数解出（ADMIN 全量兜底；其余按 `user.menus` ∩ 清单键）。
  // 这里**不做**任何按角色名的整组开关——那是被 084 根除的故障形态，单测见 menuVisibility.test.ts。
  const visibleMenus = resolveVisibleMenuKeys(user?.role, user?.menus)
  const isMenuVisible = (menuKey: string) => visibleMenus.has(menuKey)
  /** 一个分组里实际要渲染的子项（已过可见性过滤；空数组表示该组整体不渲染）。 */
  const groupItems = (group: MenuManifestGroup): MenuItemLike[] => {
    const children: MenuItemLike[] = []
    for (const item of group.items) {
      if (!isMenuVisible(item.menuKey)) continue
      const route = routeByMenuKey.get(item.menuKey)
      if (route) {
        const label = labelOf(item.i18nKey, item.title)
        children.push({ key: route.path, icon: route.icon, label })
      }
      // 借分组显示的子页面紧跟它所借的那一项之后（位置与改造前一致）。
      // 它们的可见性与所借的项同源（同一 menu key），所以放在这个判断之内。
      for (const [aliasPath, anchor] of Object.entries(SUB_PAGE_AFTER_MENU_KEY)) {
        if (anchor !== item.menuKey) continue
        const aliasRoute = routeByPath.get(aliasPath)
        if (aliasRoute) {
          children.push({ key: aliasRoute.path, icon: aliasRoute.icon, label: aliasRoute.name })
        }
      }
    }
    return children
  }
  const groupedMenuItems: MenuItemLike[] = MENU_MANIFEST.flatMap((group) => {
    if (TOP_LEVEL_GROUP_I18N_KEYS.has(group.i18nKey)) return []
    const children = groupItems(group)
    if (!children.length) return []
    return [
      {
        type: 'submenu' as const,
        key: `g-${group.i18nKey}`,
        label: labelOf(group.i18nKey, group.title),
        icon: GROUP_ICONS[group.i18nKey],
        children,
      },
    ]
  })
  /**
   * 置顶项（「首页」）：来自生成物里被标记为置顶的分组，**不参与可见性过滤**——
   * 改造前 `statsMenuItem` 就是无条件拼在最前面的，行为保持不变。
   */
  const topLevelItems: MenuItemLike[] = MENU_MANIFEST.filter((g) =>
    TOP_LEVEL_GROUP_I18N_KEYS.has(g.i18nKey),
  ).flatMap((group) =>
    group.items.flatMap((item) => {
      const route = routeByMenuKey.get(item.menuKey)
      if (!route) return []
      const label = labelOf(item.i18nKey, item.title)
      return [{ key: route.path, icon: route.icon, label }]
    }),
  )
  // 所有分组默认收起（FR-S14 默认行为）；点击分组标签可收起/展开
  // 统计分析置顶为独立菜单项；移动端横向菜单不支持分组，递归拍平为普通项
  const menuItems = isMobile
    ? [...topLevelItems, ...flattenMenuItems(groupedMenuItems)]
    : [...topLevelItems, ...groupedMenuItems]
  // 高亮当前页对应的菜单项（详情页按前缀匹配最长路径）
  const selectedKey =
    menuRoutes
      .map((r) => r.path)
      .filter((p) => location.pathname === p || location.pathname.startsWith(p + '/'))
      .sort((a, b) => b.length - a.length)[0] ?? '/stats'
  const { Header, Sider, Content, Footer } = Layout

  // 判断是否为数据大屏页面（使用更精确的路径匹配）
  const shouldHideMenu = isDataVision || location.pathname.startsWith('/data-vision')

  return (
    shouldHideMenu ? (
      <div style={{ width: '100vw', height: '100vh', overflow: 'auto' }}>
        <Outlet />
      </div>
    ) : (
      <Layout style={{ height: '100vh', overflow: 'hidden' }}>
        {/* 027：PWA 安装提示 */}
        <InstallPrompt />
        {/* 顶部栏（ant-layout-header，横跨 100%）：左=系统名称，右=当前用户下拉，内容垂直居中 */}
        <Header
          style={{
            width: '100%',
            height: 48,
            lineHeight: 1,
            padding: '0 24px',
            background: '#fff',
            borderBottom: '1px solid #f0f0f0',
            boxShadow: '0 1px 4px rgba(0, 0, 0, 0.04)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          {/* 左侧：折叠按钮 + Logo + 系统名称 */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {!isMobile && (
              <Button
                type="text"
                icon={siderCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setSiderCollapsed((c) => !c)}
                aria-label="折叠/展开菜单"
                style={{ padding: 4 }}
              />
            )}
            <TeamOutlined style={{ fontSize: 20, color: '#1677ff' }} />
            <span style={{ fontSize: 16, fontWeight: 600, color: '#1f1f1f' }}>
              {t('app.title')}
            </span>
          </div>
          {/* 右侧：通知 + 用户头像 */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {!isMobile && <GlobalSearch />}
            <NotificationCenter />
            <Dropdown
              menu={{
                items: [
                  { key: 'personal-center', icon: <UserOutlined />, label: t('app.personalCenter'), onClick: () => navigate('/personal-center') },
                  { key: 'usage-map', icon: <CompassOutlined />, label: t('app.usageMap'), onClick: () => navigate('/usage-map') },
                  { key: 'password', icon: <KeyOutlined />, label: t('app.changePassword'), onClick: () => navigate('/account/password') },
                  { type: 'divider' },
                  { key: 'lang-zh', icon: <GlobalOutlined />, label: '中文', onClick: () => void changeLanguage('zh-CN') },
                  { key: 'lang-en', icon: <GlobalOutlined />, label: 'English', onClick: () => void changeLanguage('en') },
                  { type: 'divider' },
                  { key: 'logout', icon: <LogoutOutlined />, label: t('app.logout'), danger: true, onClick: onLogout },
                ],
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                <Avatar style={{ backgroundColor: '#1677ff' }} size="small">
                  {(user?.displayName ?? user?.username ?? '?').charAt(0).toUpperCase()}
                </Avatar>
              </div>
            </Dropdown>
          </div>
        </Header>

        {/* header 下方：左侧菜单栏 + 右侧内容区（窄屏折叠菜单为顶部横向滚动） */}
        <Layout style={{ flex: 1, minHeight: 0, flexDirection: isMobile ? 'column' : 'row' }}>
          <Sider
            width={isMobile ? undefined : 200}
            theme="light"
            collapsed={isMobile ? false : siderCollapsed}
            collapsedWidth={isMobile ? undefined : 64}
            style={{
              background: '#fafbfc',
              borderRight: '1px solid #e8e8e8',
              ...(isMobile ? { height: 48 } : {}),
            }}
          >
            <Menu
              mode={isMobile ? 'horizontal' : 'inline'}
              style={{ 
                height: '100%', 
                borderInlineEnd: 'none', 
                overflow: 'auto',
                background: 'transparent',
                scrollbarWidth: 'none', /* Firefox */
                msOverflowStyle: 'none', /* IE 10+ */
              }}
              items={menuItems}
              openKeys={isMobile ? undefined : openKeys}
              onOpenChange={(keys) => setOpenKeys(keys as string[])}
              selectedKeys={[selectedKey]}
              onClick={({ key }) => startTransition(() => navigate(key))}
              theme="light"
            />
          </Sider>
          <Layout style={{ flexDirection: 'column' }}>
            <Content
              ref={contentRef}
              className="page-scroll"
              style={{ 
                background: '#f0f2f5', 
                padding: isMobile ? 12 : '20px 24px', 
                overflow: 'auto' 
              }}
            >
              <div className="page-container" style={{ minHeight: 'calc(100vh - 48px - 40px)' }}>
                <div style={{ marginBottom: 12 }}>
                  <BreadcrumbNav />
                </div>
                {/* 布局优化：路由切换滚动复位 + 淡入过渡 */}
                <div key={location.pathname} className="page-fade">
                  <Outlet />
                </div>
              </div>
            </Content>
            <Footer
              style={{
                textAlign: 'center',
                padding: '8px 24px',
                background: '#f0f2f5',
                color: '#bfbfbf',
                fontSize: 12,
                borderTop: '1px solid #e8e8e8',
              }}
            >
              © {new Date().getFullYear()} {t('app.title')}
            </Footer>
          </Layout>
        </Layout>
      </Layout>
    )
  )
}

export default function App() {
  return (
    <Suspense fallback={<PageSkeleton />}>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      {/* 036：公开表单提交页（匿名） */}
      <Route path="/f/:id" element={<PublicFormPage />} />
      {/* 050：客户自助门户（公开，无需登录） */}
      <Route path="/portal" element={<CustomerPortalPage />} />
      {/* 053：托管落地页公开渲染（无需登录） */}
      <Route path="/lp/:id" element={<LandingPageView />} />
      {/* 数据大屏页面：独立路由，不嵌套在 Shell 中，确保完全隐藏菜单和顶栏 */}
      <Route
        path="/data-vision"
        element={
          <RequireAuth>
            <DataVisionPage />
          </RequireAuth>
        }
      />
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
        <Route path="playbook" element={<StageActionTemplatePage />} />
        <Route path="quotes" element={<QuoteListPage />} />
        <Route path="quotes/:id" element={<QuoteDetailPage />} />
        <Route path="contracts" element={<ContractListPage />} />
        <Route path="contract-renewal" element={<ContractRenewalPage />} />
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
        <Route path="landing-pages" element={<LandingPageListPage />} />
        <Route path="announcements" element={<AnnouncementPage />} />
        <Route path="invoices" element={<InvoiceListPage />} />
        <Route path="tasks/calendar" element={<TaskCalendarPage />} />
        <Route path="products" element={<ProductListPage />} />
        <Route path="marketing" element={<CampaignListPage />} />
        <Route path="marketing/email" element={<EmailMarketingPage />} />
        <Route path="email-unsubscribes" element={<EmailUnsubscribePage />} />
        <Route path="marketing/roi" element={<ChannelRoiPage />} />
        <Route path="tickets" element={<TicketListPage />} />
        <Route path="satisfaction" element={<SatisfactionStatsPage />} />
        <Route path="tickets/:id" element={<TicketDetailPage />} />
        <Route path="knowledge" element={<KnowledgeArticleListPage />} />
        <Route path="sla-policies" element={<SlaPolicyListPage />} />
        <Route path="opportunity-stages" element={<OpportunityStagePage />} />
        <Route path="sla-calendar" element={<SlaCalendarPage />} />
        <Route path="open-platform" element={<OpenPlatformPage />} />
        <Route path="field-permissions" element={<FieldPermissionPage />} />
        <Route path="currencies" element={<CurrencyRatePage />} />
        <Route path="integration-hub" element={<IntegrationHubPage />} />
        <Route path="custom-objects" element={<CustomObjectListPage />} />
        <Route path="custom-objects/:id/records" element={<CustomObjectRecordPage />} />
        <Route path="exports" element={<ExportCenterPage />} />
        <Route path="settings/custom-fields" element={<CustomFieldListPage />} />
        <Route path="stats" element={<DashboardPage />} />
        <Route path="stats/leaderboard" element={<TeamLeaderboardPage />} />
        <Route path="reports" element={<ReportCenterPage />} />
        <Route path="suggestions" element={<SuggestionCenterPage />} />
        <Route path="call-records" element={<CallRecordPage />} />
        <Route path="mail-sync" element={<MailSyncPage />} />
        <Route path="users" element={<UserManagementPage />} />
        <Route path="roles" element={<RoleListPage />} />
        <Route path="tags" element={<TagSegmentPage />} />
        <Route path="departments" element={<DepartmentListPage />} />
        <Route path="workflows" element={<WorkflowRuleListPage />} />
        <Route path="workflows/logs" element={<WorkflowLogListPage />} />
        <Route path="audit-logs" element={<AuditLogPage />} />
        <Route path="recycle-bin" element={<RecycleBinPage />} />
        <Route path="account/password" element={<ChangePasswordPage />} />
        <Route path="personal-center" element={<PersonalCenterPage />} />
        <Route path="quotas" element={<QuotaListPage />} />
        <Route path="quotas/:id/breakdown" element={<QuotaBreakdownPage />} />
        <Route path="quotas/:id/achievement" element={<QuotaAchievementPage />} />
        <Route path="quotas/:id/versions" element={<QuotaVersionPage />} />
        <Route path="quotas/comparison" element={<QuotaComparisonPage />} />
        <Route path="quotas/create" element={<QuotaCreatePage />} />
        <Route path="exports/scheduled" element={<ScheduledExportListPage />} />
        <Route path="exports/scheduled/create" element={<ScheduledExportCreatePage />} />
        <Route path="exports/scheduled/:id/executions" element={<ScheduledExportExecutionHistoryPage />} />
        <Route path="data-retention" element={<DataRetentionPolicyListPage />} />
        <Route path="data-retention/create" element={<DataRetentionPolicyCreatePage />} />
        <Route path="data-retention/:id/executions" element={<DataRetentionExecutionHistoryPage />} />
        <Route path="data-retention/:id/edit" element={<DataRetentionPolicyEditPage />} />
        <Route path="data-retention/compliance-export" element={<ComplianceExportPage />} />
        {/* 布局优化：Shell 内未知路径 → 带菜单的 404 */}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
    </Suspense>
  )
}
