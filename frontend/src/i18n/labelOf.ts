/**
 * 取菜单文案：缺键时降级为**权威中文名**。
 *
 * <p>`t()` 在缺键时返回键名本身（渲染出 `menu.xxx`）——这比不翻译更糟：用户看到的是一个内部标识符，
 * 而缺键恰恰是**没有任何测试会发现的**场景（测试里 react-i18next 被 `t(key) => key` 打桩，
 * 缺键与存在走同一条路径，见 `scripts/check-i18n.mjs` 的说明）。
 * 生成物 `menuManifest.ts` 里的 `title` 就是权威处的中文名，直接拿它降级：既不显示键名，
 * 也与角色配置页显示的名字一致（084 FR-N09 与边界情况「缺文案降级」）。
 *
 * <p>**为什么必须只有这一份实现**：侧边栏（`App.tsx`）与面包屑（`components/BreadcrumbNav.tsx`）
 * 现在都从生成物取文案，两处各写一遍降级逻辑，就会有两种降级行为——而分歧只在缺键时才显形。
 */
export function menuLabel(
  t: (key: string) => string,
  i18nKey: string,
  title: string,
): string {
  const key = `menu.${i18nKey}`
  const text = t(key)
  return text === key ? title : text
}
