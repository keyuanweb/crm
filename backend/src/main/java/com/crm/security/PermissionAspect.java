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
import org.springframework.stereotype.Component;

/**
 * 操作权限校验切面（028-role-permissions，FR-006）：拦截 @RequirePermission 方法， 校验当前用户角色是否含权限码；ADMIN 内建角色恒放行；无权限抛
 * 403。
 */
@Aspect
@Component
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
