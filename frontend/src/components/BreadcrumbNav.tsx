import { useMemo } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Breadcrumb } from 'antd'

/** 菜单分组定义（与 App.tsx 路由分组对齐）。 */
const GROUPED_ROUTES: { group: string; routes: { path: string; name: string }[] }[] = [
  {
    group: 'customerManagement',
    routes: [
      { path: '/leads', name: 'leads' },
      { path: '/customers', name: 'customers' },
      { path: '/contacts', name: 'contacts' },
      { path: '/customer-merge', name: 'customerMerge' },
      { path: '/customers/at-risk', name: 'atRiskCustomers' },
    ],
  },
  {
    group: 'salesManagement',
    routes: [
      { path: '/opportunities', name: 'opportunities' },
      { path: '/sales-opportunities', name: 'salesOpportunities' },
      { path: '/quotes', name: 'quotes' },
      { path: '/visits', name: 'visits' },
      { path: '/products', name: 'products' },
      { path: '/playbook', name: 'playbook' },
    ],
  },
  {
    group: 'dealManagement',
    routes: [
      { path: '/contracts', name: 'contracts' },
      { path: '/contract-renewal', name: 'contractRenewal' },
      { path: '/orders', name: 'orders' },
      { path: '/invoices', name: 'invoices' },
    ],
  },
  {
    group: 'marketingAndService',
    routes: [
      { path: '/marketing', name: 'marketing' },
      { path: '/marketing/email', name: 'emailMarketing' },
      { path: '/email-unsubscribes', name: 'emailUnsubscribes' },
      { path: '/online-forms', name: 'onlineForms' },
      { path: '/landing-pages', name: 'landingPages' },
      { path: '/tickets', name: 'tickets' },
      { path: '/knowledge', name: 'knowledge' },
      { path: '/announcements', name: 'announcements' },
      { path: '/approvals', name: 'approvals' },
      { path: '/portal', name: 'portal' },
      { path: '/satisfaction', name: 'satisfaction' },
      { path: '/sla-calendar', name: 'slaCalendar' },
    ],
  },
  {
    group: 'basicData',
    routes: [
      { path: '/tasks', name: 'tasks' },
      { path: '/suggestions', name: 'suggestions' },
      { path: '/data-vision', name: 'dataVision' },
      { path: '/call-records', name: 'callRecords' },
      { path: '/mail-sync', name: 'mailSync' },
    ],
  },
  {
    group: 'dataAnalysis',
    routes: [
      { path: '/reports', name: 'reports' },
      { path: '/stats/leaderboard', name: 'leaderboard' },
      { path: '/exports', name: 'exports' },
    ],
  },
  {
    group: 'systemManagement',
    routes: [
      { path: '/users', name: 'users' },
      { path: '/roles', name: 'roles' },
      { path: '/departments', name: 'departments' },
      { path: '/field-permissions', name: 'fieldPermissions' },
      { path: '/currencies', name: 'currencies' },
      { path: '/workflows', name: 'workflows' },
      { path: '/approval-flows', name: 'approvalFlows' },
      { path: '/sla-policies', name: 'slaPolicies' },
      { path: '/contract-templates', name: 'contractTemplates' },
      { path: '/settings/custom-fields', name: 'customFields' },
      { path: '/custom-objects', name: 'customObjects' },
      { path: '/open-platform', name: 'openPlatform' },
      { path: '/integration-hub', name: 'integrationHub' },
      { path: '/tags', name: 'tags' },
      { path: '/audit-logs', name: 'auditLogs' },
      { path: '/recycle-bin', name: 'recycleBin' },
    ],
  },
]

/** 详情页路径段（匹配 /xxx/:id → 显示"详情"）。 */
const DETAIL_SEGMENTS = ['customers', 'leads', 'tickets', 'orders', 'quotes', 'contracts']

/** 菜单项 i18n key 映射（path → menuKey）。 */
const MENU_KEY_MAP: Record<string, string> = {
  '/leads': 'menu.leads',
  '/customers': 'menu.customers',
  '/contacts': 'menu.contacts',
  '/customer-merge': 'menu.customerMerge',
  '/customers/at-risk': 'menu.atRisk',
  '/opportunities': 'menu.opportunities',
  '/sales-opportunities': 'menu.salesOpportunities',
  '/quotes': 'menu.quotes',
  '/visits': 'menu.visits',
  '/products': 'menu.products',
  '/playbook': 'menu.playbook',
  '/contracts': 'menu.contracts',
  '/contract-renewal': 'menu.renewal',
  '/orders': 'menu.orders',
  '/invoices': 'menu.invoices',
  '/marketing': 'menu.marketing',
  '/marketing/email': 'menu.emailMarketing',
  '/email-unsubscribes': 'menu.emailUnsubscribes',
  '/online-forms': 'menu.onlineForms',
  '/landing-pages': 'menu.landingPages',
  '/tickets': 'menu.tickets',
  '/knowledge': 'menu.knowledge',
  '/announcements': 'menu.announcements',
  '/approvals': 'menu.approvals',
  '/portal': 'menu.portal',
  '/satisfaction': 'menu.satisfaction',
  '/sla-calendar': 'menu.slaCalendar',
  '/tasks': 'menu.tasks',
  '/suggestions': 'menu.suggestions',
  '/data-vision': '酷炫大屏',
  '/call-records': 'menu.callRecords',
  '/mail-sync': 'menu.mailSync',
  '/reports': 'menu.reports',
  '/stats/leaderboard': 'menu.leaderboard',
  '/exports': 'menu.exports',
  '/users': 'menu.users',
  '/roles': 'menu.roles',
  '/departments': 'menu.departments',
  '/field-permissions': 'menu.fieldPermissions',
  '/currencies': 'menu.currencies',
  '/workflows': 'menu.workflows',
  '/approval-flows': 'menu.approvalFlows',
  '/sla-policies': 'menu.slaPolicies',
  '/contract-templates': 'menu.contractTemplates',
  '/settings/custom-fields': 'menu.customFields',
  '/custom-objects': 'menu.customObjects',
  '/open-platform': 'menu.openPlatform',
  '/integration-hub': 'menu.integrationHub',
  '/tags': 'menu.tags',
  '/audit-logs': 'menu.auditLogs',
  '/recycle-bin': 'menu.recycleBin',
}

/** 面包屑导航：首页 / 分组 / 页面（详情页显示 列表名 / 详情）。 */
export default function BreadcrumbNav() {
  const { t } = useTranslation()
  const location = useLocation()

  const items = useMemo(() => {
    const path = location.pathname
    if (path === '/stats') {
      return [{ title: t('menu.home') }]
    }
    const items: { title: React.ReactNode }[] = [
      { title: <Link to="/stats">{t('menu.home')}</Link> },
    ]

    // 匹配分组与子路由（最长前缀优先）
    let matchedGroup: string | null = null
    let matchedRoute: { path: string; name: string } | null = null
    for (const g of GROUPED_ROUTES) {
      for (const r of g.routes) {
        if (path === r.path || path.startsWith(r.path + '/')) {
          if (!matchedRoute || r.path.length > matchedRoute.path.length) {
            matchedGroup = g.group
            matchedRoute = r
          }
        }
      }
    }

    if (matchedGroup && matchedRoute) {
      items.push({ title: t(`pages.breadcrumbGroup.${matchedGroup}`) })
      // 详情页：在列表名后追加"详情"
      const isDetail = path !== matchedRoute.path && path.startsWith(matchedRoute.path + '/')
      const isNestedDetail =
        DETAIL_SEGMENTS.some((s) => path.startsWith('/' + s + '/')) && path !== matchedRoute.path
      if (matchedRoute.path !== '/stats') {
        const menuKey = MENU_KEY_MAP[matchedRoute.path]
        items.push({ title: <Link to={matchedRoute.path}>{t(menuKey || matchedRoute.name)}</Link> })
      }
      if (isDetail || isNestedDetail) {
        items.push({ title: t('breadcrumb.detail') })
      }
    } else {
      // 未匹配（如 /account/password）：显示当前段
      items.push({ title: t('breadcrumb.currentPage') })
    }
    return items
  }, [location.pathname, t])

  return (
    <Breadcrumb
      items={items}
      style={{ marginBottom: 12, fontSize: 13 }}
    />
  )
}
