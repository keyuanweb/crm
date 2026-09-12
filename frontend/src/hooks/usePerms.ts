import { useAuthStore } from '../store/authStore'
import { hasPerm } from './usePermission'

/**
 * 批量权限判断（权限接线）。
 *
 * <p>把页面里成串的 `const canX = hasPerm('x', user)` 收敛成一次调用，返回「权限码 → 是否放行」的对象，
 * 取值处写 `can[PERMS.customerDelete]`。判断逻辑仍然只有 `hasPerm` 一处（ADMIN 短路、其余按
 * `user.permissions`），这里不做任何额外加工。
 *
 * <p>两个使用约束：必须在组件顶层**无条件**调用（React Hooks 规则）；返回值每次渲染都是**新对象**，
 * 因此不要把它塞进 useMemo / useEffect 的依赖数组——那会让依赖每帧都判为变化。
 */
export function usePerms(codes: readonly string[]): Record<string, boolean> {
  const user = useAuthStore((state) => state.user)
  const result: Record<string, boolean> = {}
  for (const code of codes) {
    result[code] = hasPerm(code, user)
  }
  return result
}
