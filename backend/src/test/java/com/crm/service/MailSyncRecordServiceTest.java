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
import com.crm.common.MailInboundNotConfiguredException;
import com.crm.config.MailInboundStatus;
import com.crm.entity.MailAccount;
import com.crm.entity.MailSyncRecord;
import com.crm.repository.MailSyncRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MailSyncRecordService 单元测试（062 T011 → 101 T1 重写）：收信同步的两条路。
 *
 * <p>062 的原用例断言的是"生成 INBOUND 记录"，因为当时的实现就是无条件生成一条 {@code SYNCED} 记录。101 把默认路改成拒绝之后，
 * 那条断言的对象已经不存在了——本类现在分三组：① 默认路<b>零插入</b>（本批的核心主张）、② 账户不存在时 404 <b>先于</b>409、③ 演示路生成 {@code
 * SIMULATED} 记录。
 */
class MailSyncRecordServiceTest {

  private MailSyncRecordMapper recordMapper;
  private MailAccountService accountService;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, MailSyncRecord.class);
    TableInfoHelper.initTableInfo(assistant, MailAccount.class);
  }

  /** 按给定开关造一个 service；演示开关的取值就是本类唯一的变量。 */
  private MailSyncRecordService serviceWithDemo(boolean demoEnabled) {
    recordMapper = mock(MailSyncRecordMapper.class);
    accountService = mock(MailAccountService.class);
    return new MailSyncRecordService(
        recordMapper, accountService, new MailInboundStatus(demoEnabled));
  }

  private static MailAccount account(long id, String email) {
    MailAccount account = new MailAccount();
    account.setId(id);
    account.setEmail(email);
    return account;
  }

  /**
   * 默认路：抛 {@link MailInboundNotConfiguredException}，且**一条记录都没写**。
   *
   * <p>⚠️ {@code never().insert} 是本条的主角，不是陪衬。只断言"抛了异常"是不够的：方法上有
   * {@code @Transactional}，看起来"反正事务会回滚"， 于是一个<b>先 insert 再抛</b>的实现也能让"抛了异常"这一半成立——而那正是 062
   * 的行为换了个壳。副作用必须被正面断言（这条破坏在 falsification-evidence.md 的 D3 里跑过）。
   */
  @Test
  @DisplayName("默认（未开演示开关）：拒绝且零插入")
  void defaultPathRejectsWithoutWritingAnything() {
    MailSyncRecordService service = serviceWithDemo(false);
    when(accountService.require(1L)).thenReturn(account(1L, "sales@corp.com"));

    assertThatThrownBy(() -> service.triggerSync(1L))
        .isInstanceOf(MailInboundNotConfiguredException.class)
        .hasMessageContaining("未接入收信源");

    verify(recordMapper, never()).insert(any(MailSyncRecord.class));
  }

  /**
   * 账户不存在 ⇒ 仍是 404，而不是被 409 盖掉。
   *
   * <p>两个错误码在同一个方法里挨着，顺序是契约的一部分：062 的 {@code MAIL_ACCOUNT_NOT_FOUND} 语义一字未改， 若把判门提到 {@code require}
   * 之前，调一个不存在的账户会从 404 变成 409——前端按 404 做的"账户已被删除"分支就再也走不到了。
   */
  @Test
  @DisplayName("账户不存在：404 先于 409（存在性判断没被配置门挪动）")
  void missingAccountStaysNotFound() {
    MailSyncRecordService service = serviceWithDemo(false);
    when(accountService.require(99L))
        .thenThrow(new BusinessException(ErrorCode.MAIL_ACCOUNT_NOT_FOUND));

    assertThatThrownBy(() -> service.triggerSync(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.MAIL_ACCOUNT_NOT_FOUND);

    verify(recordMapper, never()).insert(any(MailSyncRecord.class));
  }

  /**
   * 演示路：写一条 {@code SIMULATED} 记录，且主题与外部 id 都带演示标记。
   *
   * <p>只把状态常量从 {@code SYNCED} 换成 {@code SIMULATED} 而主题照旧写"模拟同步邮件"是不够的——外部 id 与主题是这条记录在**数据层**
   * 的身份，导出报表或直接查库的人看不到界面上的橙色标签。两处标记都要断言（falsification-evidence.md 的 D2/D4）。
   */
  @Test
  @DisplayName("演示（显式打开开关）：生成带演示标记的 SIMULATED 记录")
  void demoPathCreatesSimulatedRecord() {
    MailSyncRecordService service = serviceWithDemo(true);
    when(accountService.require(1L)).thenReturn(account(1L, "sales@corp.com"));
    when(recordMapper.insert(any(MailSyncRecord.class)))
        .thenAnswer(
            invocation -> {
              MailSyncRecord r = invocation.getArgument(0);
              r.setId(10L);
              when(recordMapper.selectById(10L)).thenReturn(r);
              return 1;
            });

    var resp = service.triggerSync(1L);

    assertThat(resp.getId()).isEqualTo(10L);
    assertThat(resp.getDirection()).isEqualTo("INBOUND");
    assertThat(resp.getSyncStatus()).isEqualTo(MailSyncRecord.STATUS_SIMULATED);
    assertThat(resp.getSyncStatus()).isNotEqualTo(MailSyncRecord.STATUS_SYNCED);
    assertThat(resp.getSubject()).contains("演示").contains("非真实收信");
    assertThat(resp.getExternalId()).startsWith("demo-");
    assertThat(resp.getToAddress()).isEqualTo("sales@corp.com");
  }
}
