package com.crm.controller;

import com.crm.security.RateLimit;
import com.crm.security.RateLimitDimension;
import com.crm.service.EmailCampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件追踪公开端点（030-email-marketing）：打开像素 + 点击重定向。 无 JWT（邮件客户端加载），仅记录事件。
 *
 * <p><b>频控（100-rate-limit-consolidation）</b>：两个端点各自带 {@link RateLimit}，配额 <b>逐字沿用改造前的数字</b>——60 次 /
 * 60 秒、按 <b>IP</b> 分桶。改造前这里有一套私有的进程内 滑动窗口（{@code Map<String, Deque<Long>>} + {@code
 * synchronized}），现已删除并委托给共享限流件： 同一份实现同时服务表单提交、导出、开放 API 与公开只读端点，阈值语义（含第 60 次、第 61 次被拒）
 * 与改造前一致，但<b>计数介质从单 JVM 内存换成 Redis</b> ⇒ 多实例部署下阈值不再成倍放大。
 *
 * <p>⚠️ <b>两处如实说明的语义变化（同一批的第 ④、⑥ 处对外变更）</b>：
 *
 * <ol>
 *   <li><b>窗口算法：滑动窗口 → 固定窗口</b>。旧实现是时间戳队列（任意 60 秒跨度内 ≤ 60 次）， 新件是 {@code INCR} + 首次 {@code EXPIRE}
 *       的固定窗口 ⇒ <b>窗口内部逐字等价</b>（第 60 次放行、 第 61 次拒绝）， 但<b>跨窗口边界</b>最坏可放行 2× 突发。这是选固定窗口的既定代价（不用
 *       Lua/令牌桶）， 已登记为债务。
 *   <li><b>IP 解析：私有副本 → {@code ClientIpResolver}</b>。旧副本遇 {@code X-Forwarded-For = ","} 会抛 {@code
 *       ArrayIndexOutOfBoundsException}（{@code split} 丢弃末尾空段）⇒ <b>一条潜伏的 500</b>； 新件改用 {@code
 *       indexOf(',')} 并一律回退到 {@code remoteAddr}。
 * </ol>
 *
 * <p>⚠️ <b>改造前这句注释是错的，这里如实订正</b>。原文：<br>
 * 「IP 频控：1 分钟 ≤60 次；超限返回 429 语义（此处直接降级为 400 保持公开端点简单）」<br>
 * 实现从来<b>没有</b>降级为 400 —— 私有异常由 {@code GlobalExceptionHandler} 里一个专用处理器渲染成 <b>429</b>，且 {@code
 * error.code} 是硬编码的裸字符串 {@code "TOO_MANY_REQUESTS"}。收敛后该码变为 {@code ErrorCode.RATE_LIMITED}（同 429），
 * 响应体走统一 {@code ApiResponse} 信封，并新增标准的 {@code Retry-After} 头。
 * <b>这是本批唯一一处对外可见的字符串变更</b>，单独落在本次提交里以便日后二分。
 *
 * <p>⚠️ <b>两个端点共用同一个 scope {@code public-email-track}，且与表单提交的 {@code public-form-submit}
 * 分开</b>：前者是<b>逐字沿用</b>改造前的行为（两个端点本来就共用同一个 {@code rateBuckets}，像素与点击合起来数
 * 60/60s），后者是<b>有意不合并</b>——合并会让「邮件客户端 预取/加载像素」这种自然高频的行为挤掉表单提交的配额，而这两件事的风险与自然频率完全不同。
 */
@RestController
@RequestMapping("/api/v1/public/track")
@Tag(name = "邮件追踪（公开）")
public class EmailTrackController {

  private final EmailCampaignService campaignService;

  public EmailTrackController(EmailCampaignService campaignService) {
    this.campaignService = campaignService;
  }

  @GetMapping(value = "/open/{sendLogId}", produces = MediaType.IMAGE_GIF_VALUE)
  @Operation(summary = "打开追踪像素")
  @RateLimit(
      scope = "public-email-track",
      limit = 60,
      windowSeconds = 60,
      by = RateLimitDimension.IP)
  public ResponseEntity<byte[]> trackOpen(@PathVariable Long sendLogId) {
    byte[] gif = campaignService.trackOpen(sendLogId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .contentType(MediaType.IMAGE_GIF)
        .body(gif);
  }

  @GetMapping("/click/{sendLogId}")
  @Operation(summary = "点击追踪重定向")
  @RateLimit(
      scope = "public-email-track",
      limit = 60,
      windowSeconds = 60,
      by = RateLimitDimension.IP)
  public ResponseEntity<Void> trackClick(
      @PathVariable Long sendLogId, @RequestParam(required = false) String url) {
    String target = "/";
    if (url != null && !url.isBlank()) {
      try {
        target = URLDecoder.decode(url, StandardCharsets.UTF_8);
      } catch (Exception ex) {
        target = url;
      }
    }
    String redirect = campaignService.trackClick(sendLogId, target);
    return ResponseEntity.status(302).header(HttpHeaders.LOCATION, redirect).build();
  }
}
