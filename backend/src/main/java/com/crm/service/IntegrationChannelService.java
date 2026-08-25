package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.integration.ChannelRequest;
import com.crm.dto.integration.ChannelResponse;
import com.crm.entity.IntegrationChannel;
import com.crm.entity.WebhookDelivery;
import com.crm.repository.IntegrationChannelMapper;
import com.crm.repository.WebhookDeliveryMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 集成中心服务（058，FR-I01~I09）：通道 CRUD + 事件推送（复用 055）。 */
@Service
public class IntegrationChannelService {

  public static final String TYPE_WECHAT_WORK = "WECHAT_WORK";
  public static final String TYPE_DINGTALK = "DINGTALK";
  public static final String TYPE_CUSTOM = "CUSTOM";

  private final IntegrationChannelMapper channelMapper;
  private final WebhookDeliveryMapper deliveryMapper;
  private final WebhookService webhookService;

  public IntegrationChannelService(
      IntegrationChannelMapper channelMapper,
      WebhookDeliveryMapper deliveryMapper,
      WebhookService webhookService) {
    this.channelMapper = channelMapper;
    this.deliveryMapper = deliveryMapper;
    this.webhookService = webhookService;
  }

  // ===== 通道管理 =====

  public List<ChannelResponse> list() {
    return channelMapper
        .selectList(
            new LambdaQueryWrapper<IntegrationChannel>().orderByDesc(IntegrationChannel::getId))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public ChannelResponse create(ChannelRequest req) {
    validate(req);
    IntegrationChannel c = new IntegrationChannel();
    c.setChannelType(req.getChannelType().trim());
    c.setName(req.getName().trim());
    c.setWebhookUrl(req.getWebhookUrl().trim());
    c.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
    c.setCreatedBy(SecurityUtil.currentUserId());
    channelMapper.insert(c);
    return toResponse(channelMapper.selectById(c.getId()));
  }

  @Transactional
  public ChannelResponse update(Long id, ChannelRequest req) {
    IntegrationChannel existing = require(id);
    validate(req);
    existing.setChannelType(req.getChannelType().trim());
    existing.setName(req.getName().trim());
    existing.setWebhookUrl(req.getWebhookUrl().trim());
    existing.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
    channelMapper.updateById(existing);
    return toResponse(channelMapper.selectById(id));
  }

  @Transactional
  public ChannelResponse toggle(Long id) {
    IntegrationChannel existing = require(id);
    existing.setEnabled(existing.getEnabled() != null && existing.getEnabled() == 1 ? 0 : 1);
    channelMapper.updateById(existing);
    return toResponse(channelMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    channelMapper.deleteById(id);
  }

  /** 通道推送记录（复用 webhook_delivery）。 */
  public PageResult<WebhookDelivery> deliveries(Long channelId, long page, long pageSize) {
    require(channelId);
    var p =
        deliveryMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
            new LambdaQueryWrapper<WebhookDelivery>()
                .eq(WebhookDelivery::getSubscriptionId, channelId)
                .orderByDesc(WebhookDelivery::getId));
    return PageResult.of(p.getRecords(), p.getTotal(), page, pageSize);
  }

  // ===== 事件推送 =====

  /** 推送业务事件到全部启用通道（异步，复用 055 重试/记录）。 */
  public void publish(String eventType, String title) {
    List<IntegrationChannel> channels =
        channelMapper.selectList(
            new LambdaQueryWrapper<IntegrationChannel>()
                .eq(IntegrationChannel::getEnabled, 1));
    if (channels.isEmpty()) {
      return;
    }
    String content = "【" + eventType + "】" + title;
    Map<String, Object> payload =
        Map.of(
            "msgtype", "text",
            "text", Map.of("content", content));
    for (IntegrationChannel channel : channels) {
      // 复用 055 WebhookService：构造临时订阅语义推送（subscriptionId=channelId 用于记录关联）
      webhookService.publishToUrl(
          channel.getId(),
          eventType,
          "INTEGRATION",
          null,
          payload,
          channel.getWebhookUrl(),
          channel.getName());
    }
  }

  private void validate(ChannelRequest req) {
    String url = req.getWebhookUrl().trim();
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      throw new BusinessException(ErrorCode.INTEGRATION_URL_INVALID);
    }
  }

  private IntegrationChannel require(Long id) {
    IntegrationChannel c = channelMapper.selectById(id);
    if (c == null) {
      throw new BusinessException(ErrorCode.INTEGRATION_CHANNEL_NOT_FOUND);
    }
    return c;
  }

  private ChannelResponse toResponse(IntegrationChannel c) {
    ChannelResponse resp = new ChannelResponse();
    resp.setId(c.getId());
    resp.setChannelType(c.getChannelType());
    resp.setName(c.getName());
    resp.setWebhookUrl(c.getWebhookUrl());
    resp.setEnabled(c.getEnabled() != null && c.getEnabled() == 1);
    resp.setCreatedAt(c.getCreatedAt());
    return resp;
  }
}
