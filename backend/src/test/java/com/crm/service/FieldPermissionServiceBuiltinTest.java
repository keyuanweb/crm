package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.field.AvailableFieldResponse;
import com.crm.dto.field.FieldPermissionRequest;
import com.crm.entity.CustomField;
import com.crm.entity.FieldPermission;
import com.crm.repository.CustomFieldMapper;
import com.crm.repository.FieldPermissionMapper;
import com.crm.security.SecurityUtil;
import com.crm.support.BuiltinField;
import com.crm.support.BuiltinFieldRegistry;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * 字段权限服务的内置字段面（102 T4–T7 + 配置面）：upsert 的「二选一」守卫、按 identifier 分支的查询、内置权限查找、可配字段表。
 *
 * <p><b>为什么不复用 {@code FieldPermissionServiceTest}</b>：那边是 056 的自定义字段面（构造器里注册表是空替身），
 * 本类要把注册表替身**按需打桩**（认哪些内置字段是这组用例的前置条件）。分成两个文件后，两个面的前置互不干扰。
 *
 * <p><b>本类为什么能抓住「查询退化成 {@code field_id = NULL}」这个缺陷</b>：mock 的 {@code selectOne(any())}
 * 对参数不敏感，所以「第二次保存会不会再 INSERT」在纯替身下**看不出来**。故 T6 直接对**生成的 SQL 段**下断言 （{@code
 * getSqlSegment()}）：内置行必须查到 {@code field_key}、自定义行必须查到 {@code field_id}。 这是一条机制级判据，正好落在缺陷发生的那一层。
 */
class FieldPermissionServiceBuiltinTest {

  private FieldPermissionMapper permissionMapper;
  private CustomFieldMapper customFieldMapper;
  private BuiltinFieldRegistry registry;
  private FieldPermissionService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    // LambdaQueryWrapper 解析列名要靠 TableInfo；getSqlSegment() 也一样。
    TableInfoHelper.initTableInfo(assistant, FieldPermission.class);
    TableInfoHelper.initTableInfo(assistant, CustomField.class);
  }

  @BeforeEach
  void setUp() {
    permissionMapper = mock(FieldPermissionMapper.class);
    customFieldMapper = mock(CustomFieldMapper.class);
    registry = mock(BuiltinFieldRegistry.class);
    service = new FieldPermissionService(permissionMapper, registry, customFieldMapper);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);

    when(registry.find("CUSTOMER", "phone")).thenReturn(customerPhone());
    when(registry.fieldsOf("CUSTOMER"))
        .thenReturn(
            List.of(
                new BuiltinField("CUSTOMER", "contactPerson", "联系人"),
                new BuiltinField("CUSTOMER", "phone", "电话")));
    when(registry.fieldsOf("OPPORTUNITY"))
        .thenReturn(List.of(new BuiltinField("OPPORTUNITY", "expectedAmountMin", "预计金额下限")));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  // ===== T4：恰好一个标识 =====

  @Test
  @DisplayName("T4 upsert：fieldId 与 fieldKey 双空 ⇒ 422 FIELD_PERMISSION_INVALID")
  void upsertRejectsBothIdentifiersMissing() {
    FieldPermissionRequest req = request("SALES", null, null);
    assertThatThrownBy(() -> service.upsert(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FIELD_PERMISSION_INVALID);
    verify(permissionMapper, never()).insert(any());
  }

  @Test
  @DisplayName("T4 upsert：fieldId 与 fieldKey 双非空 ⇒ 422 FIELD_PERMISSION_INVALID")
  void upsertRejectsBothIdentifiersPresent() {
    FieldPermissionRequest req = request("SALES", 5L, "phone");
    assertThatThrownBy(() -> service.upsert(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FIELD_PERMISSION_INVALID);
    verify(permissionMapper, never()).insert(any());
  }

  @Test
  @DisplayName("T5 upsert：未注册的 fieldKey（或属于别的实体）⇒ 422，垃圾配置不入库")
  void upsertRejectsUnknownBuiltinFieldKey() {
    when(registry.find("CUSTOMER", "nope")).thenReturn(null);
    assertThatThrownBy(() -> service.upsert(request("SALES", null, "nope")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FIELD_PERMISSION_INVALID);

    // phone 注册在 CUSTOMER 下，拿 OPPORTUNITY 来配也认不出（注册表按 entityType+fieldKey 查）
    when(registry.find("OPPORTUNITY", "phone")).thenReturn(null);
    FieldPermissionRequest crossEntity = request("SALES", null, "phone");
    crossEntity.setEntityType("OPPORTUNITY");
    assertThatThrownBy(() -> service.upsert(crossEntity))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FIELD_PERMISSION_INVALID);
    verify(permissionMapper, never()).insert(any());
  }

  // ===== T6：按 identifier 分支 / 幂等 =====

  @Test
  @DisplayName("T6 upsert（内置）：查重按 field_key；命中既有行 ⇒ UPDATE 而不是再 INSERT")
  void upsertBuiltinMatchesOnFieldKeyAndUpdates() {
    FieldPermission existing = new FieldPermission();
    existing.setId(9L);
    existing.setRoleCode("SALES");
    existing.setEntityType("CUSTOMER");
    existing.setFieldKey("phone");
    existing.setPermission("HIDDEN");
    when(permissionMapper.selectOne(any())).thenReturn(existing);
    when(permissionMapper.selectById(9L)).thenReturn(existing);

    FieldPermissionRequest req = request("SALES", null, "phone");
    req.setPermission("READ_ONLY");
    var resp = service.upsert(req);

    assertThat(resp.getId()).isEqualTo(9L);
    assertThat(resp.getFieldKey()).isEqualTo("phone");
    assertThat(resp.getFieldId()).isNull();
    // 内置行的 fieldName 由注册表的 label 给（056 起这个字段只声明、从不赋值）
    assertThat(resp.getFieldName()).isEqualTo("电话");
    verify(permissionMapper, never()).insert(any());
    verify(permissionMapper).updateById(existing);

    String sql = capturedSelectOneSql();
    assertThat(sql).contains("field_key");
    assertThat(sql).doesNotContain("field_id");
  }

  @Test
  @DisplayName("T6 upsert（自定义）对照：查重按 field_id，且不得带上 field_key 条件")
  void upsertCustomMatchesOnFieldId() {
    when(permissionMapper.selectOne(any())).thenReturn(null);
    when(permissionMapper.insert(any(FieldPermission.class)))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, FieldPermission.class).setId(11L);
              return 1;
            });
    FieldPermission stored = new FieldPermission();
    stored.setId(11L);
    stored.setRoleCode("SALES");
    stored.setEntityType("CUSTOMER");
    stored.setFieldId(5L);
    stored.setPermission("READ_ONLY");
    when(permissionMapper.selectById(11L)).thenReturn(stored);
    when(customFieldMapper.selectBatchIds(any())).thenReturn(List.of(customField(5L, "客户等级")));

    var resp = service.upsert(request("SALES", 5L, null));

    assertThat(resp.getFieldId()).isEqualTo(5L);
    assertThat(resp.getFieldKey()).isNull();
    assertThat(resp.getFieldName()).isEqualTo("客户等级");
    String sql = capturedSelectOneSql();
    assertThat(sql).contains("field_id");
    assertThat(sql).doesNotContain("field_key");
  }

  @Test
  @DisplayName("T6 upsert（内置）新建：落库的行 field_key 有值、field_id 为 null")
  void upsertBuiltinInsertsWithFieldKeyOnly() {
    when(permissionMapper.selectOne(any())).thenReturn(null);
    when(permissionMapper.insert(any(FieldPermission.class)))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, FieldPermission.class).setId(12L);
              return 1;
            });
    FieldPermission stored = new FieldPermission();
    stored.setId(12L);
    stored.setRoleCode("SALES");
    stored.setEntityType("CUSTOMER");
    stored.setFieldKey("phone");
    stored.setPermission("HIDDEN");
    when(permissionMapper.selectById(12L)).thenReturn(stored);

    service.upsert(request("SALES", null, "phone"));

    ArgumentCaptor<FieldPermission> captor = ArgumentCaptor.forClass(FieldPermission.class);
    verify(permissionMapper).insert(captor.capture());
    assertThat(captor.getValue().getFieldKey()).isEqualTo("phone");
    assertThat(captor.getValue().getFieldId()).isNull();
  }

  // ===== T7：内置权限查找 =====

  @Test
  @DisplayName("T7 permissionForKey：三态 + ADMIN 恒 EDITABLE + 未配置 ⇒ EDITABLE")
  void permissionForKeyCoversAllStates() {
    when(permissionMapper.selectOne(any())).thenReturn(storedBuiltin("HIDDEN"));
    assertThat(service.permissionForKey("SALES", "CUSTOMER", "phone"))
        .isEqualTo(FieldPermissionService.PERM_HIDDEN);

    when(permissionMapper.selectOne(any())).thenReturn(storedBuiltin("READ_ONLY"));
    assertThat(service.permissionForKey("SALES", "CUSTOMER", "phone"))
        .isEqualTo(FieldPermissionService.PERM_READ_ONLY);

    when(permissionMapper.selectOne(any())).thenReturn(null);
    assertThat(service.permissionForKey("SALES", "CUSTOMER", "phone"))
        .isEqualTo(FieldPermissionService.PERM_EDITABLE);
  }

  @Test
  @DisplayName("T7 permissionForKey：ADMIN 恒 EDITABLE，且**不查库**（admin 直通是短路的，不是查出来的）")
  void permissionForKeyShortCircuitsForAdmin() {
    assertThat(service.permissionForKey("ADMIN", "CUSTOMER", "phone"))
        .isEqualTo(FieldPermissionService.PERM_EDITABLE);
    verify(permissionMapper, never()).selectOne(any());
  }

  @Test
  @DisplayName("T7 permissionForKey 的 SQL 按 field_key 查（不得退化成 field_id，那会 fail-open）")
  void permissionForKeyQueriesByFieldKey() {
    when(permissionMapper.selectOne(any())).thenReturn(null);
    service.permissionForKey("SALES", "CUSTOMER", "phone");
    String sql = capturedSelectOneSql();
    assertThat(sql).contains("field_key");
    assertThat(sql).doesNotContain("field_id =");
  }

  @Test
  @DisplayName("builtinPermissionsForRole：只取 field_key 非空的行（内置行混排时不能把自定义行算进来）")
  void builtinPermissionsForRoleFiltersOnFieldKey() {
    FieldPermission hidden = storedBuiltin("HIDDEN");
    when(permissionMapper.selectList(any())).thenReturn(List.of(hidden));

    assertThat(service.builtinPermissionsForRole("SALES", "CUSTOMER"))
        .containsExactly(Map.entry("phone", "HIDDEN"));
    String sql = capturedSelectListSql();
    assertThat(sql).as("实际 SQL 段：%s", sql).contains("field_key IS NOT NULL");
  }

  @Test
  @DisplayName("permissionsForRole：只取 field_id 非空的行（防内置行的 null 键混进自定义映射）")
  void permissionsForRoleFiltersOnFieldId() {
    FieldPermission custom = new FieldPermission();
    custom.setFieldId(5L);
    custom.setPermission("HIDDEN");
    when(permissionMapper.selectList(any())).thenReturn(List.of(custom));

    assertThat(service.permissionsForRole("SALES", "CUSTOMER"))
        .containsExactly(Map.entry(5L, "HIDDEN"));
    assertThat(capturedSelectListSql()).contains("field_id IS NOT NULL");
  }

  @Test
  @DisplayName("builtinPermissionsForRole：ADMIN 返回空表且不查库")
  void builtinPermissionsForRoleIsEmptyForAdmin() {
    assertThat(service.builtinPermissionsForRole("ADMIN", "CUSTOMER")).isEmpty();
    verify(permissionMapper, never()).selectList(any());
  }

  // ===== 配置面：可用字段（T14 的单元层） =====

  @Test
  @DisplayName("availableFields：内置在前、自定义在后；fieldKey/builtin 按种类装配")
  void availableFieldsMergesBuiltinThenCustom() {
    when(customFieldMapper.selectList(any()))
        .thenReturn(List.of(customField(5L, "客户等级"), customField(6L, "行业")));

    var page = service.availableFields("CUSTOMER", 1, 200);

    assertThat(page.getTotal()).isEqualTo(4);
    List<AvailableFieldResponse> items = page.getItems();
    assertThat(items)
        .extracting(AvailableFieldResponse::getFieldKey)
        .containsExactly("contactPerson", "phone", null, null);
    assertThat(items)
        .extracting(AvailableFieldResponse::getFieldId)
        .containsExactly(null, null, 5L, 6L);
    assertThat(items)
        .extracting(AvailableFieldResponse::isBuiltin)
        .containsExactly(true, true, false, false);
    assertThat(items)
        .extracting(AvailableFieldResponse::getFieldName)
        .containsExactly("联系人", "电话", "客户等级", "行业");
  }

  @Test
  @DisplayName("availableFields：没有任何可配字段（含实体名拼错）⇒ 422，而不是空表")
  void availableFieldsThrowsWhenEmpty() {
    assertThatThrownBy(() -> service.availableFields("NO_SUCH_ENTITY", 1, 200))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FIELD_PERMISSION_INVALID);
    assertThatThrownBy(() -> service.availableFields(null, 1, 200))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FIELD_PERMISSION_INVALID);
  }

  @Test
  @DisplayName("availableFields：越界的 page/pageSize 一律返回空表，不得抛异常（下拉不能被 500 打断）")
  void availableFieldsToleratesOutOfRangePaging() {
    when(customFieldMapper.selectList(any())).thenReturn(List.of(customField(5L, "客户等级")));

    assertThat(service.availableFields("CUSTOMER", 99, 20).getItems()).isEmpty();
    assertThat(service.availableFields("CUSTOMER", 1, 0).getItems())
        .hasSize(3); // pageSize<=0 ⇒ 一页到底
    assertThat(service.availableFields("CUSTOMER", 2, Long.MAX_VALUE).getItems()).isEmpty();
    assertThat(service.availableFields("CUSTOMER", 2, 2).getItems()).hasSize(1);
    assertThat(service.availableFields("CUSTOMER", 1, 100).getTotal()).isEqualTo(3);
  }

  @Test
  @DisplayName("availableFields：只列**启用中**的自定义字段（停用字段配不上权限）")
  void availableFieldsOnlyListsEnabledCustomFields() {
    when(customFieldMapper.selectList(any())).thenReturn(List.of());

    service.availableFields("OPPORTUNITY", 1, 200);

    String sql = capturedCustomSelectListSql();
    assertThat(sql).as("实际 SQL 段：%s", sql).contains("enabled");
    assertThat(sql).as("实际 SQL 段：%s", sql).contains("sort_order");
    assertThat(sql).as("实际 SQL 段：%s", sql).contains("entity_type");
  }

  // ===== page 的内置行装配 =====

  @Test
  @DisplayName("page：内置行的 fieldName 取注册表 label，且不为此查自定义字段表")
  void pageFillsBuiltinFieldName() {
    FieldPermission builtin = storedBuiltin("HIDDEN");
    when(permissionMapper.selectPage(any(), any()))
        .thenAnswer(
            invocation -> {
              com.baomidou.mybatisplus.extension.plugins.pagination.Page<FieldPermission> p =
                  invocation.getArgument(0);
              p.setRecords(List.of(builtin));
              p.setTotal(1);
              return p;
            });

    var page = service.page("SALES", "CUSTOMER", 1, 20);

    assertThat(page.getItems()).hasSize(1);
    assertThat(page.getItems().get(0).getFieldName()).isEqualTo("电话");
    // 内置行没有 fieldId ⇒ 不该为字段名去查自定义字段表（批量取名对空 id 列表直接返回）
    verify(customFieldMapper, never()).selectBatchIds(any());
  }

  @Test
  @DisplayName("page：字段定义已被删除 ⇒ fieldName 留 null（不是编一个占位串）")
  void pageLeavesFieldNameNullWhenCustomFieldMissing() {
    FieldPermission orphan = new FieldPermission();
    orphan.setId(3L);
    orphan.setRoleCode("SALES");
    orphan.setEntityType("CUSTOMER");
    orphan.setFieldId(999L);
    orphan.setPermission("HIDDEN");
    when(permissionMapper.selectPage(any(), any()))
        .thenAnswer(
            invocation -> {
              com.baomidou.mybatisplus.extension.plugins.pagination.Page<FieldPermission> p =
                  invocation.getArgument(0);
              p.setRecords(List.of(orphan));
              p.setTotal(1);
              return p;
            });
    when(customFieldMapper.selectBatchIds(any())).thenReturn(List.of());

    assertThat(service.page("SALES", "CUSTOMER", 1, 20).getItems().get(0).getFieldName()).isNull();
  }

  @Test
  @DisplayName("page：自定义字段名批量取一次（一页 N 条配置不产生 N 次查询）")
  void pageBatchesCustomFieldNames() {
    FieldPermission a = new FieldPermission();
    a.setId(1L);
    a.setRoleCode("SALES");
    a.setEntityType("CUSTOMER");
    a.setFieldId(5L);
    a.setPermission("HIDDEN");
    FieldPermission b = new FieldPermission();
    b.setId(2L);
    b.setRoleCode("SALES");
    b.setEntityType("CUSTOMER");
    b.setFieldId(6L);
    b.setPermission("READ_ONLY");
    when(permissionMapper.selectPage(any(), any()))
        .thenAnswer(
            invocation -> {
              com.baomidou.mybatisplus.extension.plugins.pagination.Page<FieldPermission> p =
                  invocation.getArgument(0);
              p.setRecords(List.of(a, b));
              p.setTotal(2);
              return p;
            });
    when(customFieldMapper.selectBatchIds(any()))
        .thenReturn(List.of(customField(5L, "客户等级"), customField(6L, "行业")));

    var page = service.page("SALES", "CUSTOMER", 1, 20);

    assertThat(page.getItems()).extracting("fieldName").containsExactly("客户等级", "行业");
    verify(customFieldMapper, times(1)).selectBatchIds(any());
  }

  // ===== 夹具 =====

  private static BuiltinField customerPhone() {
    return new BuiltinField("CUSTOMER", "phone", "电话");
  }

  private static FieldPermissionRequest request(String roleCode, Long fieldId, String fieldKey) {
    FieldPermissionRequest req = new FieldPermissionRequest();
    req.setRoleCode(roleCode);
    req.setEntityType("CUSTOMER");
    req.setFieldId(fieldId);
    req.setFieldKey(fieldKey);
    req.setPermission("HIDDEN");
    return req;
  }

  private static FieldPermission storedBuiltin(String permission) {
    FieldPermission fp = new FieldPermission();
    fp.setId(1L);
    fp.setRoleCode("SALES");
    fp.setEntityType("CUSTOMER");
    fp.setFieldKey("phone");
    fp.setPermission(permission);
    return fp;
  }

  private static CustomField customField(Long id, String name) {
    CustomField cf = new CustomField();
    cf.setId(id);
    cf.setEntityType("CUSTOMER");
    cf.setName(name);
    cf.setEnabled(1);
    cf.setSortOrder(0);
    return cf;
  }

  /** 抓取最后一次 selectOne 收到的 wrapper 的 SQL 片段（判据落在「查到哪一列」上）。 */
  private String capturedSelectOneSql() {
    ArgumentCaptor<LambdaQueryWrapper<FieldPermission>> captor = wrapperCaptor();
    verify(permissionMapper, org.mockito.Mockito.atLeastOnce()).selectOne(captor.capture());
    return captor.getValue().getSqlSegment();
  }

  private String capturedSelectListSql() {
    ArgumentCaptor<LambdaQueryWrapper<FieldPermission>> captor = wrapperCaptor();
    verify(permissionMapper, org.mockito.Mockito.atLeastOnce()).selectList(captor.capture());
    return captor.getValue().getSqlSegment();
  }

  private String capturedCustomSelectListSql() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaQueryWrapper<CustomField>> captor =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(customFieldMapper, org.mockito.Mockito.atLeastOnce()).selectList(captor.capture());
    return captor.getValue().getSqlSegment();
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<LambdaQueryWrapper<FieldPermission>> wrapperCaptor() {
    return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
  }
}
