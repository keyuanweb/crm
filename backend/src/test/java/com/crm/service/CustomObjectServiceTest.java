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
import com.crm.dto.customobject.CustomObjectRequest;
import com.crm.entity.CustomObject;
import com.crm.repository.CustomObjectMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** CustomObjectService 单元测试（059 T008）：CRUD/字段校验/编码唯一。 */
class CustomObjectServiceTest {

  private CustomObjectMapper objectMapper;
  private CustomObjectService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, CustomObject.class);
  }

  @BeforeEach
  void setUp() {
    objectMapper = mock(CustomObjectMapper.class);
    service = new CustomObjectService(objectMapper);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private CustomObjectRequest request() {
    CustomObjectRequest req = new CustomObjectRequest();
    req.setName("项目");
    req.setCode("PROJECT");
    CustomObjectRequest.FieldDef f = new CustomObjectRequest.FieldDef();
    f.setField("name");
    f.setLabel("项目名称");
    f.setType("TEXT");
    f.setRequired(true);
    req.setFields(List.of(f));
    req.setEnabled(true);
    return req;
  }

  @Test
  @DisplayName("创建：字段合法 → 成功")
  void createSucceeds() {
    when(objectMapper.selectCount(any())).thenReturn(0L);
    when(objectMapper.insert(any(CustomObject.class)))
        .thenAnswer(
            invocation -> {
              CustomObject o = invocation.getArgument(0);
              o.setId(1L);
              return 1;
            });
    CustomObject stored = new CustomObject();
    stored.setId(1L);
    stored.setName("项目");
    stored.setCode("PROJECT");
    stored.setFields(
        "[{\"field\":\"name\",\"label\":\"项目名称\",\"type\":\"TEXT\",\"required\":true}]");
    stored.setEnabled(1);
    stored.setVersion(0);
    when(objectMapper.selectById(1L)).thenReturn(stored);

    var resp = service.create(request());

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getCode()).isEqualTo("PROJECT");
    assertThat(resp.getFields()).hasSize(1);
  }

  @Test
  @DisplayName("创建：编码重复 → 409")
  void createDuplicateCodeThrows() {
    when(objectMapper.selectCount(any())).thenReturn(1L);
    assertThatThrownBy(() -> service.create(request()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OBJECT_CODE_DUPLICATE);
  }

  @Test
  @DisplayName("创建：字段类型非法 → 422")
  void createInvalidFieldThrows() {
    when(objectMapper.selectCount(any())).thenReturn(0L);
    CustomObjectRequest req = request();
    CustomObjectRequest.FieldDef bad = new CustomObjectRequest.FieldDef();
    bad.setField("x");
    bad.setLabel("X");
    bad.setType("BOGUS");
    req.setFields(List.of(bad));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OBJECT_FIELD_INVALID);
  }

  @Test
  @DisplayName("创建：空字段集 → 422")
  void createEmptyFieldsThrows() {
    when(objectMapper.selectCount(any())).thenReturn(0L);
    CustomObjectRequest req = request();
    req.setFields(List.of());
    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OBJECT_FIELD_INVALID);
  }
}
