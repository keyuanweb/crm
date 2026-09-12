package com.crm.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.entity.OpportunityStage;
import com.crm.repository.OpportunityStageMapper;
import com.crm.service.AuditService;
import com.crm.service.OpportunityStageService;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.cache.CacheManager;

/**
 * 阶段字典的测试支撑（1.2-stage-configurable）。
 *
 * <p><b>为什么是「真实 Service + 内存种子」而不是 mock 掉 {@link OpportunityStageService}</b>：
 * 阶段语义（哪些是活跃、哪些是终态、赢率多少、顺序如何）正是 1.2 改造的核心。若把 Service mock 掉，
 * 每个测试都要自己复述一遍这层语义，等于把「实现」抄进「断言」——实现错了、抄错的那份也跟着错， 测试依然全绿。这里让真实 Service 跑在内存种子上，被测代码取到的语义与生产一致。
 *
 * <p>种子与 {@code V79__opportunity_stage.sql} 保持一致（编码 / 赢率 / 顺序）。两边不一致时，
 * 单元测试会以「与生产不同的字典」通过——故本文件顶部刻意写明这条约束。
 */
public final class StageDictionaryTestSupport {

  /**
   * 注册 {@link OpportunityStage} 的 TableInfo。
   *
   * <p>{@code OpportunityStageService.loadOrdered()} 建 wrapper 时会调 {@code
   * orderByAsc(OpportunityStage::getSortOrder)}，而 MyBatis-Plus 在<b>那一刻</b>就要把方法引用解析成列名， 解析依赖
   * TableInfo。纯 Mockito 测试没有 Spring 上下文没有它，会抛 {@code can not find lambda cache for this
   * entity}——症状是「阶段字典坏了」，其实只是没注册。放在支撑类的静态块里，省得每个用到它的测试各写一遍 {@code @BeforeAll}（{@code
   * TableInfoHelper} 对同一实体重复注册是幂等的）。
   */
  static {
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
    TableInfoHelper.initTableInfo(assistant, OpportunityStage.class);
  }

  private StageDictionaryTestSupport() {}

  /**
   * 与 V79 种子一致的 6 个阶段。
   *
   * <p>顺序即 {@code sort_order}：4 个进行中阶段夹在两端终态之间。
   */
  public static List<OpportunityStage> rows() {
    return List.of(
        stage("INITIAL_CONTACT", "初步接触", 10, "ACTIVE", "0.2"),
        stage("NEEDS_CONFIRMED", "需求确认", 20, "ACTIVE", "0.3"),
        stage("PROPOSAL_QUOTED", "方案报价", 30, "ACTIVE", "0.4"),
        stage("NEGOTIATING", "谈判中", 40, "ACTIVE", "0.5"),
        stage("CLOSED_WON", "已赢单", 90, "WON", "1.0"),
        stage("CLOSED_LOST", "已输单", 100, "LOST", "0.0"));
  }

  /**
   * 真实阶段服务，跑在 {@link #rows()} 上；缓存留空（取不到 Cache 时服务直接查库）。
   *
   * <p>打桩刻意用 {@code lenient()}：调用方未必每条用例都会走到阶段校验（例如「更新已关闭的商机」在建单阶段就 抛异常了），而在 {@code
   * MockitoExtension} 的 STRICT_STUBS 下，未使用的打桩会以 {@code UnnecessaryStubbingException}
   * 让用例失败——那与被测行为无关，纯粹是这个共享支撑类替调用方背了锅。
   */
  public static OpportunityStageService service() {
    return service(rows());
  }

  /** 用自定义字典行装配服务——测「停用/停用后仍可移出」这类语义时需要用非全启用的种子。 */
  public static OpportunityStageService service(List<OpportunityStage> stages) {
    OpportunityStageMapper mapper = mock(OpportunityStageMapper.class);
    lenient().when(mapper.selectList(any())).thenReturn(stages);
    return new OpportunityStageService(mapper, mock(CacheManager.class), mock(AuditService.class));
  }

  public static OpportunityStage stage(
      String code, String name, int sortOrder, String stageType, String probability) {
    return stage(code, name, sortOrder, stageType, probability, true);
  }

  /**
   * @param enabled false 表示已停用（停用只禁止新进入，存量商机仍在其中）
   */
  public static OpportunityStage stage(
      String code,
      String name,
      int sortOrder,
      String stageType,
      String probability,
      boolean enabled) {
    OpportunityStage stage = new OpportunityStage();
    stage.setCode(code);
    stage.setName(name);
    stage.setSortOrder(sortOrder);
    stage.setStageType(stageType);
    stage.setProbability(new BigDecimal(probability));
    stage.setEnabled(enabled ? 1 : 0);
    return stage;
  }
}
