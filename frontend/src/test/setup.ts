import { afterEach, vi } from 'vitest'
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'

// RTL 自动清理（未启用 vitest globals 时需手动注册）
afterEach(() => cleanup())

// Node ≥22 的实验性 localStorage 在 vitest + jsdom 下可能损坏（--localstorage-file 警告），
// 提供内存实现以保证测试稳定性。
const memoryStorage = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => (key in store ? store[key] : null),
    setItem: (key: string, value: string) => {
      store[key] = String(value)
    },
    removeItem: (key: string) => {
      delete store[key]
    },
    clear: () => {
      store = {}
    },
    key: (index: number) => Object.keys(store)[index] ?? null,
    get length() {
      return Object.keys(store).length
    },
  } as Storage
})()

Object.defineProperty(globalThis, 'localStorage', {
  value: memoryStorage,
  configurable: true,
  writable: true,
})

// antd v5（Grid/ProLayout 响应式）依赖 matchMedia，jsdom 未实现
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

// ProLayout/ProTable 依赖 ResizeObserver，jsdom 未实现
class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = ResizeObserverMock as never

// App 在每次路由切换时对内容区调用 Element.scrollTo（App.tsx 的滚动复位），jsdom 未实现该方法
// → 抛 TypeError 且成为测试运行后的「未处理异常」。与上面几条同属 jsdom 缺口；只有会发生路由跳转的
// 用例才会踩到，故此前未被发现。
if (typeof Element.prototype.scrollTo !== 'function') {
  Element.prototype.scrollTo = (() => {}) as never
}

// antd rc-table 测量滚动条时调用 getComputedStyle(elt, pseudoElt)，jsdom 对伪元素参数未实现，
// 这里忽略伪元素参数避免 "Not implemented: window.computedStyle" 报错。
const baseGetComputedStyle = window.getComputedStyle.bind(window)
window.getComputedStyle = ((elt: Element) => baseGetComputedStyle(elt)) as typeof window.getComputedStyle

// 测试环境禁用真实网络：jsdom 的 XHR 真实连接会失败并产生未处理拒绝（AggregateError）噪音。
// 所有被测试代码应通过 mock 服务层，未 mock 到的请求在此静默挂起而非抛错。
class XhrNoop {
  readyState = 0
  status = 0
  responseText = ''
  onreadystatechange: ((this: XMLHttpRequest, ev: Event) => unknown) | null = null
  open(): void {}
  send(): void {}
  setRequestHeader(): void {}
  abort(): void {}
  getAllResponseHeaders(): string {
    return ''
  }
  getResponseHeader(): string | null {
    return null
  }
}
Object.defineProperty(window, 'XMLHttpRequest', { writable: true, value: XhrNoop })

// react-i18next mock：测试中 t(key) 仍然返回 key 本身（既有断言均以此为准，改成中文文案会
// 大面积改测试），但**先校验该键在真实资源里存在**——缺键此前是零成本的：t() 原样返回键名，
// 界面渲染出 `pages.ticket.list.colStatus` 而所有测试依旧全绿。缺键现在直接抛错。
// 此 mock 会被 Vitest 提升到文件顶部，确保在组件导入前已生效。
vi.mock('react-i18next', async () => {
  const zhCN = (await import('../i18n/zh-CN')).default as Record<string, unknown>

  const lookup = (path: string): unknown =>
    path.split('.').reduce<unknown>(
      (node, part) =>
        node && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined,
      zhCN,
    )

  return {
    useTranslation: () => ({
      t: (key: string) => {
        if (lookup(key) === undefined) {
          throw new Error(
            `i18n 缺键: ${key}（zh-CN）。请补齐 src/i18n/zh-CN.ts 与 en.ts 后重跑 npm run i18n:check`,
          )
        }
        return key
      },
      i18n: { language: 'zh', changeLanguage: () => Promise.resolve() },
    }),
    Trans: ({ children }: { children: React.ReactNode }) => children,
    initReactI18next: {
      type: '3rdParty',
      init: () => {},
    },
  }
})
