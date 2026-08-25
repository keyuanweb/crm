package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.integration.ChannelRequest;
import com.crm.dto.integration.ChannelResponse;
import com.crm.entity.WebhookDelivery;
import com.crm.service.IntegrationChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 集成中心接口（058）。 */
@RestController
@RequestMapping("/api/v1/integration-channels")
@Tag(name = "集成中心")
@PreAuthorize("hasRole('ADMIN')")
public class IntegrationChannelController {

  private final IntegrationChannelService channelService;

  public IntegrationChannelController(IntegrationChannelService channelService) {
    this.channelService = channelService;
  }

  @GetMapping
  @Operation(summary = "通道列表")
  public ApiResponse<List<ChannelResponse>> list() {
    return ApiResponse.ok(channelService.list());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建通道")
  public ApiResponse<ChannelResponse> create(@Valid @RequestBody ChannelRequest request) {
    return ApiResponse.ok(channelService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑通道")
  public ApiResponse<ChannelResponse> update(
      @PathVariable Long id, @Valid @RequestBody ChannelRequest request) {
    return ApiResponse.ok(channelService.update(id, request));
  }

  @PostMapping("/{id}/toggle")
  @Operation(summary = "启停通道")
  public ApiResponse<ChannelResponse> toggle(@PathVariable Long id) {
    return ApiResponse.ok(channelService.toggle(id));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除通道")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    channelService.delete(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/{id}/deliveries")
  @Operation(summary = "通道推送记录")
  public ApiResponse<PageResult<WebhookDelivery>> deliveries(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(channelService.deliveries(id, page, pageSize));
  }
}
