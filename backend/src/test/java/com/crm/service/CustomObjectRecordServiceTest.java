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
import com.crm.dto.customobject.RecordRequest;
import com.crm.entity.CustomObject;
import com.crm.entity.CustomObjectRecord;
import com.crm.repository.CustomObjectRecordMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
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

/** CustomObjectRecordService 单元测试（059 T011）：CRUD/必填/停用拦截。 */
class CustomObjectRecordServiceTest {

  private CustomObjectRecordMapper recordMapper;
  private CustomObjectService objectService;
  private CustomObjectRecordService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, CustomObjectRecord.class);
    TableInfoHelper.initTableInfo(assistant, CustomObject.class);
  }

  @BeforeEach
  void setUp() {
    recordMapper = mock(CustomObjectRecordMapper.class);
    objectService = mock(CustomObjectService.class);
    service = new CustomObjectRecordService(recordMapper, objectService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private CustomObject enabledObject() {
    CustomObject obj = new CustomObject();
    obj.setId(1L);
    obj.setEnabled(1);
    CustomObjectRequest.FieldDef f = new CustomObjectRequest.FieldDef();
    f.setField("name");
    f.setLabel("项目名称");
    f.setType("TEXT");
    f.setRequired(true);
    obj.setFields("[{\"field\":\"name\",\"label\":\"项目名称\",\"type\":\"TEXT\",\"required\":true}]");
    return obj;
  }

  @Test
  @DisplayName("创建记录：必填满足 → 成功")
  void createSucceeds() {
    when(objectService.require(1L)).thenReturn(enabledObject());
    when(objectService.fieldsOf(any())).thenReturn(List.of(fieldDef()));
    when(recordMapper.insert(any(CustomObjectRecord.class)))
        .thenAnswer(
            invocation -> {
              CustomObjectRecord r = invocation.getArgument(0);
              r.setId(10L);
              return 1;
            });

    RecordRequest req = new RecordRequest();
    req.setValues(Map.of("name", "CRM 重构"));
    var resp = service.create(1L, req);

    assertThat(resp.getId()).isEqualTo(10L);
    assertThat(resp.getValues().get("name")).isEqualTo("CRM 重构");
  }

  private CustomObjectRequest.FieldDef fieldDef() {
    CustomObjectRequest.FieldDef f = new CustomObjectRequest.FieldDef();
    f.setField("name");
    f.setLabel("项目名称");
    f.setType("TEXT");
    f.setRequired(true);
    return f;
  }

  @Test
  @DisplayName("创建记录：必填缺失 → 422")
  void createMissingRequiredThrows() {
    when(objectService.require(1L)).thenReturn(enabledObject());
    when(objectService.fieldsOf(any())).thenReturn(List.of(fieldDef()));

    RecordRequest req = new RecordRequest();
    req.setValues(Map.of());
    assertThatThrownBy(() -> service.create(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OBJECT_FIELD_REQUIRED);
  }

  @Test
  @DisplayName("创建记录：对象停用 → 422")
  void createDisabledObjectThrows() {
    CustomObject disabled = enabledObject();
    disabled.setEnabled(0);
    when(objectService.require(1L)).thenReturn(disabled);

    RecordRequest req = new RecordRequest();
    req.setValues(Map.of("name", "x"));
    assertThatThrownBy(() -> service.create(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OBJECT_DISABLED);
  }
}
