package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.OutboundUrlValidator;
import com.crm.entity.WebhookDelivery;
import com.crm.entity.WebhookSubscription;
import com.crm.repository.WebhookDeliveryMapper;
import java.net.URI;
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

  /** 重定向最大跳数（FR-G13）。超出即中止投递，防止对端构造重定向环使投递线程空转。 */
  private static final int MAX_REDIRECTS = 3;

  private final WebhookDeliveryMapper deliveryMapper;
  private final RestTemplate restTemplate;
  private final OutboundUrlValidator outboundUrlValidator;

  public WebhookDeliverer(
      WebhookDeliveryMapper deliveryMapper,
      RestTemplate restTemplate,
      OutboundUrlValidator outboundUrlValidator) {
    this.deliveryMapper = deliveryMapper;
    this.restTemplate = restTemplate;
    this.outboundUrlValidator = outboundUrlValidator;
  }

  /** 异步投递（HMAC 签名 + ≤3 次退避重试 + 记录）。跨 bean 调用，@Async 代理生效。 */
  @Async
  public void deliverAsync(
      WebhookSubscription sub, String eventType, String entityType, Long entityId, String body) {
    // 投递前对**当前**地址再校验一次（FR-G13 的纵深防御，T075）：写入路径已在落库之前校验
    // （WebhookService.create），但那是"创建当时的"地址——修复前落库的行、以及 publishToUrl
    // 直接构造的临时订阅（集成通道，不经订阅表）都不经过那道门。
    // 校验放在这里，因为本方法是**所有**投递的唯一入口；放在重试循环**之外**，是因为地址能否
    // 出站不随重试改变（白名单是进程级配置），重试只会让异步线程白等最长 36 秒
    // （RETRY_DELAYS_MS 之和）再记一次同样的失败。
    try {
      outboundUrlValidator.validate(sub.getCallbackUrl(), ErrorCode.OPEN_WEBHOOK_URL_INVALID);
    } catch (BusinessException ex) {
      log.warn("Webhook delivery rejected for {}: {}", sub.getCallbackUrl(), ex.getMessage());
      record(sub, eventType, entityType, entityId, body, false, null, ex.getMessage(), 0);
      return;
    }
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
            postFollowingValidatedRedirects(sub.getCallbackUrl(), new HttpEntity<>(body, headers));
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

  /**
   * 逐跳校验重定向后再跟随（FR-G13）。
   *
   * <p><b>为什么需要这一段</b>：创建订阅时校验的是**落库的那个地址**，而重定向的落点由对端决定、当时并不存在。 只要客户端自动跟随 3xx，一条 {@code 302
   * Location: http://169.254.169.254/…} 就能让服务端去请求一个从未被校验的地址 ——创建时的校验因此形同虚设。故 {@code
   * RestTemplateConfig} 关掉自动跟随（那一半见其注释），此处负责另一半： 每一跳都用同一个校验器判定，不通过即抛错（由外层 catch 记为投递失败，不静默跳过）。
   *
   * <p><b>首跳不在这里校验</b>（T075 之后的形状）：起始地址由 {@link #deliverAsync} 在进入重试循环之前校验 ——
   * 那里能一次判死、不必重试，且是所有投递的唯一入口。于是本方法的不变式是"从第 1 跳起，每跳都已经过校验"， 第 0 跳由调用方负责。
   *
   * <p><b>跟随而非一律拒绝</b>：合法回调也可能用重定向（如迁移后的地址），一律拒绝会把合法集成一起打断。 但层数设上限，避免对端构造重定向环使投递线程空转。
   *
   * <p>抛出的 {@code BusinessException} 携带的是**既有**错误码 {@code OPEN_WEBHOOK_URL_INVALID}
   * ——与创建时用的是同一个码，故运维在投递记录里看到该码时，含义一致：这个回调地址不可出站。
   */
  private ResponseEntity<String> postFollowingValidatedRedirects(
      String url, HttpEntity<String> entity) {
    URI current = URI.create(url);
    for (int hop = 0; ; hop++) {
      ResponseEntity<String> resp = restTemplate.postForEntity(current, entity, String.class);
      if (!resp.getStatusCode().is3xxRedirection()) {
        return resp;
      }
      if (hop >= MAX_REDIRECTS) {
        throw new BusinessException(
            ErrorCode.OPEN_WEBHOOK_URL_INVALID, "重定向层数超过上限 " + MAX_REDIRECTS);
      }
      String location = resp.getHeaders().getFirst(HttpHeaders.LOCATION);
      // 落点必须过同一套校验：内网／回环／云元数据端点在此被拒，抛错即记为投递失败
      current =
          outboundUrlValidator.resolveRedirect(
              current, location, ErrorCode.OPEN_WEBHOOK_URL_INVALID);
      log.debug("Webhook redirect hop {} → {}", hop + 1, current);
    }
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
