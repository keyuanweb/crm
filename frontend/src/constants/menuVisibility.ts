/**
 * 侧边栏可见性判定（084 US1 的核心，纯函数）。
 *
 * <p><b>为什么要单独抽一个文件</b>：改造前这条规则散在 `App.tsx` 的渲染表达式里，且带着一处
 * 按角色名整组开关的硬门（`isAdmin ? adminRoutes : []`）。硬门与授权数据是**两套互相不知道的开关**，
 * 于是出现「`role_menu` 授了、角色页勾得上、侧边栏永远不渲染」这一类静默故障——V75 把
 * `users`/`roles`/`departments` 等键授给了 5 个预置角色，而它们全都进不了侧边栏（共 16 处 (角色,键) 对）。
 *
 * <p>抽成纯函数后，「可见集合由什么决定」这件事可以被单测钉住（见 `menuVisibility.test.ts`），
 * 而不是靠读 `App.tsx` 的 JSX 推断。
 *
 * <p><b>本文件的唯一角色名分支是 `ADMIN` 全量兜底</b>（FR-N04：内置管理员不依赖 `role_menu` 数据，
 * 否则一旦授权数据缺失，管理员会把自己锁在系统外面）。除此之外**不出现任何角色名判断**——
 * 这是 FR-N01「不得按角色名整组开关」的机械形态，并由单测以「虚构角色 + 管理组菜单键」的用例守住。
 *
 * <p>另有一条边界：授权里出现、但清单里没有的键**不会**变成可见项（FR-N02 的 `授权集合 ⊆ 可渲染集合`）。
 * 这种键在界面上无处渲染，直接忽略比渲染成空项更安全。
 */
import { MENU_MANIFEST, type MenuManifestGroup } from './menuManifest'

/** 生成物里的全部菜单键。ADMIN 的全量兜底与「清单里有没有这个键」都以此为准。 */
export function allMenuKeys(manifest: readonly MenuManifestGroup[] = MENU_MANIFEST): Set<string> {
  const keys = new Set<string>()
  for (const group of manifest) for (const item of group.items) keys.add(item.menuKey)
  return keys
}

/**
 * 解出某角色实际可见的菜单键集合。
 *
 * @param role 角色码（如 `'ADMIN'`、`'ANALYST'`）；缺省时按「非管理员」处理
 * @param grantedMenus 登录接口下发的 `user.menus`（即该角色在 `role_menu` 里的菜单键）
 * @param manifest 菜单清单，默认取生成物；显式传入是为了让单测能构造小清单
 */
export function resolveVisibleMenuKeys(
  role: string | undefined,
  grantedMenus: Iterable<string> | undefined,
  manifest: readonly MenuManifestGroup[] = MENU_MANIFEST,
): Set<string> {
  const renderable = allMenuKeys(manifest)
  // ADMIN 全量：不看授权数据，避免授权表残缺时管理员看不到任何菜单（FR-N04）。
  if (role === 'ADMIN') return renderable
  const visible = new Set<string>()
  for (const key of grantedMenus ?? []) if (renderable.has(key)) visible.add(key)
  return visible
}
