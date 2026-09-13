package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.OutboundUrlValidator;
import com.crm.entity.WebhookDelivery;
import com.crm.entity.WebhookSubscription;
import com.crm.repository.WebhookDeliveryMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

  // 085（FR-V05/V06/V07）：投递记录的三个状态。PENDING 是新增的中间态，见 insertPending。
  private static final String STATUS_PENDING = "PENDING";
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_FAILED = "FAILED";

  /**
   * 085（FR-V07）：在途记录超过这个时长仍未转终态，即判定为**中断残留**。
   *
   * <p>必须<b>显著大于</b>最长重试窗口（{@link #RETRY_DELAYS_MS} 之和 = 36 秒），否则会把"刚好还在重试"的 记录误判为失败。取 2
   * 分钟留有充分余量。该阈值同时让清扫在**多实例**部署下安全：另一个实例可能确有在途 投递，但不会有任何一条**真实在途**记录老到 2 分钟。
   */
  private static final Duration STALE_PENDING_AFTER = Duration.ofMinutes(2);

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
    // 085（FR-V05）：**先落库，再投递**。记录在派发这一刻就存在（状态 PENDING），而不是等重试循环
    // 结束后才写终态——原形态下最长 36 秒内页面上查无此记录，且进程终止即彻底丢失。理由见 insertPending。
    // 返回值可能为 null（落库失败），finish 会自行跳过。
    WebhookDelivery delivery = insertPending(sub, eventType, entityType, entityId, body);

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
      // 地址被拒是**立即的**终态：复用同一条记录更新为失败，不另插一行（FR-V08）
      finish(delivery, false, null, ex.getMessage(), 0);
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
    finish(delivery, success, httpStatus, error, retries);
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

  /**
   * 085（FR-V05）：派发时插入一条**在途**记录，返回带数据库生成主键的实体（供 {@link #finish} 原地更新）。
   *
   * <p><b>为什么记录必须在派发时落地</b>：记录是"一次投递确实发生过"的<b>事实记录</b>。原形态在重试循环 <b>结束之后</b>才 insert，于是有两个后果：①最长 36
   * 秒（退避 1s+5s+30s）内页面上查无此记录，看起来像 "压根没派发过"，而运维者的排查窗口通常只有几十秒；②进程在这段窗口内终止，这条投递<b>永远不会</b>出现在
   * 记录里——事实发生过，记录里却查无此事。后者与 083 的 T078（对外记成功、实际未执行）属<b>同一类诚信缺口</b>。
   *
   * <p><b>为什么这次插入与 {@link #finish} 必须分处两条语句、不能包在同一个事务里</b>：两者之间隔着最长 36 秒的 重试窗口。包进一个事务会<b>持锁 36
   * 秒</b>，把一次异步投递变成对 {@code webhook_delivery} 行的长事务占用。 这是<b>刻意</b>的选择（章程原则三要求事务"限定在"Service
   * 层），不是遗漏。
   *
   * <p>落库失败返回 {@code null}（仅记 WARN）：记不下记录不应让投递本身失败。
   */
  private WebhookDelivery insertPending(
      WebhookSubscription sub, String eventType, String entityType, Long entityId, String body) {
    try {
      WebhookDelivery d = new WebhookDelivery();
      d.setSubscriptionId(sub.getId());
      d.setEventType(eventType);
      d.setEntityType(entityType);
      d.setEntityId(entityId);
      d.setPayload(truncate(body, 4000));
      d.setStatus(STATUS_PENDING);
      d.setRetryCount(0);
      d.setCreatedAt(LocalDateTime.now());
      deliveryMapper.insert(d);
      return d;
    } catch (Exception ex) {
      log.warn("Webhook record insert failed: {}", ex.getMessage());
      return null;
    }
  }

  /**
   * 085（FR-V06）：把派发时那条记录<b>原地更新</b>为终态，带上重试次数、HTTP 状态与错误摘要。
   *
   * <p><b>原地更新而非再插一条</b>（FR-V08）：一次投递只对应<b>一条</b>记录。若在此处 insert，一次投递就会留下 两条（PENDING + 终态），投递统计直接翻倍。
   *
   * <p>{@code delivery} 为 {@code null}（派发时落库失败）时直接跳过——没有可更新的行。
   */
  private void finish(
      WebhookDelivery delivery, boolean success, Integer httpStatus, String error, int retries) {
    if (delivery == null) {
      return;
    }
    try {
      delivery.setStatus(success ? STATUS_SUCCESS : STATUS_FAILED);
      delivery.setHttpStatus(httpStatus);
      delivery.setError(truncate(error, 500));
      delivery.setRetryCount(retries);
      deliveryMapper.updateById(delivery);
    } catch (Exception ex) {
      log.warn("Webhook record update failed: {}", ex.getMessage());
    }
  }

  /**
   * 085（FR-V07）：把**中断残留**的在途记录判定为终态，返回被判定的行数。
   *
   * <p>FR-V05 把记录的生命周期提前到"派发"那一刻，于是产生一个新的失败形态：进程在投递完成前终止时，该记录会
   * 永远停在"投递中"——那只是把"记录丢失"换成了"记录永远在途"。本方法负责给它一个<b>可判定</b>的归宿。
   *
   * <p>由 {@link com.crm.config.WebhookDeliverySweepScheduler} 周期性触发（不是只在启动时扫一次：若进程崩溃后
   * <b>很快</b>重启、早于阈值，那条残留当次不会被判定，此后若无重启就一直悬空）。
   *
   * <p>时间阈值见 {@link #STALE_PENDING_AFTER}。用 {@code update(实体, wrapper)} 批量判定：实体非空但 {@link
   * WebhookDelivery} 不带 {@code @Version}，故不涉及乐观锁。
   */
  public int sweepStalePending() {
    LocalDateTime cutoff = LocalDateTime.now().minus(STALE_PENDING_AFTER);
    WebhookDelivery patch = new WebhookDelivery();
    patch.setStatus(STATUS_FAILED);
    patch.setError("投递中断：进程在投递完成前终止，本记录由清扫判定为失败");
    return deliveryMapper.update(
        patch,
        new LambdaUpdateWrapper<WebhookDelivery>()
            .eq(WebhookDelivery::getStatus, STATUS_PENDING)
            .lt(WebhookDelivery::getCreatedAt, cutoff));
  }

  /** 截断超长文本（载荷入库存摘要、错误信息受列宽限制）。 */
  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() > max ? s.substring(0, max) : s;
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
