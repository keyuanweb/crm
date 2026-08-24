package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crm.entity.FollowUp;
import com.crm.entity.Lead;
import com.crm.entity.LeadScoreConfig;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.LeadScoreConfigMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LeadScoreService 单元测试（019 T003）：评分维度/边界/颜色。 */
class LeadScoreServiceTest {

  private LeadScoreConfigMapper configMapper;
  private FollowUpMapper followUpMapper;
  private LeadScoreService service;

  @BeforeEach
  void setUp() {
    configMapper = mock(LeadScoreConfigMapper.class);
    followUpMapper = mock(FollowUpMapper.class);
    service = new LeadScoreService(configMapper, followUpMapper);
  }

  private LeadScoreConfig config(String key, String params) {
    LeadScoreConfig c = new LeadScoreConfig();
    c.setRuleKey(key);
    c.setParamsJson(params);
    c.setEnabled(1);
    return c;
  }

  private List<LeadScoreConfig> defaultConfigs() {
    return List.of(
        config(
            "SOURCE",
            "{\"REFERRAL\":30,\"WEBSITE\":25,\"EXHIBITION\":20,\"AD\":15,\"COLD_CALL\":10,\"OTHER\":10}"),
        config("INFO", "{\"fields\":[\"company\",\"title\",\"phone\",\"email\"],\"each\":7.5}"),
        config("FOLLOWUP", "{\"activeDays\":7,\"maxScore\":25}"),
        config("FRESHNESS", "{\"activeDays\":7,\"zeroDays\":30,\"maxScore\":15}"));
  }

  private Lead lead(String source, String company, String title, String phone, String email) {
    Lead l = new Lead();
    l.setSource(source);
    l.setCompany(company);
    l.setTitle(title);
    l.setPhone(phone);
    l.setEmail(email);
    l.setCreatedAt(LocalDateTime.now());
    return l;
  }

  @Test
  @DisplayName("高分线索：REFERRAL + 信息全 + 近期跟进 → ≥70")
  void highQualityLeadScoresHigh() {
    when(configMapper.selectList(any())).thenReturn(defaultConfigs());
    when(followUpMapper.selectList(any()))
        .thenReturn(List.of(followUp(LocalDateTime.now().minusDays(1))));

    int score = service.computeScore(lead("REFERRAL", "Acme", "CTO", "138", "a@b.com"));

    assertThat(score).isGreaterThanOrEqualTo(70);
  }

  @Test
  @DisplayName("低分线索：COLD_CALL + 信息少 + 无跟进 → <40")
  void lowQualityLeadScoresLow() {
    when(configMapper.selectList(any())).thenReturn(defaultConfigs());
    when(followUpMapper.selectList(any())).thenReturn(List.of());

    int score = service.computeScore(lead("COLD_CALL", null, null, "138", null));

    assertThat(score).isLessThan(40);
  }

  @Test
  @DisplayName("无任何信息：给基础分（≥0 且 <40）")
  void noInfoLeadGivesBaseScore() {
    when(configMapper.selectList(any())).thenReturn(defaultConfigs());
    when(followUpMapper.selectList(any())).thenReturn(List.of());

    int score = service.computeScore(lead("OTHER", null, null, null, null));

    assertThat(score).isBetween(0, 40);
  }

  @Test
  @DisplayName("跟进活跃度加分：有近期跟进高于无跟进")
  void followUpBoostsScore() {
    when(configMapper.selectList(any())).thenReturn(defaultConfigs());
    when(followUpMapper.selectList(any()))
        .thenReturn(List.of(followUp(LocalDateTime.now().minusDays(2))))
        .thenReturn(List.of());

    Lead l = lead("WEBSITE", "Acme", "CTO", "138", "a@b.com");
    int withFollowUp = service.computeScore(l);
    int withoutFollowUp = service.computeScore(l);

    assertThat(withFollowUp).isGreaterThan(withoutFollowUp);
  }

  @Test
  @DisplayName("分数封顶 100，不越界")
  void scoreCappedAt100() {
    when(configMapper.selectList(any())).thenReturn(defaultConfigs());
    when(followUpMapper.selectList(any()))
        .thenReturn(List.of(followUp(LocalDateTime.now().minusDays(1))));

    int score = service.computeScore(lead("REFERRAL", "Acme", "CTO", "138", "a@b.com"));

    assertThat(score).isLessThanOrEqualTo(100);
  }

  private FollowUp followUp(LocalDateTime createdAt) {
    FollowUp f = new FollowUp();
    f.setCreatedAt(createdAt);
    return f;
  }
}
