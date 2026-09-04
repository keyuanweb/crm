package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.personal.PersonalInfoResponse;
import com.crm.dto.personal.UpdateDisplayNameRequest;
import com.crm.security.SecurityUtil;
import com.crm.service.PersonalCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 个人中心接口（070-personal-center，FR-001~008）。 */
@RestController
@RequestMapping("/api/v1/personal")
@Tag(name = "个人中心")
public class PersonalCenterController {

  private final PersonalCenterService personalCenterService;

  public PersonalCenterController(PersonalCenterService personalCenterService) {
    this.personalCenterService = personalCenterService;
  }

  /** 获取当前登录用户的个人信息。 */
  @GetMapping("/info")
  @Operation(summary = "获取当前用户个人信息")
  public ApiResponse<PersonalInfoResponse> getPersonalInfo() {
    Long userId = SecurityUtil.currentUserId();
    if (userId == null) {
      throw new RuntimeException("用户未登录");
    }
    PersonalInfoResponse response = personalCenterService.getPersonalInfo(userId);
    return ApiResponse.ok(response);
  }

  /** 修改当前用户的显示名。 */
  @PutMapping("/display-name")
  @Operation(summary = "修改当前用户显示名")
  public ApiResponse<Void> updateDisplayName(@Valid @RequestBody UpdateDisplayNameRequest request) {
    Long userId = SecurityUtil.currentUserId();
    if (userId == null) {
      throw new RuntimeException("用户未登录");
    }
    personalCenterService.updateDisplayName(userId, request);
    return ApiResponse.ok();
  }
}
