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
import com.crm.entity.EmailUnsubscribe;
import com.crm.repository.EmailSendLogMapper;
import com.crm.repository.EmailUnsubscribeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** EmailUnsubscribeService 单元测试（052 T011）：退订/名单/恢复/排除。 */
class EmailUnsubscribeServiceTest {

  private EmailUnsubscribeMapper unsubscribeMapper;
  private EmailSendLogMapper sendLogMapper;
  private EmailUnsubscribeService service;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, EmailUnsubscribe.class);
  }

  @BeforeEach
  void setUp() {
    unsubscribeMapper = mock(EmailUnsubscribeMapper.class);
    sendLogMapper = mock(EmailSendLogMapper.class);
    service = new EmailUnsubscribeService(unsubscribeMapper, sendLogMapper);
  }

  @Test
  @DisplayName("退订：新邮箱插入记录")
  void unsubscribeNew() {
    when(unsubscribeMapper.selectOne(any())).thenReturn(null);
    when(unsubscribeMapper.insert(any(EmailUnsubscribe.class)))
        .thenAnswer(
            invocation -> {
              EmailUnsubscribe u = invocation.getArgument(0);
              u.setId(1L);
              return 1;
            });

    var record = service.unsubscribe("a@b.com", null);

    assertThat(record.getId()).isEqualTo(1L);
    assertThat(record.getEmail()).isEqualTo("a@b.com");
  }

  @Test
  @DisplayName("退订：已退订幂等（不重复插入）")
  void unsubscribeIdempotent() {
    EmailUnsubscribe existing = new EmailUnsubscribe();
    existing.setId(1L);
    existing.setEmail("a@b.com");
    when(unsubscribeMapper.selectOne(any())).thenReturn(existing);

    var record = service.unsubscribe("a@b.com", null);

    assertThat(record.getId()).isEqualTo(1L);
    verify(unsubscribeMapper, never()).insert(any());
  }

  @Test
  @DisplayName("退订：空邮箱 → 422")
  void unsubscribeEmptyThrows() {
    assertThatThrownBy(() -> service.unsubscribe("  ", null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_UNSUBSCRIBE_EMAIL_REQUIRED);
  }

  @Test
  @DisplayName("排除检查：已退订邮箱返回 true")
  void isUnsubscribed() {
    when(unsubscribeMapper.selectCount(any())).thenReturn(1L);
    assertThat(service.isUnsubscribed("a@b.com")).isTrue();

    when(unsubscribeMapper.selectCount(any())).thenReturn(0L);
    assertThat(service.isUnsubscribed("c@d.com")).isFalse();
  }

  @Test
  @DisplayName("批量过滤：返回未退订客户 id")
  void filterUnsubscribed() {
    java.util.Map<Long, String> emails = new java.util.LinkedHashMap<>();
    emails.put(1L, "a@b.com");
    emails.put(2L, "c@d.com");
    // 全部未退订 → 全部保留
    when(unsubscribeMapper.selectCount(any())).thenReturn(0L);
    var kept = service.filterUnsubscribed(emails);
    assertThat(kept).containsExactlyInAnyOrder(1L, 2L);

    // a@b.com 已退订（selectCount 按调用顺序返回）→ 排除 id=1
    org.mockito.Mockito.reset(unsubscribeMapper);
    when(unsubscribeMapper.selectCount(any())).thenReturn(1L, 0L);
    var kept2 = service.filterUnsubscribed(emails);
    assertThat(kept2).containsExactly(2L);
  }

  @Test
  @DisplayName("恢复：删除退订记录")
  void restore() {
    service.restore(1L);
    verify(unsubscribeMapper).deleteById(1L);
  }
}
