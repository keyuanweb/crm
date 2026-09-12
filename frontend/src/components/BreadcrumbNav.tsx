import { useMemo, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Breadcrumb } from 'antd'
import { menuLabel } from '../i18n/labelOf'
import { resolveTrail, type TrailSegment } from './breadcrumbTrail'

/**
 * 面包屑导航：首页 / 分组 / 页面（详情页再追加「详情」）。
 *
 * <p>分组、顺序与名称一律来自生成物 `MENU_MANIFEST`——「哪条路径产生哪些段」由
 * `breadcrumbTrail.ts` 的纯函数决定并被单测钉住，本组件只负责把分段渲染成 antd 的 `Breadcrumb`。
 *
 * <p>改造前这里自带两张手写表（分组/路径 + 路径/文案键），与生成物、侧边栏各说各话：
 * `/opportunity-stages` 三处都在、唯独这张表没有，于是渲染成「首页 / 当前页面」而无人报警；
 * 配置类页面的组名写「系统管理」（侧边栏是「流程配置」）；`/data-vision` 挂的是中文裸字面量。
 * 三者都是同一类故障——第二份菜单定义——故一并收口到生成物。
 */
export default function BreadcrumbNav() {
  const { t } = useTranslation()
  const location = useLocation()

  const items = useMemo(
    () => resolveTrail(location.pathname).map((segment) => ({ title: renderSegment(segment, t) })),
    [location.pathname, t],
  )

  return <Breadcrumb items={items} style={{ marginBottom: 12, fontSize: 13 }} />
}

/** 把一段分段渲染成节点：文案一律经 `menuLabel` 降级，缺键时显示权威中文名而非键名。 */
function renderSegment(segment: TrailSegment, t: (key: string) => string): ReactNode {
  switch (segment.kind) {
    case 'home': {
      const label = menuLabel(t, segment.i18nKey, segment.title)
      // `current`（即 `/stats` 自身）不渲染链接：指向当前页的链接是无效操作
      return segment.current ? label : <Link to={segment.path}>{label}</Link>
    }
    case 'group':
      return menuLabel(t, segment.i18nKey, segment.title)
    case 'item':
      return <Link to={segment.path}>{menuLabel(t, segment.i18nKey, segment.title)}</Link>
    case 'detail':
      return t('breadcrumb.detail')
    case 'unmatched':
      return t('breadcrumb.currentPage')
  }
}
