/* CRM 应用外壳 Service Worker（027-mobile-pwa，FR-003/004/007）。
 * 缓存优先 + 网络回退；不缓存 /api/**（业务数据与令牌安全）。
 */
const VERSION = 'v1'
const CACHE_NAME = `crm-shell-${VERSION}`
const SHELL = ['/', '/index.html', '/manifest.webmanifest', '/icon-192.png', '/icon-512.png', '/apple-touch-icon.png']

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))),
      )
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url)
  // 仅处理同源 GET；不缓存 API 请求
  if (url.origin !== self.location.origin || event.request.method !== 'GET') {
    return
  }
  if (url.pathname.startsWith('/api/')) {
    return
  }
  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) {
        return cached
      }
      return fetch(event.request).then((resp) => {
        // 成功响应且为可缓存资源时写入缓存
        if (resp && resp.status === 200) {
          const clone = resp.clone()
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone))
        }
        return resp
      })
    }),
  )
})
