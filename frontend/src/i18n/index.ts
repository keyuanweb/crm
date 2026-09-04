import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zhCN from './zh-CN'
import en from './en'

/** 语言偏好 key（localStorage）。 */
export const LANG_STORAGE_KEY = 'app_lang'

const savedLang = localStorage.getItem(LANG_STORAGE_KEY) || 'zh-CN'

// 验证模块是否正确导入
console.log('[i18n] zhCN keys:', Object.keys(zhCN as any).slice(0, 5))
console.log('[i18n] en keys:', Object.keys(en as any).slice(0, 5))
console.log('[i18n] zhCN.auditLog:', (zhCN as any).auditLog)
console.log('[i18n] en.auditLog:', (en as any).auditLog)

i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    en: { translation: en },
  },
  lng: savedLang,
  fallbackLng: 'zh-CN',
  interpolation: { escapeValue: false },
})

export default i18n
