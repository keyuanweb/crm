package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.entity.WebhookDelivery;
import com.crm.repository.WebhookDeliveryMapper;
import com.crm.service.WebhookDeliverer;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * 集成中心集成测试（058 T014）：通道 CRUD/事件推送/非法 URL。
 *
 * <p><b>为什么本类要显式声明出站白名单</b>：083（FR-G13）把出站地址改为<b>默认全拒</b>——白名单留空时，回环地址与公网地址
 * <b>同样</b>不被放行（data-model.md §4）。本类两处出站地址在默认配置下都会被拒：本地的假端点 {@code
 * http://localhost:9999/hook}（推送记录链路用）与真实厂商端点 {@code https://qyapi.weixin.qq.com/x}
 * （"合法地址应当被接受"的对照项）。后者尤其值得注意：<b>拒绝理由是"不在白名单内"而非"是公网地址"</b>——白名单是全有或全无的，
 * 部署方必须显式列出每个允许的出站目标。故此处把两个主机一并列出，声明本用例的环境前提。
 *
 * <p>这不是放宽校验：出站被拒的路径由 {@code SecurityHardeningIT} 逐类断言（回环／私网／链路本地／云元数据）， 本类只负责通道 CRUD 与推送记录。
 */
@TestPropertySource(properties = "crm.outbound.allowed-hosts=localhost,qyapi.weixin.qq.com")
class IntegrationHubIT extends AbstractIntegrationTest {

  @Autowired private WebhookDeliveryMapper deliveryMapper;
  @Autowired private WebhookDeliverer webhookDeliverer;

  @Test
  @DisplayName("集成中心流程：配置通道→触发事件→推送记录")
  void integrationFlow() throws Exception {
    String token = loginAndGetToken();

    // 配置通道（URL 不可达 → 推送记录 FAILED，验证记录链路）
    String resp =
        mockMvc
            .perform(
                post("/api/v1/integration-channels")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"channelType\": \"CUSTOM\", \"name\": \"测试通道\", \"webhookUrl\": \"http://localhost:9999/hook\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long channelId = objectMapper.readTree(resp).path("data").path("id").asLong();

    // 触发工单分配事件（创建客户+工单+分配）
    String custResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"集成客户%d\", \"company\": \"集成公司\"}", System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerId = objectMapper.readTree(custResp).path("data").path("id").asLong();
    String ticketResp =
        mockMvc
            .perform(
                post("/api/v1/tickets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"title\": \"集成工单\", \"priority\": \"MEDIUM\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ticketId = objectMapper.readTree(ticketResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/assign", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\": 1}"))
        .andExpect(status().isOk());

    // 推送记录（异步，稍等后查询）
    Thread.sleep(1500);
    mockMvc
        .perform(
            get("/api/v1/integration-channels/{id}/deliveries", channelId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].eventType").value("TICKET_ASSIGNED"))
        // 085（FR-V05/V13）：派发后 1.5 秒内记录**已存在**，且状态是明确的"在途"，不是空白、更不是"成功"。
        // 改造前这条记录要等重试循环跑完（退避 1s+5s+30s）才落库，此刻查无此记录（total = 0）——本用例
        // 改前失败、改后通过，正是"用例对了、实现错了"的证据。
        // 时序是确定的：第 0 次尝试立即失败 → 睡 1s → 第 1 次尝试失败 → 睡 5s，故 t=1.5s 仍在循环内，必为 PENDING。
        .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
        // FR-V08：一次投递**只**对应一条记录（终态是原地更新，不是再插一行）
        .andExpect(jsonPath("$.data.items.length()").value(1));
  }

  @Test
  @DisplayName("非法 URL → 422；停用通道列表")
  void invalidUrlAndToggle() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            post("/api/v1/integration-channels")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"channelType\": \"DINGTALK\", \"name\": \"钉钉群\", \"webhookUrl\": \"ftp://bad\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("INTEGRATION_URL_INVALID"));

    String resp =
        mockMvc
            .perform(
                post("/api/v1/integration-channels")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"channelType\": \"WECHAT_WORK\", \"name\": \"企微群\", \"webhookUrl\": \"https://qyapi.weixin.qq.com/x\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long channelId = objectMapper.readTree(resp).path("data").path("id").asLong();

    // 启停
    mockMvc
        .perform(
            post("/api/v1/integration-channels/{id}/toggle", channelId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));
  }

  @Test
  @DisplayName("FR-V07：**中断残留**的在途记录被清扫判定为终态，且刚派发的记录不被误伤（真实库行级验证）")
  void stalePendingDeliveriesAreSweptToFailed() {
    // ① 残留：模拟"进程在投递完成前终止"留下的记录 —— 早于 2 分钟阈值（远超 36 秒最长重试窗口）
    WebhookDelivery orphan = pendingRecord(LocalDateTime.now().minusMinutes(10));
    deliveryMapper.insert(orphan);
    assertThat(orphan.getId()).isNotNull(); // id-type: auto → 插入后带回主键

    // ② 对照：刚派发的在途记录。它**不得**被误伤 —— 只按状态清空会伤到它
    WebhookDelivery inflight = pendingRecord(LocalDateTime.now());
    deliveryMapper.insert(inflight);

    int swept = webhookDeliverer.sweepStalePending();
    assertThat(swept).isGreaterThanOrEqualTo(1);

    // 残留被判定为**终态**，并带明确原因（不得停在"投递中"）
    WebhookDelivery afterOrphan = deliveryMapper.selectById(orphan.getId());
    assertThat(afterOrphan.getStatus()).isEqualTo("FAILED");
    assertThat(afterOrphan.getError()).contains("中断");

    // 反向断言：真实在途的**没有**被误判 —— 这一条把"阈值确实在起作用"与"只是把 PENDING 全清了"区分开
    WebhookDelivery afterInflight = deliveryMapper.selectById(inflight.getId());
    assertThat(afterInflight.getStatus()).isEqualTo("PENDING");
  }

  private static WebhookDelivery pendingRecord(LocalDateTime createdAt) {
    WebhookDelivery d = new WebhookDelivery();
    d.setSubscriptionId(9999L);
    d.setEventType("TICKET_ASSIGNED");
    d.setEntityType("TICKET");
    d.setEntityId(1L);
    d.setPayload("{}");
    d.setStatus("PENDING");
    d.setRetryCount(0);
    d.setCreatedAt(createdAt);
    return d;
  }
}
