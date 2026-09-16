package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.entity.EmailUnsubscribe;
import com.crm.security.RateLimit;
import com.crm.security.RateLimitDimension;
import com.crm.service.EmailUnsubscribeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开邮件退订端点（052，邮件退订链接调用，免登录）。
 *
 * <p>⚠️ <b>2026-09-17（100-rate-limit-consolidation）</b>：本端点匿名可达、<b>有写副作用</b>（落一条退订记录），且入参是
 * <b>邮箱</b>（可被用来批量退订或探测邮箱是否存在）⇒ 挂 {@link RateLimit}，维度取 <b>IP</b>（匿名请求没有更细的主体）。 10/60s 的量级与同为匿名写路径的
 * {@code public-ticket-submit}（5/60s）刻意区分：这里一次点击只写一行、没有枚举面。
 */
@RestController
@RequestMapping("/api/v1/public/email")
@Tag(name = "邮件退订")
public class EmailUnsubscribeController {

  private final EmailUnsubscribeService unsubscribeService;

  public EmailUnsubscribeController(EmailUnsubscribeService unsubscribeService) {
    this.unsubscribeService = unsubscribeService;
  }

  @PostMapping("/unsubscribe")
  @RateLimit(
      scope = "public-unsubscribe",
      limit = 10,
      windowSeconds = 60,
      by = RateLimitDimension.IP)
  @Operation(summary = "邮件退订（公开，幂等）")
  public ApiResponse<EmailUnsubscribe> unsubscribe(@RequestBody Map<String, String> body) {
    EmailUnsubscribe record = unsubscribeService.unsubscribe(body.get("email"), null);
    return ApiResponse.ok(record);
  }
}
