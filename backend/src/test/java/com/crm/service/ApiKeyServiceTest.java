package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.open.ApiKeyRequest;
import com.crm.entity.ApiKey;
import com.crm.repository.ApiKeyMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** ApiKeyService 单元测试（055 T010）：生成/鉴权/吊销/范围。 */
class ApiKeyServiceTest {

  private ApiKeyMapper apiKeyMapper;
  private ApiKeyService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, ApiKey.class);
  }

  @BeforeEach
  void setUp() {
    apiKeyMapper = mock(ApiKeyMapper.class);
    service = new ApiKeyService(apiKeyMapper);
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

  private ApiKeyRequest request() {
    ApiKeyRequest req = new ApiKeyRequest();
    req.setName("数据同步");
    req.setScopes(List.of("customer:read"));
    return req;
  }

  @Test
  @DisplayName("创建：返回完整 key + 前缀")
  void createReturnsKey() {
    when(apiKeyMapper.insert(any(ApiKey.class)))
        .thenAnswer(
            invocation -> {
              ApiKey k = invocation.getArgument(0);
              k.setId(1L);
              return 1;
            });
    ApiKey stored = new ApiKey();
    stored.setId(1L);
    stored.setName("数据同步");
    stored.setKeyPrefix("ck_");
    stored.setScopes("[\"customer:read\"]");
    stored.setStatus("ACTIVE");
    when(apiKeyMapper.selectById(1L)).thenReturn(stored);

    var resp = service.create(request());

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getKey()).startsWith("ck_");
    assertThat(resp.getKeyPrefix()).isNotNull();
  }

  @Test
  @DisplayName("鉴权：哈希匹配 + ACTIVE → 返回 Key")
  void authenticateOk() {
    ApiKey key = new ApiKey();
    key.setId(1L);
    key.setKeyHash("abc");
    key.setStatus("ACTIVE");
    key.setUseCount(0L);
    when(apiKeyMapper.selectOne(any())).thenReturn(key);

    var result = service.authenticate("raw-key");

    assertThat(result.getId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("鉴权：无效 Key → 401")
  void authenticateInvalidThrows() {
    when(apiKeyMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.authenticate("bad-key"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPEN_API_KEY_INVALID);
  }

  @Test
  @DisplayName("鉴权：吊销 Key → 401")
  void authenticateRevokedThrows() {
    ApiKey key = new ApiKey();
    key.setId(1L);
    key.setKeyHash("abc");
    key.setStatus("REVOKED");
    when(apiKeyMapper.selectOne(any())).thenReturn(key);

    assertThatThrownBy(() -> service.authenticate("raw-key"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPEN_API_KEY_INVALID);
  }

  @Test
  @DisplayName("083：鉴权只写使用记录两列，不得回写授权状态（否则在途鉴权会撤销并发 revoke）")
  void authenticateNarrowsWriteToUsageColumns() {
    ApiKey key = new ApiKey();
    key.setId(1L);
    key.setKeyHash("abc");
    key.setStatus("ACTIVE");
    when(apiKeyMapper.selectOne(any())).thenReturn(key);

    service.authenticate("raw-key");

    ArgumentCaptor<LambdaUpdateWrapper<ApiKey>> captor =
        ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    verify(apiKeyMapper).update(isNull(), captor.capture());
    String sqlSet = captor.getValue().getSqlSet();

    // 使用记录确实被写：少了这一条，"干脆什么都不写"也会让本用例通过
    assertThat(sqlSet).contains("use_count = use_count + 1");
    assertThat(sqlSet).contains("last_used_at");
    // 授权列一概不得出现。旧写法 updateById(key) 会把 status=ACTIVE 一并写回，
    // 覆盖并发 revoke() 刚落库的 REVOKED —— 吊销失效。这是本用例真正要钉住的东西。
    assertThat(sqlSet).doesNotContain("status");
    // 同一原因：不得再走"整实体回写"的老路
    verify(apiKeyMapper, never()).updateById(any());
  }

  @Test
  @DisplayName("范围校验：无权限 → 403")
  void requireScopeDenied() {
    ApiKey key = new ApiKey();
    key.setScopes("[\"customer:read\"]");

    assertThatThrownBy(() -> service.requireScope(key, "lead:write"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPEN_API_SCOPE_DENIED);
  }
}
