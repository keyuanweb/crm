package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.entity.WebhookDelivery;
import com.crm.entity.WebhookSubscription;
import com.crm.repository.WebhookDeliveryMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
import org.springframework.web.client.RestTemplate;

/**
 * Webhook 异步投递器（独立 bean 使 @Async 代理生效——B1 安全/性能审计修复： 原 WebhookService 内自调用导致 @Async 失效、重试同步阻塞业务事务）。
 */
@Service
public class WebhookDeliverer {

  private static final Logger log = LoggerFactory.getLogger(WebhookDeliverer.class);
  private static final int MAX_RETRIES = 3;
  private static final long[] RETRY_DELAYS_MS = {1000, 5000, 30000};

  private final WebhookDeliveryMapper deliveryMapper;
  private final RestTemplate restTemplate;

  public WebhookDeliverer(WebhookDeliveryMapper deliveryMapper, RestTemplate restTemplate) {
    this.deliveryMapper = deliveryMapper;
    this.restTemplate = restTemplate;
  }

  /** 异步投递（HMAC 签名 + ≤3 次退避重试 + 记录）。跨 bean 调用，@Async 代理生效。 */
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
        log.warn(
            "Webhook deliver failed to {} (attempt {}): {}",
            sub.getCallbackUrl(),
            attempt + 1,
            ex.getMessage());
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
}
