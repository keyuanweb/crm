package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.entity.MailAccount;
import com.crm.entity.MailSyncRecord;
import com.crm.repository.MailSyncRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MailSyncRecordService 单元测试（062 T011）：模拟同步。 */
class MailSyncRecordServiceTest {

  private MailSyncRecordMapper recordMapper;
  private MailAccountService accountService;
  private MailSyncRecordService service;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, MailSyncRecord.class);
    TableInfoHelper.initTableInfo(assistant, MailAccount.class);
  }

  @BeforeEach
  void setUp() {
    recordMapper = mock(MailSyncRecordMapper.class);
    accountService = mock(MailAccountService.class);
    service = new MailSyncRecordService(recordMapper, accountService);
  }

  @Test
  @DisplayName("模拟同步：生成 INBOUND 记录")
  void simulateSyncCreatesRecord() {
    MailAccount account = new MailAccount();
    account.setId(1L);
    account.setEmail("sales@corp.com");
    when(accountService.require(1L)).thenReturn(account);
    when(recordMapper.insert(any(MailSyncRecord.class)))
        .thenAnswer(
            invocation -> {
              MailSyncRecord r = invocation.getArgument(0);
              r.setId(10L);
              return 1;
            });
    MailSyncRecord stored = new MailSyncRecord();
    stored.setId(10L);
    stored.setAccountId(1L);
    stored.setDirection("INBOUND");
    stored.setSubject("模拟同步邮件");
    stored.setFromAddress("customer@example.com");
    stored.setToAddress("sales@corp.com");
    stored.setSyncStatus("SYNCED");
    when(recordMapper.selectById(10L)).thenReturn(stored);

    var resp = service.simulateSync(1L);

    assertThat(resp.getId()).isEqualTo(10L);
    assertThat(resp.getDirection()).isEqualTo("INBOUND");
    assertThat(resp.getSyncStatus()).isEqualTo("SYNCED");
    assertThat(resp.getToAddress()).isEqualTo("sales@corp.com");
  }
}
