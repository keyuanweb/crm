package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.entity.EmailUnsubscribe;
import com.crm.service.EmailUnsubscribeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开邮件退订端点（052，邮件退订链接调用，免登录）。 */
@RestController
@RequestMapping("/api/v1/public/email")
@Tag(name = "邮件退订")
public class EmailUnsubscribeController {

  private final EmailUnsubscribeService unsubscribeService;

  public EmailUnsubscribeController(EmailUnsubscribeService unsubscribeService) {
    this.unsubscribeService = unsubscribeService;
  }

  @PostMapping("/unsubscribe")
  @Operation(summary = "邮件退订（公开，幂等）")
  public ApiResponse<EmailUnsubscribe> unsubscribe(
      @RequestBody Map<String, String> body) {
    EmailUnsubscribe record = unsubscribeService.unsubscribe(body.get("email"), null);
    return ApiResponse.ok(record);
  }
}
