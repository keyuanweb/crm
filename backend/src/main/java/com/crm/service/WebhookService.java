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
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/** Webhook 服务（055，FR-O05~O08）：订阅/异步推送 HMAC/重试/记录（异步投递拆至 WebhookDeliverer）。 */
@Service
public class WebhookService {

  private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SecureRandom RANDOM = new SecureRandom();

  public static final String EVENT_LEAD_CREATED = "LEAD_CREATED";
  public static final String EVENT_LEAD_UPDATED = "LEAD_UPDATED";
  public static final String EVENT_CUSTOMER_CREATED = "CUSTOMER_CREATED";

  private final WebhookSubscriptionMapper subscriptionMapper;
  private final WebhookDeliveryMapper deliveryMapper;
  private final RestTemplate restTemplate;
  private final WebhookDeliverer deliverer;

  public WebhookService(
      WebhookSubscriptionMapper subscriptionMapper,
      WebhookDeliveryMapper deliveryMapper,
      RestTemplate restTemplate,
      WebhookDeliverer deliverer) {
    this.subscriptionMapper = subscriptionMapper;
    this.deliveryMapper = deliveryMapper;
    this.restTemplate = restTemplate;
    this.deliverer = deliverer;
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
  public void publish(
      String eventType, String entityType, Long entityId, Map<String, Object> payload) {
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
      deliverer.deliverAsync(sub, eventType, entityType, entityId, body);
    }
  }

  /** 058：直接推送到指定 URL（集成通道用，不经订阅表），复用重试/记录机制。 */
  public void publishToUrl(
      Long subscriptionId,
      String eventType,
      String entityType,
      Long entityId,
      Map<String, Object> payload,
      String url,
      String name) {
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
    WebhookSubscription sub = new WebhookSubscription();
    sub.setId(subscriptionId);
    sub.setEventType(eventType);
    sub.setCallbackUrl(url);
    sub.setSecret(name == null ? "integration" : name);
    sub.setEnabled(1);
    deliverer.deliverAsync(sub, eventType, entityType, entityId, body);
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
