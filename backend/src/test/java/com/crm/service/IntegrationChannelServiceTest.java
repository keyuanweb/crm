package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.integration.ChannelRequest;
import com.crm.entity.IntegrationChannel;
import com.crm.repository.IntegrationChannelMapper;
import com.crm.repository.WebhookDeliveryMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** IntegrationChannelService 单元测试（058 T008）：CRUD/URL 校验/推送。 */
class IntegrationChannelServiceTest {

  private IntegrationChannelMapper channelMapper;
  private WebhookDeliveryMapper deliveryMapper;
  private WebhookService webhookService;
  private IntegrationChannelService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, IntegrationChannel.class);
  }

  @BeforeEach
  void setUp() {
    channelMapper = mock(IntegrationChannelMapper.class);
    deliveryMapper = mock(WebhookDeliveryMapper.class);
    webhookService = mock(WebhookService.class);
    service = new IntegrationChannelService(channelMapper, deliveryMapper, webhookService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private ChannelRequest request(String url) {
    ChannelRequest req = new ChannelRequest();
    req.setChannelType("WECHAT_WORK");
    req.setName("销售群");
    req.setWebhookUrl(url);
    req.setEnabled(true);
    return req;
  }

  @Test
  @DisplayName("创建：合法 URL → 成功")
  void createSucceeds() {
    when(channelMapper.insert(any(IntegrationChannel.class)))
        .thenAnswer(
            invocation -> {
              IntegrationChannel c = invocation.getArgument(0);
              c.setId(1L);
              return 1;
            });
    IntegrationChannel stored = new IntegrationChannel();
    stored.setId(1L);
    stored.setChannelType("WECHAT_WORK");
    stored.setName("销售群");
    stored.setWebhookUrl("https://qyapi.weixin.qq.com/abc");
    stored.setEnabled(1);
    when(channelMapper.selectById(1L)).thenReturn(stored);

    var resp = service.create(request("https://qyapi.weixin.qq.com/abc"));

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getChannelType()).isEqualTo("WECHAT_WORK");
  }

  @Test
  @DisplayName("创建：非法 URL → 422")
  void createInvalidUrlThrows() {
    assertThatThrownBy(() -> service.create(request("ftp://bad")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INTEGRATION_URL_INVALID);
  }

  @Test
  @DisplayName("发布事件：无启用通道 → 跳过不推送")
  void publishNoChannelsSkips() {
    when(channelMapper.selectList(any())).thenReturn(List.of());
    service.publish("TICKET_ASSIGNED", "工单 #1 已分配");
    verify(webhookService, never()).publishToUrl(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("发布事件：启用通道 → 推送")
  void publishPushesToChannels() {
    IntegrationChannel c = new IntegrationChannel();
    c.setId(1L);
    c.setChannelType("CUSTOM");
    c.setName("自定义");
    c.setWebhookUrl("http://localhost:9999/hook");
    c.setEnabled(1);
    when(channelMapper.selectList(any())).thenReturn(List.of(c));

    service.publish("TICKET_ASSIGNED", "工单 #1 已分配");

    verify(webhookService)
        .publishToUrl(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq("TICKET_ASSIGNED"),
            any(),
            any(),
            any(),
            any(),
            any());
  }
}
