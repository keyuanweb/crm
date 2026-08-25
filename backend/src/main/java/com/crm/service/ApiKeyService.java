package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.open.ApiKeyRequest;
import com.crm.dto.open.ApiKeyResponse;
import com.crm.entity.ApiKey;
import com.crm.repository.ApiKeyMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 开放平台 API Key 服务（055，FR-O01~O04）：生成/校验/吊销/使用记录。 */
@Service
public class ApiKeyService {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String PREFIX = "ck_";

  /** 启用 Key 缓存（id→ApiKey），TTL 由调用方结合 lastUsed 判断；简单缓存 60s 由 getEnabledKey 重新查询。 */
  private final Map<Long, ApiKey> cache = new ConcurrentHashMap<>();

  private final ApiKeyMapper apiKeyMapper;

  public ApiKeyService(ApiKeyMapper apiKeyMapper) {
    this.apiKeyMapper = apiKeyMapper;
  }

  /** 生成 API Key（完整值仅返回一次）。 */
  @Transactional
  public ApiKeyResponse create(ApiKeyRequest req) {
    byte[] raw = new byte[24];
    RANDOM.nextBytes(raw);
    String fullKey = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    ApiKey key = new ApiKey();
    key.setName(req.getName().trim());
    key.setKeyHash(hash(fullKey));
    key.setKeyPrefix(fullKey.substring(0, Math.min(10, fullKey.length())));
    key.setScopes(writeJson(req.getScopes()));
    key.setExpiresAt(req.getExpiresAt());
    key.setStatus("ACTIVE");
    key.setUseCount(0L);
    key.setCreatedBy(SecurityUtil.currentUserId());
    apiKeyMapper.insert(key);

    ApiKeyResponse resp = toResponse(apiKeyMapper.selectById(key.getId()));
    resp.setKey(fullKey);
    return resp;
  }

  public PageResult<ApiKeyResponse> page(long page, long pageSize) {
    var p =
        apiKeyMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
            new LambdaQueryWrapper<ApiKey>().orderByDesc(ApiKey::getId));
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public void revoke(Long id) {
    ApiKey key = apiKeyMapper.selectById(id);
    if (key == null) {
      throw new BusinessException(ErrorCode.OPEN_API_KEY_INVALID);
    }
    key.setStatus("REVOKED");
    apiKeyMapper.updateById(key);
    cache.remove(id);
  }

  /** 校验 X-API-Key（哈希匹配 + ACTIVE + 未过期），并记录使用。 */
  public ApiKey authenticate(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      throw new BusinessException(ErrorCode.OPEN_API_KEY_REQUIRED);
    }
    String hash = hash(rawKey.trim());
    ApiKey key =
        apiKeyMapper.selectOne(
            new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getKeyHash, hash).last("LIMIT 1"));
    if (key == null
        || !"ACTIVE".equals(key.getStatus())
        || (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now()))) {
      throw new BusinessException(ErrorCode.OPEN_API_KEY_INVALID);
    }
    // 更新使用记录（异步友好：直接更新，量小）
    key.setLastUsedAt(LocalDateTime.now());
    key.setUseCount((key.getUseCount() == null ? 0 : key.getUseCount()) + 1);
    apiKeyMapper.updateById(key);
    return key;
  }

  /** 权限范围校验。 */
  public void requireScope(ApiKey key, String scope) {
    List<String> scopes = readScopes(key.getScopes());
    if (!scopes.contains(scope)) {
      throw new BusinessException(ErrorCode.OPEN_API_SCOPE_DENIED);
    }
  }

  /** 按 key id 查 Key（开放端点 scope 校验用）。 */
  public ApiKey getById(Long keyId) {
    ApiKey key = apiKeyMapper.selectById(keyId);
    if (key == null) {
      throw new BusinessException(ErrorCode.OPEN_API_KEY_INVALID);
    }
    return key;
  }

  private String hash(String raw) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
  }

  private String writeJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception ex) {
      return null;
    }
  }

  private List<String> readScopes(String scopes) {
    if (scopes == null || scopes.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(scopes, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      return List.of();
    }
  }

  private ApiKeyResponse toResponse(ApiKey key) {
    ApiKeyResponse resp = new ApiKeyResponse();
    resp.setId(key.getId());
    resp.setName(key.getName());
    resp.setKeyPrefix(key.getKeyPrefix());
    resp.setScopes(readScopes(key.getScopes()));
    resp.setExpiresAt(key.getExpiresAt());
    resp.setStatus(key.getStatus());
    resp.setLastUsedAt(key.getLastUsedAt());
    resp.setUseCount(key.getUseCount());
    resp.setCreatedAt(key.getCreatedAt());
    return resp;
  }
}
