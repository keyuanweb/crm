package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.customfield.CustomFieldRequest;
import com.crm.dto.customfield.CustomFieldValueDTO;
import com.crm.entity.CustomField;
import com.crm.entity.CustomFieldValue;
import com.crm.repository.CustomFieldMapper;
import com.crm.repository.CustomFieldValueMapper;
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

/** CustomFieldService 单元测试（016 T013）：定义 CRUD/重复 409/类型校验/必填 422。 */
class CustomFieldServiceTest {

  private CustomFieldMapper fieldMapper;
  private CustomFieldValueMapper valueMapper;
  private AuditService auditService;
  private FieldPermissionService fieldPermissionService;
  private CustomFieldService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, CustomField.class);
    TableInfoHelper.initTableInfo(assistant, CustomFieldValue.class);
  }

  @BeforeEach
  void setUp() {
    fieldMapper = mock(CustomFieldMapper.class);
    valueMapper = mock(CustomFieldValueMapper.class);
    auditService = mock(AuditService.class);
    fieldPermissionService = mock(FieldPermissionService.class);
    service =
        new CustomFieldService(fieldMapper, valueMapper, auditService, fieldPermissionService);
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

  private CustomFieldRequest request(String entity, String name, String type) {
    CustomFieldRequest req = new CustomFieldRequest();
    req.setEntityType(entity);
    req.setName(name);
    req.setFieldType(type);
    return req;
  }

  private CustomField field(Long id) {
    CustomField f = new CustomField();
    f.setId(id);
    f.setEntityType("LEAD");
    f.setName("预算规模");
    f.setFieldType("NUMBER");
    f.setRequired(0);
    f.setEnabled(1);
    f.setVersion(0);
    return f;
  }

  @Test
  @DisplayName("创建字段成功：审计记录")
  void createSucceeds() {
    when(fieldMapper.selectCount(any())).thenReturn(0L);
    when(fieldMapper.insert(any(CustomField.class)))
        .thenAnswer(
            invocation -> {
              CustomField f = invocation.getArgument(0);
              f.setId(1L);
              return 1;
            });

    var resp = service.create(request("LEAD", "预算规模", "NUMBER"));

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getName()).isEqualTo("预算规模");
    verify(auditService).record("CREATE", "CUSTOM_FIELD", 1L, "创建自定义字段：LEAD.预算规模");
  }

  @Test
  @DisplayName("创建字段：实体内重名 → 409")
  void createDuplicateThrows() {
    when(fieldMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.create(request("LEAD", "预算规模", "NUMBER")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOM_FIELD_DUPLICATE);
  }

  @Test
  @DisplayName("SELECT 类型无选项 → 422；非 SELECT 带选项 → 422")
  void typeOptionsValidation() {
    CustomFieldRequest select = request("LEAD", "来源", "SELECT");
    assertThatThrownBy(() -> service.create(select))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOM_FIELD_INVALID);

    CustomFieldRequest text = request("LEAD", "备注", "TEXT");
    text.setOptions("a,b");
    assertThatThrownBy(() -> service.create(text))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOM_FIELD_INVALID);
    verify(fieldMapper, never()).insert(any(CustomField.class));
  }

  @Test
  @DisplayName("保存值：必填字段缺失 → 422 CUSTOM_FIELD_REQUIRED")
  void saveValuesRequiredMissingThrows() {
    CustomField required = field(1L);
    required.setRequired(1);
    when(fieldMapper.selectList(any())).thenReturn(List.of(required));

    assertThatThrownBy(() -> service.saveValues("LEAD", 10L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOM_FIELD_REQUIRED);
  }

  @Test
  @DisplayName("保存值：提供必填字段值则成功（先删后插）")
  void saveValuesSucceeds() {
    CustomField required = field(1L);
    required.setRequired(1);
    when(fieldMapper.selectList(any())).thenReturn(List.of(required));

    CustomFieldValueDTO v = new CustomFieldValueDTO();
    v.setFieldId(1L);
    v.setValue("500万");
    service.saveValues("LEAD", 10L, List.of(v));

    verify(valueMapper).delete(any());
    verify(valueMapper).insert(any(CustomFieldValue.class));
  }

  @Test
  @DisplayName("删除字段：物理清理关联值")
  void deleteCleansValues() {
    when(fieldMapper.selectById(1L)).thenReturn(field(1L));

    service.delete(1L);

    verify(valueMapper).delete(any());
    verify(fieldMapper).deleteById(org.mockito.ArgumentMatchers.<Long>any());
  }

  // ===== 056 读路径：HIDDEN 字段的值不下发 =====

  private CustomFieldValue stored(Long fieldId, String value) {
    CustomFieldValue v = new CustomFieldValue();
    v.setFieldId(fieldId);
    v.setEntityType("LEAD");
    v.setEntityId(10L);
    v.setFieldValue(value);
    return v;
  }

  /** 当前用户切换为非 ADMIN 角色，并登记该角色在本实体上的字段权限。 */
  private void asRole(String roleCode, java.util.Map<Long, String> perms) {
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(2L, "sales", roleCode));
    when(fieldPermissionService.permissionsForRole(roleCode, "LEAD")).thenReturn(perms);
  }

  @Test
  @DisplayName("读值：HIDDEN 字段的值不下发（原先读路径不做权限计算，值随 Response 外泄）")
  void readValuesDropsHiddenField() {
    asRole("SALES", java.util.Map.of(1L, FieldPermissionService.PERM_HIDDEN));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "机密"), stored(2L, "可见")));

    var values = service.readValues("LEAD", 10L);

    assertThat(values).extracting(CustomFieldValueDTO::getFieldId).containsExactly(2L);
    assertThat(values).extracting(CustomFieldValueDTO::getValue).containsExactly("可见");
  }

  @Test
  @DisplayName("读值（批量）：HIDDEN 字段的值同样不下发")
  void readValuesBatchDropsHiddenField() {
    asRole("SALES", java.util.Map.of(1L, FieldPermissionService.PERM_HIDDEN));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "机密"), stored(2L, "可见")));

    var values = service.readValuesBatch("LEAD", List.of(10L));

    assertThat(values.get(10L)).extracting(CustomFieldValueDTO::getFieldId).containsExactly(2L);
  }

  @Test
  @DisplayName("读值：无 HIDDEN 配置时直通，不裁剪")
  void readValuesKeepsEverythingWhenNothingHidden() {
    asRole("SALES", java.util.Map.of(1L, FieldPermissionService.PERM_READ_ONLY));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "机密"), stored(2L, "可见")));

    var values = service.readValues("LEAD", 10L);

    assertThat(values).extracting(CustomFieldValueDTO::getFieldId).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("保存值：HIDDEN 字段不在入参里 → 先删后插必须按原值补回，不得静默删除")
  void saveValuesKeepsHiddenFieldValue() {
    asRole("SALES", java.util.Map.of(1L, FieldPermissionService.PERM_HIDDEN));
    when(fieldMapper.selectList(any())).thenReturn(List.of(field(2L)));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "机密"), stored(2L, "旧")));

    CustomFieldValueDTO submitted = new CustomFieldValueDTO();
    submitted.setFieldId(2L);
    submitted.setValue("新");
    service.saveValues("LEAD", 10L, List.of(submitted));

    org.mockito.ArgumentCaptor<CustomFieldValue> captor =
        org.mockito.ArgumentCaptor.forClass(CustomFieldValue.class);
    verify(valueMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
    assertThat(captor.getAllValues())
        .anySatisfy(
            v -> {
              assertThat(v.getFieldId()).isEqualTo(1L);
              assertThat(v.getFieldValue()).isEqualTo("机密");
            });
  }

  // ===== 103：受保护字段（HIDDEN ∪ READ_ONLY）被省略时不得销毁其值 =====
  //
  // 判据一律落在**插入的行**上（不是「没抛异常」）：省略即销毁的形态是「少了一行」，
  // 而「多了一行」是同一次修法的反向风险（会毁掉 EDITABLE 的清空能力）——两个方向都要钉。

  /** 捕获本次 saveValues 的全部 insert（按插入顺序）。 */
  private List<CustomFieldValue> insertedValues() {
    org.mockito.ArgumentCaptor<CustomFieldValue> captor =
        org.mockito.ArgumentCaptor.forClass(CustomFieldValue.class);
    verify(valueMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
    return captor.getAllValues();
  }

  /** 同上，收成 fieldId → fieldValue 的 Map（同一 id 被插两次会暴露为重复键，故另有逐条判据）。 */
  private java.util.Map<Long, String> insertedById() {
    return insertedValues().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                CustomFieldValue::getFieldId, CustomFieldValue::getFieldValue, (a, b) -> a));
  }

  @Test
  @DisplayName("103：保存值时省略 READ_ONLY 字段 → 按库中原值补回（原先只回补 HIDDEN）")
  void saveValuesKeepsReadOnlyFieldValue() {
    asRole("SALES", java.util.Map.of(1L, FieldPermissionService.PERM_READ_ONLY));
    when(fieldMapper.selectList(any())).thenReturn(List.of(field(1L), field(2L)));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "只读原值"), stored(2L, "旧")));

    CustomFieldValueDTO submitted = new CustomFieldValueDTO();
    submitted.setFieldId(2L);
    submitted.setValue("新");
    service.saveValues("LEAD", 10L, List.of(submitted));

    assertThat(insertedById()).containsEntry(1L, "只读原值").containsEntry(2L, "新");
    assertThat(insertedValues()).hasSize(2);
  }

  @Test
  @DisplayName("103：values 为 null 或空列表时同样回补受保护字段（回补不以非空载荷为前提）")
  void saveValuesRestoresProtectedFieldWhenNothingSubmittedAtAll() {
    asRole("SALES", java.util.Map.of(1L, FieldPermissionService.PERM_READ_ONLY));
    when(fieldMapper.selectList(any())).thenReturn(List.of(field(1L), field(2L)));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "只读原值")));

    service.saveValues("LEAD", 10L, null);

    assertThat(insertedById()).containsEntry(1L, "只读原值").hasSize(1);

    Mockito.clearInvocations(valueMapper);
    service.saveValues("LEAD", 10L, List.of());

    assertThat(insertedById()).containsEntry(1L, "只读原值").hasSize(1);
  }

  @Test
  @DisplayName("103：原样回传的 READ_ONLY 值不得被回补成第二行（uk_field_entity_value 撞键）")
  void saveValuesDoesNotDuplicateEchoedReadOnlyValue() {
    asRole("SALES", java.util.Map.of(1L, FieldPermissionService.PERM_READ_ONLY));
    when(fieldMapper.selectList(any())).thenReturn(List.of(field(1L), field(2L)));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "只读原值")));

    CustomFieldValueDTO echoed = new CustomFieldValueDTO();
    echoed.setFieldId(1L);
    echoed.setValue("只读原值");
    service.saveValues("LEAD", 10L, List.of(echoed));

    assertThat(insertedValues()).extracting(CustomFieldValue::getFieldId).containsExactly(1L);
    assertThat(insertedById()).containsEntry(1L, "只读原值");
  }

  @Test
  @DisplayName("103 反方向：未配置权限的字段被省略时仍照旧删除（清空能力不得被过度回补毁掉）")
  void saveValuesStillClearsOmittedEditableField() {
    asRole("SALES", java.util.Map.of(1L, FieldPermissionService.PERM_READ_ONLY));
    when(fieldMapper.selectList(any())).thenReturn(List.of(field(1L), field(2L)));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "只读原值"), stored(2L, "旧")));

    CustomFieldValueDTO echoed = new CustomFieldValueDTO();
    echoed.setFieldId(1L);
    echoed.setValue("只读原值");
    service.saveValues("LEAD", 10L, List.of(echoed));

    assertThat(insertedValues()).extracting(CustomFieldValue::getFieldId).doesNotContain(2L);
    assertThat(insertedById()).hasSize(1);
  }

  @Test
  @DisplayName("103：未知权限值按受保护处理（谓词取 != EDITABLE，往严的一侧倒）")
  void saveValuesTreatsUnknownPermissionValueAsProtected() {
    asRole("SALES", java.util.Map.of(1L, "SOMETHING_ELSE"));
    when(fieldMapper.selectList(any())).thenReturn(List.of(field(1L), field(2L)));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "只读原值"), stored(2L, "旧")));

    CustomFieldValueDTO submitted = new CustomFieldValueDTO();
    submitted.setFieldId(2L);
    submitted.setValue("新");
    service.saveValues("LEAD", 10L, List.of(submitted));

    assertThat(insertedById()).containsEntry(1L, "只读原值").containsEntry(2L, "新");
  }

  @Test
  @DisplayName("103：完全无权限配置时不回补任何字段（fail-open 默认下省略即清空）")
  void saveValuesRestoresNothingWhenNoPermissionConfigured() {
    asRole("SALES", java.util.Map.of());
    when(fieldMapper.selectList(any())).thenReturn(List.of(field(1L), field(2L)));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "旧一"), stored(2L, "旧二")));

    CustomFieldValueDTO submitted = new CustomFieldValueDTO();
    submitted.setFieldId(2L);
    submitted.setValue("新");
    service.saveValues("LEAD", 10L, List.of(submitted));

    assertThat(insertedById()).containsEntry(2L, "新").doesNotContainKey(1L).hasSize(1);
  }

  @Test
  @DisplayName("103：字段被提交但值为空白 → 与主循环同规则，不算「已提交」、原值照旧回补")
  void saveValuesTreatsBlankSubmissionAsOmittedForProtectedField() {
    asRole("SALES", java.util.Map.of(1L, FieldPermissionService.PERM_READ_ONLY));
    when(fieldMapper.selectList(any())).thenReturn(List.of(field(1L), field(2L)));
    when(valueMapper.selectList(any())).thenReturn(List.of(stored(1L, "只读原值")));

    CustomFieldValueDTO blank = new CustomFieldValueDTO();
    blank.setFieldId(1L);
    blank.setValue("   ");
    service.saveValues("LEAD", 10L, List.of(blank));

    assertThat(insertedById()).containsEntry(1L, "只读原值").hasSize(1);
  }
}
