package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.field.FieldPermissionRequest;
import com.crm.dto.field.FieldValueLike;
import com.crm.entity.FieldPermission;
import com.crm.repository.CustomFieldMapper;
import com.crm.repository.FieldPermissionMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import com.crm.support.BuiltinFieldRegistry;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** FieldPermissionService 单元测试（056 T008）：配置/权限计算/保存校验。 */
class FieldPermissionServiceTest {

  private FieldPermissionMapper permissionMapper;
  private FieldPermissionService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, FieldPermission.class);
  }

  @BeforeEach
  void setUp() {
    permissionMapper = mock(FieldPermissionMapper.class);
    // 注册表在此用替身：本类测的是服务语义，不是注册表自检（那条在 BuiltinFieldRegistryTest）
    // 102：第三个参数是只读自定义字段定义的 mapper（可配字段表与 fieldName 用；默认返回空表）
    service =
        new FieldPermissionService(
            permissionMapper, mock(BuiltinFieldRegistry.class), mock(CustomFieldMapper.class));
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private FieldPermission permission(String roleCode, String permission) {
    FieldPermission fp = new FieldPermission();
    fp.setRoleCode(roleCode);
    fp.setEntityType("CUSTOMER");
    fp.setFieldId(5L);
    fp.setPermission(permission);
    return fp;
  }

  @Test
  @DisplayName("ADMIN 恒可编辑")
  void adminAlwaysEditable() {
    when(permissionMapper.selectOne(any())).thenReturn(permission("SALES", "READ_ONLY"));
    assertThat(service.permissionFor("ADMIN", "CUSTOMER", 5L))
        .isEqualTo(FieldPermissionService.PERM_EDITABLE);
  }

  @Test
  @DisplayName("未配置默认可编辑")
  void defaultEditable() {
    when(permissionMapper.selectOne(any())).thenReturn(null);
    assertThat(service.permissionFor("SALES", "CUSTOMER", 5L))
        .isEqualTo(FieldPermissionService.PERM_EDITABLE);
  }

  @Test
  @DisplayName("配置只读 → 返回 READ_ONLY")
  void readOnlyConfigured() {
    when(permissionMapper.selectOne(any())).thenReturn(permission("SALES", "READ_ONLY"));
    assertThat(service.permissionFor("SALES", "CUSTOMER", 5L))
        .isEqualTo(FieldPermissionService.PERM_READ_ONLY);
  }

  @Test
  @DisplayName("upsert：新配置插入")
  void upsertCreates() {
    when(permissionMapper.selectOne(any())).thenReturn(null);
    when(permissionMapper.insert(any(FieldPermission.class)))
        .thenAnswer(
            invocation -> {
              FieldPermission fp = invocation.getArgument(0);
              fp.setId(1L);
              return 1;
            });
    FieldPermission stored = permission("SALES", "READ_ONLY");
    stored.setId(1L);
    when(permissionMapper.selectById(1L)).thenReturn(stored);

    FieldPermissionRequest req = new FieldPermissionRequest();
    req.setRoleCode("SALES");
    req.setEntityType("CUSTOMER");
    req.setFieldId(5L);
    req.setPermission("READ_ONLY");
    var resp = service.upsert(req);

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getPermission()).isEqualTo("READ_ONLY");
  }

  @Test
  @DisplayName("保存校验：HIDDEN 字段写入 → 422")
  void hiddenFieldRejected() {
    when(permissionMapper.selectOne(any())).thenReturn(permission("SALES", "HIDDEN"));
    FieldValueLike v =
        new FieldValueLike() {
          @Override
          public Long getFieldId() {
            return 5L;
          }

          @Override
          public String getValue() {
            return "x";
          }
        };
    assertThatThrownBy(() -> service.validateWrite("SALES", "CUSTOMER", List.of(v), Map.of()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FIELD_HIDDEN);
  }

  @Test
  @DisplayName("保存校验：READ_ONLY 字段修改已有值 → 422；相同值幂等放行")
  void readOnlyFieldRejected() {
    when(permissionMapper.selectOne(any())).thenReturn(permission("SALES", "READ_ONLY"));
    FieldValueLike changed =
        new FieldValueLike() {
          @Override
          public Long getFieldId() {
            return 5L;
          }

          @Override
          public String getValue() {
            return "new";
          }
        };
    assertThatThrownBy(
            () -> service.validateWrite("SALES", "CUSTOMER", List.of(changed), Map.of(5L, "old")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FIELD_READ_ONLY);

    // 相同值幂等
    FieldValueLike same =
        new FieldValueLike() {
          @Override
          public Long getFieldId() {
            return 5L;
          }

          @Override
          public String getValue() {
            return "old";
          }
        };
    service.validateWrite("SALES", "CUSTOMER", List.of(same), Map.of(5L, "old"));
  }
}
