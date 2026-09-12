package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.playbook.ActionTemplateRequest;
import com.crm.dto.playbook.ActionTemplateResponse;
import com.crm.entity.StageActionTemplate;
import com.crm.repository.StageActionTemplateMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 阶段动作模板服务（045，FR-P01/P02）：CRUD/启停。 */
@Service
public class StageActionTemplateService {

  private final StageActionTemplateMapper templateMapper;
  private final AuditService auditService;
  private final OpportunityStageService stageService;

  public StageActionTemplateService(
      StageActionTemplateMapper templateMapper,
      AuditService auditService,
      OpportunityStageService stageService) {
    this.templateMapper = templateMapper;
    this.auditService = auditService;
    this.stageService = stageService;
  }

  public PageResult<ActionTemplateResponse> page(String stage, long page, long pageSize) {
    LambdaQueryWrapper<StageActionTemplate> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(stage)) {
      qw.eq(StageActionTemplate::getStage, stage.trim());
    }
    qw.orderByAsc(StageActionTemplate::getStage)
        .orderByAsc(StageActionTemplate::getSortOrder)
        .orderByAsc(StageActionTemplate::getId);
    Page<StageActionTemplate> p = templateMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public ActionTemplateResponse create(ActionTemplateRequest req) {
    validateStage(req.getStage());
    StageActionTemplate template = new StageActionTemplate();
    apply(req, template);
    template.setEnabled(1);
    template.setCreatedBy(SecurityUtil.currentUserId());
    templateMapper.insert(template);
    auditService.record(
        "CREATE", "STAGE_ACTION_TEMPLATE", template.getId(), "创建动作模板：" + template.getActionName());
    return toResponse(template);
  }

  @Transactional
  public ActionTemplateResponse update(Long id, ActionTemplateRequest req) {
    StageActionTemplate existing = require(id);
    validateStage(req.getStage());
    apply(req, existing);
    existing.setVersion(req.getVersion());
    int rows = templateMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record(
        "UPDATE", "STAGE_ACTION_TEMPLATE", id, "编辑动作模板：" + existing.getActionName());
    return toResponse(templateMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    StageActionTemplate template = require(id);
    templateMapper.deleteById(id);
    auditService.record(
        "DELETE", "STAGE_ACTION_TEMPLATE", id, "删除动作模板：" + template.getActionName());
  }

  /**
   * 仅活动阶段可配置动作模板。
   *
   * <p>取值改读阶段字典（1.2）：此前是硬编码的两个码，新增阶段会静默地不可配置动作—— 页面能建出阶段、却没法给它配动作，且不报错。
   */
  private void validateStage(String stage) {
    if (stage == null || !stageService.activeCodes().contains(stage.trim())) {
      throw new BusinessException(ErrorCode.PLAYBOOK_STAGE_INVALID);
    }
  }

  private void apply(ActionTemplateRequest req, StageActionTemplate template) {
    template.setStage(req.getStage().trim());
    template.setActionName(req.getActionName().trim());
    template.setDescription(req.getDescription());
    template.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
    template.setRequired(Boolean.TRUE.equals(req.getRequired()) ? 1 : 0);
  }

  private StageActionTemplate require(Long id) {
    StageActionTemplate template = templateMapper.selectById(id);
    if (template == null) {
      throw new BusinessException(ErrorCode.PLAYBOOK_TEMPLATE_NOT_FOUND);
    }
    return template;
  }

  private ActionTemplateResponse toResponse(StageActionTemplate template) {
    ActionTemplateResponse resp = new ActionTemplateResponse();
    resp.setId(template.getId());
    resp.setStage(template.getStage());
    resp.setActionName(template.getActionName());
    resp.setDescription(template.getDescription());
    resp.setSortOrder(template.getSortOrder());
    resp.setRequired(template.getRequired() != null && template.getRequired() == 1);
    resp.setEnabled(template.getEnabled() != null && template.getEnabled() == 1);
    resp.setVersion(template.getVersion());
    resp.setCreatedAt(template.getCreatedAt());
    return resp;
  }

  /** 某阶段启用模板列表（供动作清单查询）。 */
  public List<StageActionTemplate> enabledForStage(String stage) {
    return templateMapper.selectList(
        new LambdaQueryWrapper<StageActionTemplate>()
            .eq(StageActionTemplate::getStage, stage)
            .eq(StageActionTemplate::getEnabled, 1)
            .orderByAsc(StageActionTemplate::getSortOrder)
            .orderByAsc(StageActionTemplate::getId));
  }
}
