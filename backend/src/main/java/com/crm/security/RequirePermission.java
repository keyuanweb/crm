package com.crm.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作权限校验注解（028-role-permissions，FR-006）：标注关键写操作方法， 由 PermissionAspect 校验当前用户角色是否含指定权限码；ADMIN 恒放行。
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

  /** 权限码（如 customer:delete、order:payment、user:manage）。 */
  String value();
}
