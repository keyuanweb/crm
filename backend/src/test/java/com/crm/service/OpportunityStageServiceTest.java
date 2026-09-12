package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.opportunity.OpportunityStageResponse;
import com.crm.entity.OpportunityStage;
import com.crm.repository.OpportunityStageMapper;
import com.crm.support.StageDictionaryTestSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

/** OpportunityStageService 单元测试（1.2-stage-configurable）：字典语义 / 校验 / 缓存失效。 */
class OpportunityStageServiceTest {

  private final OpportunityStageMapper mapper = mock(OpportunityStageMapper.class);
  private final AuditService auditService = mock(AuditService.class);

  private OpportunityStageService serviceOver(List<OpportunityStage> stages) {
    when(mapper.selectList(any())).thenReturn(stages);
    return new OpportunityStageService(mapper, mock(CacheManager.class), auditService);
  }

  private OpportunityStageService service() {
    return serviceOver(StageDictionaryTestSupport.rows());
  }

  // ------------------------------------------------------------------ 字典语义

  @Test
  @DisplayName("selectableCodes 排除已停用；activeCodes 不排除——两者不是一回事")
  void selectableVersusActive() {
    // PROPOSAL_QUOTED 已停用：不能再选入，但仍在里面（存量商机）故仍属「进行中」
    OpportunityStageService svc =
        serviceOver(
            List.of(
                StageDictionaryTestSupport.stage(
                    "INITIAL_CONTACT", "初步接触", 10, "ACTIVE", "0.2", true),
                StageDictionaryTestSupport.stage(
                    "PROPOSAL_QUOTED", "方案报价", 30, "ACTIVE", "0.4", false),
                StageDictionaryTestSupport.stage("CLOSED_WON", "已赢单", 90, "WON", "1.0", true)));

    assertThat(svc.selectableCodes()).containsExactly("INITIAL_CONTACT");
    // 若这里也把停用阶段排除掉，落在该阶段的存量商机会被 update/close 的守卫误判为「已关闭」而无法编辑
    assertThat(svc.activeCodes()).containsExactlyInAnyOrder("INITIAL_CONTACT", "PROPOSAL_QUOTED");
    assertThat(svc.allCodes())
        .containsExactlyInAnyOrder("INITIAL_CONTACT", "PROPOSAL_QUOTED", "CLOSED_WON");
  }

  @Test
  @DisplayName("orderedCodes 取全部阶段（含已停用与终态），并按查询给出的顺序投影")
  void orderedCodesKeepsEveryStageInQueryOrder() {
    // 断言的是「一个不落地按序取出编码」，不是「服务会把顺序排好」——排序由 loadOrdered() 的 SQL
    // ORDER BY sort_order 负责，而 mock 掉的 mapper 不会排序，在这里断言「服务排序」只会测到 mock 自己。
    // 顺序本身由集成测试把关（StatsIT 断言漏斗里 6 个阶段的确切序列）。
    OpportunityStageService svc =
        serviceOver(
            List.of(
                StageDictionaryTestSupport.stage(
                    "INITIAL_CONTACT", "初步接触", 10, "ACTIVE", "0.2", false),
                StageDictionaryTestSupport.stage("NEGOTIATING", "谈判中", 40, "ACTIVE", "0.5", true),
                StageDictionaryTestSupport.stage("CLOSED_LOST", "已输单", 100, "LOST", "0.0", true)));

    assertThat(svc.orderedCodes()).containsExactly("INITIAL_CONTACT", "NEGOTIATING", "CLOSED_LOST");
  }

  @Test
  @DisplayName("probabilityOf：进行中取字典赢率，终态固定 1/0，未知与 null 为 0")
  void probabilityOf() {
    OpportunityStageService svc = service();

    assertThat(svc.probabilityOf("NEEDS_CONFIRMED")).isEqualTo(0.3);
    assertThat(svc.probabilityOf("CLOSED_WON")).isEqualTo(1.0);
    assertThat(svc.probabilityOf("CLOSED_LOST")).isEqualTo(0.0);
    assertThat(svc.probabilityOf("NO_SUCH_STAGE")).isEqualTo(0.0);
    assertThat(svc.probabilityOf(null)).isEqualTo(0.0);
    assertThat(svc.probabilityOf("  NEGOTIATING  ")).isEqualTo(0.5);
  }

  @Test
  @DisplayName("isTerminal 只看 stage_type，不看 enabled")
  void isTerminal() {
    OpportunityStageService svc =
        serviceOver(
            List.of(
                StageDictionaryTestSupport.stage("NEGOTIATING", "谈判中", 40, "ACTIVE", "0.5", false),
                StageDictionaryTestSupport.stage("CLOSED_WON", "已赢单", 90, "WON", "1.0", true)));

    assertThat(svc.isTerminal("CLOSED_WON")).isTrue();
    assertThat(svc.isTerminal("NEGOTIATING")).isFalse();
    assertThat(svc.isTerminal(null)).isFalse();
  }

  // ------------------------------------------------------------------ 新建

  @Test
  @DisplayName("新建：强制进行中，stageType 请求 WON 会被拒（否则造出不计入配额的赢单阶段）")
  void createRejectsNonActiveStageType() {
    OpportunityStageService svc = service();

    assertThatThrownBy(() -> svc.create("SECOND_WON", "第二个赢单", 95, BigDecimal.ONE, "WON"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPPORTUNITY_STAGE_NOT_ACTIVE);
    verify(mapper, never()).insert(any(OpportunityStage.class));
  }

  @Test
  @DisplayName("新建：编码重复 / 概率越界 / 名称为空")
  void createValidatesInput() {
    OpportunityStageService svc = service();

    assertThatThrownBy(() -> svc.create("NEGOTIATING", "重复", 50, new BigDecimal("0.6"), null))
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPPORTUNITY_STAGE_DUPLICATE);

    assertThatThrownBy(() -> svc.create("BUDGET_APPROVAL", "预算审批", 50, new BigDecimal("1.5"), null))
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPPORTUNITY_STAGE_PROBABILITY_INVALID);

    assertThatThrownBy(() -> svc.create("BUDGET_APPROVAL", "  ", 50, new BigDecimal("0.6"), null))
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.STAGE_INVALID);

    verify(mapper, never()).insert(any(OpportunityStage.class));
  }

  @Test
  @DisplayName("新建成功：落库为 ACTIVE，返回体里 builtIn=false")
  void createSucceeds() {
    OpportunityStageService svc = service();
    when(mapper.insert(any(OpportunityStage.class)))
        .thenAnswer(
            inv -> {
              inv.getArgument(0, OpportunityStage.class).setId(7L);
              return 1;
            });

    OpportunityStageResponse res =
        svc.create("BUDGET_APPROVAL", "预算审批", 50, new BigDecimal("0.6"), null);

    assertThat(res.getId()).isEqualTo(7L);
    assertThat(res.getStageType()).isEqualTo("ACTIVE");
    assertThat(res.getCode()).isEqualTo("BUDGET_APPROVAL");
    assertThat(res.getBuiltIn()).isFalse();
    assertThat(res.getEnabled()).isEqualTo(1);
    verify(auditService).record("stage:create", "opportunity_stage", 7L, "BUDGET_APPROVAL");
  }

  // ------------------------------------------------------------------ 编辑 / 启停 / 删除

  @Test
  @DisplayName("编辑终态：赢率不接受配置，按 stage_type 回写 1 / 0")
  void updateTerminalIgnoresProbability() {
    OpportunityStageService svc = service();
    OpportunityStage won = StageDictionaryTestSupport.stage("CLOSED_WON", "已赢单", 90, "WON", "1.0");
    won.setId(5L);
    when(mapper.selectById(5L)).thenReturn(won);

    OpportunityStageResponse res = svc.update(5L, "成交", 90, new BigDecimal("0.3"));

    assertThat(res.getName()).isEqualTo("成交");
    assertThat(won.getProbability()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  @DisplayName("停用：走 updateById 落 enabled=0，并记审计")
  void setEnabled() {
    OpportunityStageService svc = service();
    OpportunityStage stage =
        StageDictionaryTestSupport.stage("NEGOTIATING", "谈判中", 40, "ACTIVE", "0.5");
    stage.setId(3L);
    when(mapper.selectById(3L)).thenReturn(stage);

    svc.setEnabled(3L, false);

    assertThat(stage.getEnabled()).isZero();
    verify(mapper).updateById(stage);
    verify(auditService).record("stage:disable", "opportunity_stage", 3L, "NEGOTIATING");
  }

  @Test
  @DisplayName("删除：内建终态不可删")
  void deleteRejectsBuiltIn() {
    OpportunityStageService svc = service();
    OpportunityStage won = StageDictionaryTestSupport.stage("CLOSED_WON", "已赢单", 90, "WON", "1.0");
    won.setId(5L);
    when(mapper.selectById(5L)).thenReturn(won);

    assertThatThrownBy(() -> svc.delete(5L))
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPPORTUNITY_STAGE_BUILT_IN);
    verify(mapper, never()).deleteById(any(Long.class));
  }

  @Test
  @DisplayName("删除：启用中不可删（看板列会当场消失而商机还在）")
  void deleteRejectsStillEnabled() {
    OpportunityStageService svc = service();
    OpportunityStage stage =
        StageDictionaryTestSupport.stage("NEGOTIATING", "谈判中", 40, "ACTIVE", "0.5", true);
    stage.setId(3L);
    when(mapper.selectById(3L)).thenReturn(stage);

    assertThatThrownBy(() -> svc.delete(3L))
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPPORTUNITY_STAGE_STILL_ENABLED);
  }

  @Test
  @DisplayName("删除已停用的自定义阶段：必须走 deleteById 而非 updateById")
  void deleteUsesLogicDelete() {
    OpportunityStageService svc = service();
    OpportunityStage stage =
        StageDictionaryTestSupport.stage("PROPOSAL_QUOTED", "方案报价", 30, "ACTIVE", "0.4", false);
    stage.setId(4L);
    when(mapper.selectById(4L)).thenReturn(stage);

    svc.delete(4L);

    // @TableLogic 字段不参与 updateById 的 SET 子句，手写 setDeleted(1)+updateById 会静默不生效
    verify(mapper).deleteById(4L);
    verify(mapper, never()).updateById(any(OpportunityStage.class));
    verify(auditService).record("stage:delete", "opportunity_stage", 4L, "PROPOSAL_QUOTED");
  }

  @Test
  @DisplayName("找不到的 id：统一 OPPORTUNITY_STAGE_NOT_FOUND")
  void requireByIdNotFound() {
    OpportunityStageService svc = service();
    when(mapper.selectById(9L)).thenReturn(null);

    assertThatThrownBy(() -> svc.setEnabled(9L, true))
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPPORTUNITY_STAGE_NOT_FOUND);
  }
}
