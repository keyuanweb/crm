package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.OutboundUrlValidator;
import com.crm.entity.WebhookDelivery;
import com.crm.entity.WebhookSubscription;
import com.crm.repository.WebhookDeliveryMapper;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * WebhookDeliverer 单元测试（085-verify-green，FR-V05 / V06 / V07 / V08）。
 *
 * <p><b>为什么用直接 new 而不是 Spring 容器</b>：绕过 {@code @Async} 代理使投递同步执行，测试因此是确定的。
 *
 * <p><b>为什么测试是快的</b>：两条投递用例走的都是<b>不进入退避等待</b>的路径 —— ①成功路径（第 0 次尝试即 2xx， 无
 * sleep）；②地址被拒路径（在重试循环<b>之前</b>就返回）。第三条用例（重试耗尽）用中断线程的方式让 第一次 sleep 立即抛出而不真等 —— 否则单条用例要跑满 36 秒。
 */
class WebhookDelivererTest {

  private WebhookDeliveryMapper deliveryMapper;
  private RestTemplate restTemplate;
  private OutboundUrlValidator outboundUrlValidator;
  private WebhookDeliverer deliverer;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, WebhookDelivery.class);
    TableInfoHelper.initTableInfo(assistant, WebhookSubscription.class);
  }

  @BeforeEach
  void setUp() {
    deliveryMapper = mock(WebhookDeliveryMapper.class);
    restTemplate = mock(RestTemplate.class);
    outboundUrlValidator = mock(OutboundUrlValidator.class);
    deliverer = new WebhookDeliverer(deliveryMapper, restTemplate, outboundUrlValidator);
  }

  private WebhookSubscription sub() {
    WebhookSubscription s = new WebhookSubscription();
    s.setId(7L);
    s.setCallbackUrl("http://localhost:9999/hook");
    s.setSecret("s3cret");
    return s;
  }

  /**
   * 「插入那一刻」的快照。
   *
   * <p><b>为什么需要它、而不能用 ArgumentCaptor 事后读取</b>：{@link WebhookDeliverer} 把派发时插入的
   * <b>同一个实例</b>在结束时原地改成终态，于是 captor 拿到的引用与终态引用是同一对象 —— 事后读到的必然是终态， "派发时是 PENDING" 这个断言无从表达。这一点本身是
   * FR-V08（一次投递只有一条记录、终态**原地更新**）的 直接证据，但也意味着初始态只能在插入那一刻取证。
   */
  private static final class InsertSnapshot {
    WebhookDelivery entity;
    String statusAtInsert;
    Integer retryCountAtInsert;
  }

  /** 让 mock 的 insert 在被调用那一刻记录快照并返回影响行数 1。 */
  private InsertSnapshot snapshotInsert() {
    InsertSnapshot snap = new InsertSnapshot();
    doAnswer(
            inv -> {
              snap.entity = inv.getArgument(0);
              snap.statusAtInsert = snap.entity.getStatus();
              snap.retryCountAtInsert = snap.entity.getRetryCount();
              return 1;
            })
        .when(deliveryMapper)
        .insert(any(WebhookDelivery.class));
    return snap;
  }

  @Test
  @DisplayName("派发时先落 PENDING，结束时把**同一条**记录原地更新为 SUCCESS（FR-V05/V06/V08）")
  void pendingAtDispatchThenSameRowBecomesSuccess() {
    when(restTemplate.postForEntity(any(java.net.URI.class), any(), any()))
        .thenReturn(ResponseEntity.ok("{}"));
    InsertSnapshot snap = snapshotInsert();

    deliverer.deliverAsync(sub(), "TICKET_ASSIGNED", "TICKET", 42L, "{\"a\":1}");

    // FR-V05：派发即插入，且**插入那一刻**的状态是 PENDING（不是终态、更不是空）
    assertThat(snap.statusAtInsert).isEqualTo("PENDING");
    assertThat(snap.entity.getEventType()).isEqualTo("TICKET_ASSIGNED");
    assertThat(snap.retryCountAtInsert).isZero();
    assertThat(snap.entity.getCreatedAt()).isNotNull();

    // FR-V08：一次投递**只**插一条 —— 终态是原地更新，不是再插一行
    verify(deliveryMapper, times(1)).insert(any(WebhookDelivery.class));

    ArgumentCaptor<WebhookDelivery> updated = ArgumentCaptor.forClass(WebhookDelivery.class);
    verify(deliveryMapper, times(1)).updateById(updated.capture());
    // 同一个实例 → 证明更新的是派发时那一行，而不是新造的一条
    assertThat(updated.getValue()).isSameAs(snap.entity);
    // FR-V06：终态与初始状态**可区分**，且带 HTTP 状态与重试次数
    assertThat(updated.getValue().getStatus()).isEqualTo("SUCCESS");
    assertThat(updated.getValue().getHttpStatus()).isEqualTo(200);
    assertThat(updated.getValue().getRetryCount()).isZero();
  }

  @Test
  @DisplayName("地址被拒时仍产生**恰好一条**记录，且直接是 FAILED（FR-V05/V08）")
  void rejectedUrlStillProducesExactlyOneRecord() {
    doThrow(new BusinessException(ErrorCode.OPEN_WEBHOOK_URL_INVALID))
        .when(outboundUrlValidator)
        .validate(any(), any());
    InsertSnapshot snap = snapshotInsert();

    deliverer.deliverAsync(sub(), "TICKET_ASSIGNED", "TICKET", 42L, "{\"a\":1}");

    // 先落 PENDING，再由 finish 原地改为 FAILED —— 两个状态**都**出现过
    assertThat(snap.statusAtInsert).isEqualTo("PENDING");

    verify(deliveryMapper, times(1)).insert(any(WebhookDelivery.class));
    ArgumentCaptor<WebhookDelivery> updated = ArgumentCaptor.forClass(WebhookDelivery.class);
    verify(deliveryMapper, times(1)).updateById(updated.capture());
    assertThat(updated.getValue()).isSameAs(snap.entity);
    assertThat(updated.getValue().getStatus()).isEqualTo("FAILED");
    // 地址能否出站不随重试改变，故这条路径**不**进重试循环
    verify(restTemplate, never()).postForEntity(any(java.net.URI.class), any(), any());
  }

  @Test
  @DisplayName("重试耗尽后记为 FAILED，且重试次数反映实际发生过的重试（FR-V06）")
  void exhaustedRetriesMarkFailedWithRetryCount() {
    when(restTemplate.postForEntity(any(java.net.URI.class), any(), any()))
        .thenThrow(new RuntimeException("connection refused"));

    // 预置中断标志：第一次 Thread.sleep(RETRY_DELAYS_MS[0]) 立即抛 InterruptedException → break，
    // 从而在不真等 36 秒的前提下走到"重试耗尽/中断"的收尾路径。
    Thread.currentThread().interrupt();
    try {
      deliverer.deliverAsync(sub(), "TICKET_ASSIGNED", "TICKET", 42L, "{\"a\":1}");
    } finally {
      Thread.interrupted(); // 清理中断标志，避免污染后续用例
    }

    verify(deliveryMapper, times(1)).insert(any(WebhookDelivery.class));
    ArgumentCaptor<WebhookDelivery> updated = ArgumentCaptor.forClass(WebhookDelivery.class);
    verify(deliveryMapper, times(1)).updateById(updated.capture());
    // 终态必须是"失败"，且带上错误摘要 —— 这正是改造前**根本不会出现**的那条记录
    assertThat(updated.getValue().getStatus()).isEqualTo("FAILED");
    assertThat(updated.getValue().getError()).contains("connection refused");
    assertThat(updated.getValue().getRetryCount()).isPositive();
  }

  @Test
  @DisplayName("清扫只判定**在途且已超时**的记录为失败（FR-V07）")
  void sweepOnlyTargetsStalePendingRows() {
    when(deliveryMapper.update(any(), any())).thenReturn(3);

    int n = deliverer.sweepStalePending();

    assertThat(n).isEqualTo(3);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Wrapper<WebhookDelivery>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
    ArgumentCaptor<WebhookDelivery> patchCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
    verify(deliveryMapper, times(1)).update(patchCaptor.capture(), wrapperCaptor.capture());

    // 补丁实体：只把状态改成 FAILED，并写明原因（终态必须可判定，不得停在"在途"）
    assertThat(patchCaptor.getValue().getStatus()).isEqualTo("FAILED");
    assertThat(patchCaptor.getValue().getError()).contains("中断");

    // 过滤条件必须同时含两个判据：状态 = PENDING **且** created_at 早于阈值。
    // 只按状态清空会误伤真实在途投递；只按时间清空会误伤刚派发的记录。
    Wrapper<WebhookDelivery> wrapper = wrapperCaptor.getValue();
    assertThat(wrapper.getSqlSegment()).contains("status").contains("created_at");
    // getParamNameValuePairs() 声明在 AbstractWrapper（Wrapper 顶层不暴露），故下溯一层。
    Map<String, Object> params = ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
    assertThat(params.values()).contains("PENDING");
  }
}
