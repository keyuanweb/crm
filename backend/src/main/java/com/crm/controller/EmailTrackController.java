package com.crm.controller;

import com.crm.service.EmailCampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 邮件追踪公开端点（030-email-marketing）：打开像素 + 点击重定向。 无 JWT（邮件客户端加载），仅记录事件。 */
@RestController
@RequestMapping("/api/v1/public/track")
@Tag(name = "邮件追踪（公开）")
public class EmailTrackController {

  private static final int RATE_LIMIT = 60;
  private static final long RATE_WINDOW_MS = 60_000L;

  /** IP → 请求时间戳队列（防刷统计）。 */
  private final Map<String, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

  private final EmailCampaignService campaignService;

  public EmailTrackController(EmailCampaignService campaignService) {
    this.campaignService = campaignService;
  }

  @GetMapping(value = "/open/{sendLogId}", produces = MediaType.IMAGE_GIF_VALUE)
  @Operation(summary = "打开追踪像素")
  public ResponseEntity<byte[]> trackOpen(
      @PathVariable Long sendLogId, HttpServletRequest request) {
    checkRateLimit(request);
    byte[] gif = campaignService.trackOpen(sendLogId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .contentType(MediaType.IMAGE_GIF)
        .body(gif);
  }

  @GetMapping("/click/{sendLogId}")
  @Operation(summary = "点击追踪重定向")
  public ResponseEntity<Void> trackClick(
      @PathVariable Long sendLogId,
      @RequestParam(required = false) String url,
      HttpServletRequest request) {
    checkRateLimit(request);
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

  /** IP 频控：1 分钟 ≤60 次；超限返回 429 语义（此处直接降级为 400 保持公开端点简单）。 */
  private void checkRateLimit(HttpServletRequest request) {
    if (request == null) {
      return;
    }
    String ip = clientIp(request);
    long now = System.currentTimeMillis();
    Deque<Long> queue = rateBuckets.computeIfAbsent(ip, k -> new ArrayDeque<>());
    synchronized (queue) {
      while (!queue.isEmpty() && now - queue.peekFirst() > RATE_WINDOW_MS) {
        queue.pollFirst();
      }
      if (queue.size() >= RATE_LIMIT) {
        throw new RateLimitedException();
      }
      queue.addLast(now);
    }
    // 定期清理过期桶
    if (rateBuckets.size() > 1000) {
      rateBuckets
          .entrySet()
          .removeIf(
              e -> {
                Deque<Long> q = e.getValue();
                synchronized (q) {
                  while (!q.isEmpty() && now - q.peekFirst() > RATE_WINDOW_MS) {
                    q.pollFirst();
                  }
                  return q.isEmpty();
                }
              });
    }
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwarded)) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  /** 频控异常（由全局异常处理映射为 429/400）。 */
  public static class RateLimitedException extends RuntimeException {
    public RateLimitedException() {
      super("请求过于频繁");
    }
  }
}
