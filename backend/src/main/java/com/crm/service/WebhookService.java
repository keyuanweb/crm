package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.open.DeliveryResponse;
import com.crm.dto.open.WebhookRequest;
import com.crm.dto.open.WebhookResponse;
import com.crm.entity.WebhookDelivery;
import com.crm.entity.WebhookSubscription;
import com.crm.repository.WebhookDeliveryMapper;
import com.crm.repository.WebhookSubscriptionMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/** Webhook 服务（055，FR-O05~O08）：订阅/异步推送 HMAC/重试/记录。 */
@Service
public class WebhookService {

  private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int MAX_RETRIES = 3;
  private static final long[] RETRY_DELAYS_MS = {1000, 5000, 30000};

  public static final String EVENT_LEAD_CREATED = "LEAD_CREATED";
  public static final String EVENT_LEAD_UPDATED = "LEAD_UPDATED";
  public static final String EVENT_CUSTOMER_CREATED = "CUSTOMER_CREATED";

  private final WebhookSubscriptionMapper subscriptionMapper;
  private final WebhookDeliveryMapper deliveryMapper;
  private final RestTemplate restTemplate;

  public WebhookService(
      WebhookSubscriptionMapper subscriptionMapper,
      WebhookDeliveryMapper deliveryMapper,
      RestTemplate restTemplate) {
    this.subscriptionMapper = subscriptionMapper;
    this.deliveryMapper = deliveryMapper;
    this.restTemplate = restTemplate;
  }

  // ===== 订阅管理 =====

  @Transactional
  public WebhookResponse create(WebhookRequest req) {
    WebhookSubscription sub = new WebhookSubscription();
    sub.setEventType(req.getEventType().trim());
    sub.setCallbackUrl(req.getCallbackUrl().trim());
    sub.setSecret(randomSecret());
    sub.setEnabled(1);
    sub.setCreatedBy(SecurityUtil.currentUserId());
    subscriptionMapper.insert(sub);
    WebhookResponse resp = toResponse(subscriptionMapper.selectById(sub.getId()));
    resp.setSecret(sub.getSecret());
    return resp;
  }

  public List<WebhookResponse> list() {
    return subscriptionMapper
        .selectList(
            new LambdaQueryWrapper<WebhookSubscription>().orderByDesc(WebhookSubscription::getId))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public WebhookResponse toggle(Long id) {
    WebhookSubscription sub = require(id);
    sub.setEnabled(sub.getEnabled() != null && sub.getEnabled() == 1 ? 0 : 1);
    subscriptionMapper.updateById(sub);
    return toResponse(subscriptionMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    subscriptionMapper.deleteById(id);
  }

  /** 推送记录分页。 */
  public PageResult<DeliveryResponse> deliveries(Long subscriptionId, long page, long pageSize) {
    var p =
        deliveryMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
            new LambdaQueryWrapper<WebhookDelivery>()
                .eq(WebhookDelivery::getSubscriptionId, subscriptionId)
                .orderByDesc(WebhookDelivery::getId));
    return PageResult.of(
        p.getRecords().stream().map(this::toDeliveryResponse).toList(),
        p.getTotal(),
        page,
        pageSize);
  }

  // ===== 事件发布 =====

  /** 发布业务事件：匹配订阅并异步推送（失败重试 ≤3 次退避）。 */
  public void publish(String eventType, String entityType, Long entityId, Map<String, Object> payload) {
    List<WebhookSubscription> subs =
        subscriptionMapper.selectList(
            new LambdaQueryWrapper<WebhookSubscription>()
                .eq(WebhookSubscription::getEventType, eventType)
                .eq(WebhookSubscription::getEnabled, 1));
    if (subs.isEmpty()) {
      return;
    }
    String body;
    try {
      Map<String, Object> envelope = new java.util.LinkedHashMap<>();
      envelope.put("event", eventType);
      envelope.put("entityType", entityType);
      envelope.put("entityId", entityId);
      envelope.put("timestamp", LocalDateTime.now().toString());
      envelope.put("data", payload);
      body = MAPPER.writeValueAsString(envelope);
    } catch (Exception ex) {
      log.warn("Webhook payload serialize failed: {}", ex.getMessage());
      return;
    }
    for (WebhookSubscription sub : subs) {
      deliverAsync(sub, eventType, entityType, entityId, body);
    }
  }

  @Async
  public void deliverAsync(
      WebhookSubscription sub, String eventType, String entityType, Long entityId, String body) {
    String signature = sign(sub.getSecret(), body);
    boolean success = false;
    String error = null;
    Integer httpStatus = null;
    int retries = 0;
    for (int attempt = 0; attempt <= MAX_RETRIES && !success; attempt++) {
      if (attempt > 0) {
        retries = attempt;
        try {
          Thread.sleep(RETRY_DELAYS_MS[Math.min(attempt - 1, RETRY_DELAYS_MS.length - 1)]);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      try {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Signature", signature);
        headers.set("X-Event", eventType);
        ResponseEntity<String> resp =
            restTemplate.postForEntity(
                sub.getCallbackUrl(), new HttpEntity<>(body, headers), String.class);
        httpStatus = resp.getStatusCode().value();
        success = httpStatus >= 200 && httpStatus < 300;
        if (!success) {
          error = "回调返回非 2xx: " + httpStatus;
        }
      } catch (Exception ex) {
        error = ex.getMessage();
        log.warn("Webhook deliver failed to {} (attempt {}): {}", sub.getCallbackUrl(), attempt + 1, ex.getMessage());
      }
    }
    record(sub, eventType, entityType, entityId, body, success, httpStatus, error, retries);
  }

  private void record(
      WebhookSubscription sub,
      String eventType,
      String entityType,
      Long entityId,
      String body,
      boolean success,
      Integer httpStatus,
      String error,
      int retries) {
    try {
      WebhookDelivery d = new WebhookDelivery();
      d.setSubscriptionId(sub.getId());
      d.setEventType(eventType);
      d.setEntityType(entityType);
      d.setEntityId(entityId);
      d.setPayload(body.length() > 4000 ? body.substring(0, 4000) : body);
      d.setStatus(success ? "SUCCESS" : "FAILED");
      d.setHttpStatus(httpStatus);
      d.setError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
      d.setRetryCount(retries);
      d.setCreatedAt(LocalDateTime.now());
      deliveryMapper.insert(d);
    } catch (Exception ex) {
      log.warn("Webhook record failed: {}", ex.getMessage());
    }
  }

  private String sign(String secret, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] bytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(bytes);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
  }

  private String randomSecret() {
    byte[] raw = new byte[24];
    RANDOM.nextBytes(raw);
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  private WebhookSubscription require(Long id) {
    WebhookSubscription sub = subscriptionMapper.selectById(id);
    if (sub == null) {
      throw new BusinessException(ErrorCode.OPEN_WEBHOOK_URL_INVALID);
    }
    return sub;
  }

  private WebhookResponse toResponse(WebhookSubscription sub) {
    WebhookResponse resp = new WebhookResponse();
    resp.setId(sub.getId());
    resp.setEventType(sub.getEventType());
    resp.setCallbackUrl(sub.getCallbackUrl());
    resp.setEnabled(sub.getEnabled() != null && sub.getEnabled() == 1);
    resp.setCreatedAt(sub.getCreatedAt());
    return resp;
  }

  private DeliveryResponse toDeliveryResponse(WebhookDelivery d) {
    DeliveryResponse resp = new DeliveryResponse();
    resp.setId(d.getId());
    resp.setSubscriptionId(d.getSubscriptionId());
    resp.setEventType(d.getEventType());
    resp.setEntityType(d.getEntityType());
    resp.setEntityId(d.getEntityId());
    resp.setStatus(d.getStatus());
    resp.setHttpStatus(d.getHttpStatus());
    resp.setError(d.getError());
    resp.setRetryCount(d.getRetryCount());
    resp.setCreatedAt(d.getCreatedAt());
    return resp;
  }
}
