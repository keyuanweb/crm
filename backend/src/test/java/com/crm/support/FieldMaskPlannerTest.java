package com.crm.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import com.crm.service.FieldPermissionService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * 内置字段掩码判据源（102 T3）：HIDDEN 才进掩码集合、非 EDITABLE 一律进受保护集合。
 *
 * <p>这个类是全链（出参收口点 / 两个 xlsx 导出 / 写侧回补）的**唯一**判据源：它错了，四条链会一起错， 且都以「看起来正常工作」的形态错（掩码集合多一个字段 =
 * 少显示一列；少一个 = 泄漏一列）。故三态 （HIDDEN / READ_ONLY / EDITABLE）在这里逐条钉住，防止有人把它压成「配置了就是隐藏」的二态。
 */
class FieldMaskPlannerTest {

  private FieldPermissionService fieldPermissionService;
  private FieldMaskPlanner planner;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    fieldPermissionService = mock(FieldPermissionService.class);
    planner = new FieldMaskPlanner(fieldPermissionService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  @Test
  @DisplayName("T3 plan：只有 HIDDEN 进掩码集合（READ_ONLY 可见、EDITABLE 可见）")
  void planKeepsOnlyHidden() {
    Map<String, String> configured = new LinkedHashMap<>();
    configured.put("phone", FieldPermissionService.PERM_HIDDEN);
    configured.put("email", FieldPermissionService.PERM_READ_ONLY);
    configured.put("remark", FieldPermissionService.PERM_EDITABLE);
    when(fieldPermissionService.builtinPermissionsForRole("SALES", "CUSTOMER"))
        .thenReturn(configured);

    assertThat(planner.plan("SALES", "CUSTOMER")).containsExactly("phone");
  }

  @Test
  @DisplayName("T3 plan：ADMIN（服务返回空表）⇒ 空集合（快路径的判据）")
  void planIsEmptyForAdmin() {
    when(fieldPermissionService.builtinPermissionsForRole("ADMIN", "CUSTOMER"))
        .thenReturn(Map.of());

    assertThat(planner.plan("ADMIN", "CUSTOMER")).isEmpty();
  }

  @Test
  @DisplayName("T3 plan：未配置任何内置权限 ⇒ 空集合（既有路径全直通，本批的兼容性前提）")
  void planIsEmptyWhenNothingConfigured() {
    when(fieldPermissionService.builtinPermissionsForRole("SUPPORT", "OPPORTUNITY"))
        .thenReturn(Map.of());

    assertThat(planner.plan("SUPPORT", "OPPORTUNITY")).isEmpty();
  }

  @Test
  @DisplayName("plan：两个 HIDDEN 字段 ⇒ 两个都进（不是「只取第一条」）")
  void planKeepsAllHiddenFields() {
    when(fieldPermissionService.builtinPermissionsForRole("SALES", "CUSTOMER"))
        .thenReturn(Map.of("phone", "HIDDEN", "email", "HIDDEN"));

    assertThat(planner.plan("SALES", "CUSTOMER")).containsExactlyInAnyOrder("phone", "email");
  }

  @Test
  @DisplayName("protectedKeys：HIDDEN ∪ READ_ONLY（写侧回补的判据；EDITABLE 不进）")
  void protectedKeysCoverHiddenAndReadOnly() {
    Map<String, String> configured = new LinkedHashMap<>();
    configured.put("phone", FieldPermissionService.PERM_HIDDEN);
    configured.put("email", FieldPermissionService.PERM_READ_ONLY);
    configured.put("remark", FieldPermissionService.PERM_EDITABLE);
    when(fieldPermissionService.builtinPermissionsForRole("SALES", "CUSTOMER"))
        .thenReturn(configured);

    assertThat(planner.protectedKeys("SALES", "CUSTOMER"))
        .containsExactlyInAnyOrder("phone", "email");
  }

  @Test
  @DisplayName("protectedKeys：未知权限值也按受保护处理（往严的一侧倒，不静默放行）")
  void protectedKeysTreatUnknownPermissionAsProtected() {
    when(fieldPermissionService.builtinPermissionsForRole("SALES", "CUSTOMER"))
        .thenReturn(Map.of("phone", "MASKED_AND_WATERMARKED"));

    assertThat(planner.protectedKeys("SALES", "CUSTOMER")).containsExactly("phone");
    // 而掩码集合只认 HIDDEN：多出来的权限值不该让字段从出参里消失（那会把「不认识」变成「看不见」）
    assertThat(planner.plan("SALES", "CUSTOMER")).isEmpty();
  }

  @Test
  @DisplayName("currentRole：有主体取主体角色，无主体回落 ADMIN（沿用的 fail-open 口径）")
  void currentRoleFallsBackToAdmin() {
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(7L, "sales01", "SALES"));
    assertThat(planner.currentRole()).isEqualTo("SALES");

    securityUtilMock.when(SecurityUtil::currentPrincipal).thenReturn(null);
    assertThat(planner.currentRole()).isEqualTo("ADMIN");
  }
}
