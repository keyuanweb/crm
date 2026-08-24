package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.entity.Notification;
import com.crm.repository.NotificationMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NotificationService 单元测试（016 T023）：列表/已读/未读计数/写入。 */
class NotificationServiceTest {

  private NotificationMapper notificationMapper;
  private com.crm.ws.NotificationWebSocketHandler webSocketHandler;
  private NotificationService service;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Notification.class);
  }

  @BeforeEach
  void setUp() {
    notificationMapper = mock(NotificationMapper.class);
    webSocketHandler = mock(com.crm.ws.NotificationWebSocketHandler.class);
    service = new NotificationService(notificationMapper, webSocketHandler);
  }

  @Test
  @DisplayName("未读计数：返回 count")
  void unreadCount() {
    when(notificationMapper.selectCount(any())).thenReturn(3L);

    long count = service.unreadCount(1L);

    assertThat(count).isEqualTo(3L);
  }

  @Test
  @DisplayName("写入通知：插入并清理旧记录")
  void notifyInserts() {
    when(notificationMapper.selectList(any())).thenReturn(List.of());
    when(notificationMapper.selectCount(any())).thenReturn(1L);

    service.notify(2L, "TICKET_ASSIGN", "工单已分配", "TICKET", 5L);

    verify(notificationMapper).insert(any(Notification.class));
    verify(notificationMapper).selectList(any());
  }

  @Test
  @DisplayName("写入通知后通过 WebSocket 实时推送（026）")
  void notifyPushesViaWebSocket() {
    when(notificationMapper.selectList(any())).thenReturn(List.of());
    when(notificationMapper.selectCount(any())).thenReturn(1L);

    service.notify(2L, "TICKET_ASSIGN", "工单已分配", "TICKET", 5L);

    verify(webSocketHandler)
        .notifyUser(
            org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.argThat(
                p -> p.getType().equals("TICKET_ASSIGN") && p.getUnreadCount() == 1L));
  }

  @Test
  @DisplayName("全部已读：更新未读为已读")
  void markAllRead() {
    when(notificationMapper.update(any(), any())).thenReturn(4);

    long updated = service.markAllRead(1L);

    assertThat(updated).isEqualTo(4L);
  }

  @Test
  @DisplayName("userId 为 null 时跳过写入")
  void notifyNullUserSkips() {
    service.notify(null, "WORKFLOW", "msg", null, null);
    verify(notificationMapper, org.mockito.Mockito.never()).insert(any(Notification.class));
  }
}
