package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.config.CacheConfig;
import com.crm.dto.opportunity.OpportunityStageResponse;
import com.crm.entity.OpportunityStage;
import com.crm.repository.OpportunityStageMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商机阶段字典服务（1.2-stage-configurable，表见 {@code V79__opportunity_stage.sql}）。
 *
 * <p><b>为什么集中到一个服务</b>：改造前「阶段词汇」在代码里散落成 7 份互不相关的硬编码清单—— {@code SalesOpportunityService.STAGES} /
 * {@code .ACTIVE_STAGES}、{@code StageConversionService} 的赢率表、 {@code
 * OpportunityStatsService.STAGE_ORDER}、{@code StageActionTemplateService.CONFIGURABLE_STAGES}、
 * {@code DashboardStatsService} 的阶段表与赢率表、以及 {@code SalesQuotaRepository} 原始 SQL 里的 {@code
 * 'CLOSED_WON'} 字面量。增删一个阶段要改 7 处，漏掉任何一处都是静默失效（漏掉配额那处就少算达成率）。 现在唯一来源是本表。
 *
 * <p><b>缓存范式</b>：本表只有个位数行、读远多于写，故把<b>整张表缓存成一份有序列表</b>，各消费方在内存里 取自己需要的切片（全部码 / 活跃码 / 赢率）。照 {@code
 * RoleService} 的既有做法：不用 {@code @Cacheable} 注解（同类内部自调用不经过代理，注解会静默不生效），失效点由写方法显式调用 {@link #evict()}。
 * TTL 由 {@link CacheConfig} 控制（60 秒）。
 *
 * <p><b>两个终态编码是硬约定</b>：{@code CLOSED_WON} / {@code CLOSED_LOST} 被写死在 {@code SalesQuotaRepository}
 * 的原始 SQL 里（配额达成率按 {@code stage = 'CLOSED_WON'} 统计）。 删掉或改掉这两个编码，配额达成率会静默变成 0 ——
 * 正是本方案零容忍的「对外宣称成功、实际没做」类缺陷。 故它们被列为 {@link #BUILT_IN_CODES}：不可删除、不可改编码（改名可以）。
 */
@Service
public class OpportunityStageService {

  /** 进行中。 */
  public static final String TYPE_ACTIVE = "ACTIVE";

  /** 赢单（终态）。 */
  public static final String TYPE_WON = "WON";

  /** 输单（终态）。 */
  public static final String TYPE_LOST = "LOST";

  /**
   * 被原始 SQL 与既有业务逻辑按字面量引用的终态编码。
   *
   * <p>不可删除、不可改 {@code code}（{@code name} 可改）。改动它们会静默打断配额达成率统计与赢单率计算。
   */
  public static final Set<String> BUILT_IN_CODES = Set.of("CLOSED_WON", "CLOSED_LOST");

  /** 缓存键：整表只有一份，故用固定键。 */
  private static final String CACHE_KEY = "all";

  private final OpportunityStageMapper mapper;
  private final CacheManager cacheManager;
  private final AuditService auditService;

  public OpportunityStageService(
      OpportunityStageMapper mapper, CacheManager cacheManager, AuditService auditService) {
    this.mapper = mapper;
    this.cacheManager = cacheManager;
    this.auditService = auditService;
  }

  // ------------------------------------------------------------------ 读

  /** 全部阶段，按 {@code sort_order} 升序（看板列序与漏斗顺序都取自它）。 */
  @SuppressWarnings("unchecked")
  public List<OpportunityStage> allOrdered() {
    Cache cache = cacheManager.getCache(CacheConfig.OPPORTUNITY_STAGES_CACHE);
    if (cache != null) {
      Cache.ValueWrapper hit = cache.get(CACHE_KEY);
      if (hit != null && hit.get() instanceof List<?> cached) {
        return (List<OpportunityStage>) cached;
      }
    }
    List<OpportunityStage> loaded = loadOrdered();
    if (cache != null) {
      cache.put(CACHE_KEY, loaded);
    }
    return loaded;
  }

  /** 全部合法阶段编码（写入校验用）。含已停用阶段——历史数据的 stage 仍可能是它。 */
  public Set<String> allCodes() {
    return allOrdered().stream().map(OpportunityStage::getCode).collect(Collectors.toSet());
  }

  /**
   * 全部阶段编码，按 {@code sort_order} 升序（漏斗与看板列的展示顺序）。
   *
   * <p>返回 {@code List} 而非 {@code Set}：顺序本身是语义（漏斗要按阶段推进方向排）， 且刻意**含已停用阶段**——停用只表示「不能再选到」，
   * 历史商机还在那些阶段里，漏斗少了列会让金额对不上总数。
   */
  public List<String> orderedCodes() {
    return allOrdered().stream().map(OpportunityStage::getCode).toList();
  }

  /**
   * 进行中的阶段编码（看板列、可否流转、可配置动作模板）。
   *
   * <p><b>刻意不看 {@code enabled}</b>：本方法回答的是「非终态」，不是「可选」。停用阶段里的存量商机仍然要能编辑、 能被移出去，若这里把停用阶段排除掉，它们会被
   * update/close 的守卫当成「已关闭」而拒绝。要校验「能不能选入」 请用 {@link #selectableCodes()}。
   */
  public Set<String> activeCodes() {
    return allOrdered().stream()
        .filter(s -> TYPE_ACTIVE.equals(s.getStageType()))
        .map(OpportunityStage::getCode)
        .collect(Collectors.toSet());
  }

  /**
   * 可以被**新选入**的阶段编码：进行中且未停用。
   *
   * <p>与 {@link #activeCodes()} 的差别只有 {@code enabled} 一个轴，但两者不可互换——{@code activeCodes()}
   * 回答的是「这是不是终态」，本方法回答的是「还能不能选它」。混用的后果分别是：用 activeCodes 做选择校验 → 停用形同虚设（界面藏了选项，直接调接口照样能选进去）；用本方法判断终态
   * → 存量商机落在已停用阶段时会被当成 「已关闭」而无法编辑。
   *
   * <p>停用的语义是「不许新进入」，不是「不许存在」：已在该阶段里的商机必须仍能被移出去，否则停用一个阶段会把它
   * 下面的商机全部冻住。故调用方校验「目标阶段」时用本方法，校验「当前阶段是否仍可流转」时用 {@link #activeCodes()}——见 {@code
   * SalesOpportunityService#validateStage} 的入参区分。
   */
  public Set<String> selectableCodes() {
    return allOrdered().stream()
        .filter(s -> TYPE_ACTIVE.equals(s.getStageType()))
        .filter(s -> s.getEnabled() != null && s.getEnabled() == 1)
        .map(OpportunityStage::getCode)
        .collect(Collectors.toSet());
  }

  /**
   * 是否终态（赢单/输单）。终态不可再流转，其概率也固定为 1 / 0。
   *
   * <p>用 {@code stage_type} 判定而不是比对 {@link #BUILT_IN_CODES}：前者是语义，后者是「被原始 SQL
   * 钉死的编码」。正常配置下两者一致（新建阶段一律为 ACTIVE，见 {@link #create}）。
   */
  public boolean isTerminal(String code) {
    if (code == null) {
      return false;
    }
    String trimmed = code.trim();
    return allOrdered().stream()
        .anyMatch(
            s ->
                trimmed.equals(s.getCode())
                    && (TYPE_WON.equals(s.getStageType()) || TYPE_LOST.equals(s.getStageType())));
  }

  /**
   * 预测赢率。
   *
   * <p>终态按 {@code stage_type} 直接返回 1 / 0（不查历史、也不看 {@code probability} 列）—— 赢单/输单的语义
   * 是确定的，让它随历史转化率漂移毫无意义（这是 {@code StageConversionService} 原有的设计，此处保持）。 未知编码返回 0，与改造前 {@code
   * DEFAULT_PROBABILITY.getOrDefault(stage, 0d)} 的行为一致。
   */
  public double probabilityOf(String code) {
    if (code == null) {
      return 0d;
    }
    String trimmed = code.trim();
    return allOrdered().stream()
        .filter(s -> trimmed.equals(s.getCode()))
        .findFirst()
        .map(
            s -> {
              if (TYPE_WON.equals(s.getStageType())) {
                return 1d;
              }
              if (TYPE_LOST.equals(s.getStageType())) {
                return 0d;
              }
              BigDecimal p = s.getProbability();
              return p == null ? 0d : p.doubleValue();
            })
        .orElse(0d);
  }

  // ------------------------------------------------------------------ API 出参

  /** 阶段列表（全部，含停用），按 sort_order 升序。 */
  public List<OpportunityStageResponse> list() {
    return allOrdered().stream().map(OpportunityStageService::toResponse).toList();
  }

  private static OpportunityStageResponse toResponse(OpportunityStage stage) {
    OpportunityStageResponse res = new OpportunityStageResponse();
    res.setId(stage.getId());
    res.setCode(stage.getCode());
    res.setName(stage.getName());
    res.setSortOrder(stage.getSortOrder());
    res.setProbability(stage.getProbability());
    res.setStageType(stage.getStageType());
    res.setEnabled(stage.getEnabled());
    res.setBuiltIn(BUILT_IN_CODES.contains(stage.getCode()));
    res.setVersion(stage.getVersion());
    res.setCreatedAt(stage.getCreatedAt());
    return res;
  }

  @Transactional
  public OpportunityStageResponse create(
      String code, String name, Integer sortOrder, BigDecimal probability, String stageType) {
    String normalized = requireCode(code);
    if (allCodes().contains(normalized)) {
      throw new BusinessException(ErrorCode.OPPORTUNITY_STAGE_DUPLICATE);
    }
    // 新建阶段一律为进行中：终态只有「已赢单/已输单」两个，且它们的编码被 SalesQuotaRepository 的原始
    // SQL 钉死。允许再建一个 stage_type=WON 的阶段会造出一批「算赢单、却不计入配额达成率」的商机——
    // 界面显示赢单、报表少了这笔，正是本方案零容忍的假成功类缺陷。
    if (stageType != null && !TYPE_ACTIVE.equals(stageType)) {
      throw new BusinessException(ErrorCode.OPPORTUNITY_STAGE_NOT_ACTIVE);
    }
    OpportunityStage stage = new OpportunityStage();
    stage.setCode(normalized);
    stage.setName(requireName(name));
    stage.setSortOrder(sortOrder == null ? 0 : sortOrder);
    stage.setProbability(validateProbability(probability));
    stage.setStageType(TYPE_ACTIVE);
    stage.setEnabled(1);
    mapper.insert(stage);
    evict();
    auditService.record("stage:create", "opportunity_stage", stage.getId(), normalized);
    return toResponse(stage);
  }

  @Transactional
  public OpportunityStageResponse update(
      Long id, String name, Integer sortOrder, BigDecimal probability) {
    OpportunityStage stage = requireById(id);
    // 编码刻意不可改：历史商机的 stage 列按它关联，改编码等于让历史数据指向一个不存在的阶段。
    stage.setName(requireName(name));
    stage.setSortOrder(sortOrder == null ? 0 : sortOrder);
    // 终态的赢率由 stage_type 决定（1/0），不接受配置——否则 probabilityOf 会与实际取用的值不一致。
    if (!TYPE_ACTIVE.equals(stage.getStageType())) {
      stage.setProbability(
          TYPE_WON.equals(stage.getStageType()) ? BigDecimal.ONE : BigDecimal.ZERO);
    } else {
      stage.setProbability(validateProbability(probability));
    }
    mapper.updateById(stage);
    evict();
    auditService.record("stage:update", "opportunity_stage", id, stage.getCode());
    return toResponse(stage);
  }

  /** 启停。停用只影响「还能不能选到」，历史商机照常显示。 */
  @Transactional
  public void setEnabled(Long id, boolean enabled) {
    OpportunityStage stage = requireById(id);
    stage.setEnabled(enabled ? 1 : 0);
    mapper.updateById(stage);
    evict();
    auditService.record(
        enabled ? "stage:enable" : "stage:disable", "opportunity_stage", id, stage.getCode());
  }

  @Transactional
  public void delete(Long id) {
    OpportunityStage stage = requireById(id);
    if (BUILT_IN_CODES.contains(stage.getCode())) {
      throw new BusinessException(ErrorCode.OPPORTUNITY_STAGE_BUILT_IN);
    }
    if (stage.getEnabled() != null && stage.getEnabled() == 1) {
      // 删之前必须先停用：一面在用的阶段被直接删除，看板列会当场消失而商机还在。
      throw new BusinessException(ErrorCode.OPPORTUNITY_STAGE_STILL_ENABLED);
    }
    // 用 deleteById 走 MyBatis-Plus 的逻辑删除，而不是手动 setDeleted(1)+updateById：
    // @TableLogic 字段默认不参与 updateById 的 SET 子句，手写会静默不生效（行还在，只是谁都没删掉）。
    mapper.deleteById(id);
    evict();
    auditService.record("stage:delete", "opportunity_stage", id, stage.getCode());
  }

  /** 使阶段缓存失效。阶段写入后必须调用——否则最多 60 秒内新阶段无法被选用。 */
  public void evict() {
    Cache cache = cacheManager.getCache(CacheConfig.OPPORTUNITY_STAGES_CACHE);
    if (cache != null) {
      cache.evict(CACHE_KEY);
    }
  }

  // ------------------------------------------------------------------ 内部

  private List<OpportunityStage> loadOrdered() {
    return List.copyOf(
        mapper.selectList(
            new LambdaQueryWrapper<OpportunityStage>()
                .orderByAsc(OpportunityStage::getSortOrder)
                .orderByAsc(OpportunityStage::getId)));
  }

  private OpportunityStage requireById(Long id) {
    OpportunityStage stage = id == null ? null : mapper.selectById(id);
    if (stage == null) {
      throw new BusinessException(ErrorCode.OPPORTUNITY_STAGE_NOT_FOUND);
    }
    return stage;
  }

  private static String requireCode(String code) {
    if (code == null || code.isBlank()) {
      throw new BusinessException(ErrorCode.STAGE_INVALID);
    }
    return code.trim();
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new BusinessException(ErrorCode.STAGE_INVALID);
    }
    return name.trim();
  }

  /** 校验并归一赢率。只服务进行中的阶段——终态的赢率由 {@code stage_type} 决定（1 / 0），不走这里， 故没有 {@code stageType} 参数。 */
  private static BigDecimal validateProbability(BigDecimal probability) {
    if (probability == null) {
      return BigDecimal.ZERO;
    }
    if (probability.compareTo(BigDecimal.ZERO) < 0 || probability.compareTo(BigDecimal.ONE) > 0) {
      throw new BusinessException(ErrorCode.OPPORTUNITY_STAGE_PROBABILITY_INVALID);
    }
    return probability;
  }
}
