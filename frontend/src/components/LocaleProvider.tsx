/**
 * 语言/地区上下文提供者（antd + pro-components + dayjs 三方 locale 联动）。
 *
 * 从 `src/main.tsx` 拆出：main.tsx 是入口（只做挂载、不导出任何东西），
 * 而 Fast Refresh 要求「一个文件要么只导出组件、要么什么都不导出」——
 * 入口里声明组件会让整棵树的 HMR 退化为整页刷新（react-refresh/only-export-components）。
 */

import React, { useMemo } from 'react'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import enUS from 'antd/locale/en_US'
import { ProConfigProvider, zhCNIntl, enUSIntl } from '@ant-design/pro-components'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/en'
import { useTranslation } from 'react-i18next'

export default function LocaleProvider({ children }: { children: React.ReactNode }) {
  const { i18n } = useTranslation()
  const lang = i18n.language || 'zh-CN'
  const isEnglish = lang.startsWith('en')

  const locale = useMemo(() => (isEnglish ? enUS : zhCN), [isEnglish])

  const proLocale = useMemo(() => (isEnglish ? enUSIntl : zhCNIntl), [isEnglish])

  React.useEffect(() => {
    dayjs.locale(isEnglish ? 'en' : 'zh-cn')
  }, [isEnglish])

  return (
    <ConfigProvider locale={locale}>
      <ProConfigProvider intl={proLocale}>
        {children}
      </ProConfigProvider>
    </ConfigProvider>
  )
}
