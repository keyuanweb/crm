import { useMemo } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Breadcrumb } from 'antd'

/** 菜单分组定义（与 App.tsx 路由分组对齐）。 */
const GROUPED_ROUTES: { group: string; routes: { path: string; name: string }[] }[] = [
  {
    group: '客户管理',
    routes: [
      { path: '/leads', name: '线索' },
      { path: '/customers', name: '客户' },
      { path: '/contacts', name: '联系人' },
    ],
  },
  {
    group: '销售管理',
    routes: [
      { path: '/opportunities', name: '商机' },
      { path: '/sales-opportunities', name: '销售机会' },
      { path: '/quotes', name: '报价单' },
    ],
  },
  {
    group: '交易管理',
    routes: [
      { path: '/contracts', name: '合同' },
      { path: '/orders', name: '订单' },
    ],
  },
  {
    group: '基础资料',
    routes: [
      { path: '/tasks', name: '任务' },
      { path: '/products', name: '产品' },
    ],
  },
  {
    group: '营销与服务',
    routes: [
      { path: '/marketing', name: '营销' },
      { path: '/tickets', name: '客户服务' },
      { path: '/knowledge', name: '知识库' },
    ],
  },
  {
    group: '数据分析',
    routes: [
      { path: '/exports', name: '导出中心' },
      { path: '/customers/at-risk', name: '流失预警' },
      { path: '/stats/leaderboard', name: '团队排行' },
      { path: '/reports', name: '自定义报表' },
      { path: '/suggestions', name: '智能建议' },
      { path: '/board', name: '数据大屏' },
    ],
  },
  {
    group: '系统管理',
    routes: [
      { path: '/users', name: '用户管理' },
      { path: '/departments', name: '部门' },
      { path: '/workflows', name: '工作流' },
      { path: '/sla-policies', name: 'SLA 策略' },
      { path: '/settings/custom-fields', name: '自定义字段' },
      { path: '/contract-templates', name: '合同模板' },
      { path: '/audit-logs', name: '审计日志' },
      { path: '/recycle-bin', name: '回收站' },
    ],
  },
]

/** 详情页路径段（匹配 /xxx/:id → 显示"详情"）。 */
const DETAIL_SEGMENTS = ['customers', 'leads', 'tickets', 'orders', 'quotes', 'contracts']

/** 面包屑导航：首页 / 分组 / 页面（详情页显示 列表名 / 详情）。 */
export default function BreadcrumbNav() {
  const location = useLocation()

  const items = useMemo(() => {
    const path = location.pathname
    if (path === '/stats') {
      return [{ title: '首页' }]
    }
    const items: { title: React.ReactNode }[] = [
      { title: <Link to="/stats">首页</Link> },
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
      items.push({ title: matchedGroup })
      // 详情页：在列表名后追加"详情"
      const isDetail = path !== matchedRoute.path && path.startsWith(matchedRoute.path + '/')
      const isNestedDetail =
        DETAIL_SEGMENTS.some((s) => path.startsWith('/' + s + '/')) && path !== matchedRoute.path
      if (matchedRoute.path !== '/stats') {
        items.push({ title: <Link to={matchedRoute.path}>{matchedRoute.name}</Link> })
      }
      if (isDetail || isNestedDetail) {
        items.push({ title: '详情' })
      }
    } else {
      // 未匹配（如 /account/password）：显示当前段
      items.push({ title: '当前页面' })
    }
    return items
  }, [location.pathname])

  return (
    <Breadcrumb
      items={items}
      style={{ marginBottom: 12, fontSize: 13 }}
    />
  )
}
