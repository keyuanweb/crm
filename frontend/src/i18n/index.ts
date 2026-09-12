import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zhCN from './zh-CN'
import en from './en'

/** 语言偏好 key（localStorage）。 */
export const LANG_STORAGE_KEY = 'app_lang'

const savedLang = localStorage.getItem(LANG_STORAGE_KEY) || 'zh-CN'

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
