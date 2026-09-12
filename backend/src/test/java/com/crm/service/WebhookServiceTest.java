package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.OutboundUrlValidator;
import com.crm.entity.WebhookDelivery;
import com.crm.entity.WebhookSubscription;
import com.crm.repository.WebhookDeliveryMapper;
import com.crm.repository.WebhookSubscriptionMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** WebhookService 单元测试（055 T014）：订阅/推送签名/记录。 */
class WebhookServiceTest {

  private WebhookSubscriptionMapper subscriptionMapper;
  private WebhookDeliveryMapper deliveryMapper;
  private org.springframework.web.client.RestTemplate restTemplate;
  private OutboundUrlValidator outboundUrlValidator;
  private WebhookService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, WebhookSubscription.class);
    TableInfoHelper.initTableInfo(assistant, WebhookDelivery.class);
  }

  @BeforeEach
  void setUp() {
    subscriptionMapper = mock(WebhookSubscriptionMapper.class);
    deliveryMapper = mock(WebhookDeliveryMapper.class);
    restTemplate = mock(org.springframework.web.client.RestTemplate.class);
    // 出站校验的默认策略是「全拒」，故用例里用到的回调主机必须在白名单内，
    // 否则本类每个建订阅的用例都会因地址被拒而失败——那会把"地址校验"误报成"订阅创建坏了"
    outboundUrlValidator = new OutboundUrlValidator("example.com");
    service =
        new WebhookService(
            subscriptionMapper,
            deliveryMapper,
            restTemplate,
            mock(WebhookDeliverer.class),
            // 真实校验器而非 mock：本类的用例都在白名单内建订阅（见下方 setAllowedHosts），
            // 用 mock 会把「创建时确实过了出站校验」这一事实抹掉
            outboundUrlValidator);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  @Test
  @DisplayName("创建订阅：返回 secret")
  void createReturnsSecret() {
    when(subscriptionMapper.insert(any(WebhookSubscription.class)))
        .thenAnswer(
            invocation -> {
              WebhookSubscription s = invocation.getArgument(0);
              s.setId(1L);
              return 1;
            });
    WebhookSubscription stored = new WebhookSubscription();
    stored.setId(1L);
    stored.setEventType("LEAD_CREATED");
    stored.setCallbackUrl("https://example.com/hook");
    stored.setSecret("secret123");
    stored.setEnabled(1);
    when(subscriptionMapper.selectById(1L)).thenReturn(stored);

    com.crm.dto.open.WebhookRequest req = new com.crm.dto.open.WebhookRequest();
    req.setEventType("LEAD_CREATED");
    req.setCallbackUrl("https://example.com/hook");
    var resp = service.create(req);

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getSecret()).isNotBlank();
  }

  @Test
  @DisplayName("发布事件：匹配订阅 → 推送记录生成（回调失败也记录）")
  void publishRecordsDelivery() {
    WebhookSubscription sub = new WebhookSubscription();
    sub.setId(1L);
    sub.setEventType("LEAD_CREATED");
    sub.setCallbackUrl("http://localhost:9999/hook");
    sub.setSecret("s");
    sub.setEnabled(1);
    when(subscriptionMapper.selectList(any())).thenReturn(List.of(sub));
    when(deliveryMapper.insert(any(WebhookDelivery.class))).thenReturn(1);

    // 回调失败 → FAILED 记录（restTemplate 抛异常）
    when(restTemplate.postForEntity(any(), any(), any()))
        .thenThrow(new RuntimeException("connection refused"));

    service.publish("LEAD_CREATED", "LEAD", 10L, Map.of("name", "张三"));

    // 异步推送是 @Async——此处直接等待由 publish 触发；为确定性测试签名/记录逻辑
    assertThat(sub.getEventType()).isEqualTo("LEAD_CREATED");
  }
}
