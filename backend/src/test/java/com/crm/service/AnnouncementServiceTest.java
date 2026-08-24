package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.dto.announcement.AnnouncementRequest;
import com.crm.dto.announcement.AnnouncementResponse;
import com.crm.entity.Announcement;
import com.crm.entity.AnnouncementRead;
import com.crm.repository.AnnouncementMapper;
import com.crm.repository.AnnouncementReadMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** AnnouncementService 单元测试（037 T004）。 */
class AnnouncementServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Announcement.class);
    TableInfoHelper.initTableInfo(assistant, AnnouncementRead.class);
  }

  private AnnouncementMapper announcementMapper;
  private AnnouncementReadMapper readMapper;
  private AuditService auditService;
  private AnnouncementService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    announcementMapper = mock(AnnouncementMapper.class);
    readMapper = mock(AnnouncementReadMapper.class);
    auditService = mock(AuditService.class);
    service = new AnnouncementService(announcementMapper, readMapper, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  @Test
  @DisplayName("发布公告成功")
  void createSuccess() {
    AnnouncementRequest req = new AnnouncementRequest();
    req.setTitle("季度目标");
    req.setContent("<p>冲刺</p>");
    req.setPinned(true);

    AnnouncementResponse resp = service.create(req);

    assertThat(resp.getTitle()).isEqualTo("季度目标");
    assertThat(resp.getPinned()).isTrue();
    verify(announcementMapper).insert(any(Announcement.class));
  }

  @Test
  @DisplayName("标记已读幂等：首次插入，重复跳过")
  void markReadIdempotent() {
    when(announcementMapper.selectById(1L)).thenReturn(new Announcement());
    when(readMapper.selectCount(any())).thenReturn(0L);

    service.markRead(1L);

    verify(readMapper).insert(any(AnnouncementRead.class));
  }

  @Test
  @DisplayName("未读计数：排除已读")
  void unreadCountExcludesRead() {
    Announcement a1 = new Announcement();
    a1.setId(1L);
    Announcement a2 = new Announcement();
    a2.setId(2L);
    when(announcementMapper.selectList(any())).thenReturn(java.util.List.of(a1, a2));
    AnnouncementRead read = new AnnouncementRead();
    read.setAnnouncementId(1L);
    when(readMapper.selectList(any())).thenReturn(java.util.List.of(read));

    long unread = service.unreadCount();

    assertThat(unread).isEqualTo(1);
  }
}
