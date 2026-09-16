package com.crm.security;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.service.RoleService;
import java.util.List;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 操作权限校验切面（028-role-permissions，FR-006）：拦截 @RequirePermission 方法， 校验当前用户角色是否含权限码；ADMIN 内建角色恒放行；无权限抛
 * 403。
 *
 * <p>⚠️ 2026-09-16（100-rate-limit-consolidation）新增 {@code @Order(10)}：本批引入了第二个切面 {@code
 * RateLimitAspect}（{@code @Order(20)}），两者若无显式序则并列，谁先执行取决于排序实现——
 * 而顺序在这里<b>有语义</b>：权限必须先于限流，否则<b>未授权者也会消耗配额</b>（一个没有导出权限的账号
 * 反复打导出端点，会把有权限的人都挤掉）。两个序都是显式常量，只要求相对大小关系。
 */
@Aspect
@Component
@Order(10)
public class PermissionAspect {

  private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

  private final RoleService roleService;

  public PermissionAspect(RoleService roleService) {
    this.roleService = roleService;
  }

  @Before("@annotation(requirePermission)")
  public void checkPermission(RequirePermission requirePermission) {
    CrmPrincipal principal = SecurityUtil.currentPrincipal();
    if (principal == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    // ADMIN 内建恒有全部权限（兜底）
    if ("ADMIN".equals(principal.role())) {
      return;
    }
    List<String> permissions = roleService.permissionsOf(principal.role());
    if (!permissions.contains(requirePermission.value())) {
      log.debug("Permission denied for role {} on {}", principal.role(), requirePermission.value());
      throw new BusinessException(ErrorCode.PERMISSION_DENIED);
    }
  }
}
