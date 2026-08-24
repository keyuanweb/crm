package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.playbook.ActionViewResponse;
import com.crm.entity.SalesOpportunity;
import com.crm.entity.SalesOpportunityAction;
import com.crm.entity.StageActionTemplate;
import com.crm.repository.SalesOpportunityActionMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.repository.StageActionTemplateMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 销售机会动作服务（045，FR-P03~P06）：清单查询/勾选完成/必做校验。 */
@Service
public class SalesOpportunityActionService {

  private final SalesOpportunityActionMapper actionMapper;
  private final SalesOpportunityMapper soMapper;
  private final StageActionTemplateMapper templateMapper;

  public SalesOpportunityActionService(
      SalesOpportunityActionMapper actionMapper,
      SalesOpportunityMapper soMapper,
      StageActionTemplateMapper templateMapper) {
    this.actionMapper = actionMapper;
    this.soMapper = soMapper;
    this.templateMapper = templateMapper;
  }

  /** 销售机会当前阶段动作清单（仅启用模板，含完成状态）。 */
  public List<ActionViewResponse> listForOpportunity(Long opportunityId) {
    SalesOpportunity so = requireOpportunity(opportunityId);
    List<StageActionTemplate> templates = enabledTemplates(so.getStage());
    if (templates.isEmpty()) {
      return List.of();
    }
    Map<Long, SalesOpportunityAction> done =
        actionMapper
            .selectList(
                new LambdaQueryWrapper<SalesOpportunityAction>()
                    .eq(SalesOpportunityAction::getOpportunityId, opportunityId))
            .stream()
            .collect(Collectors.toMap(SalesOpportunityAction::getTemplateId, a -> a));
    return templates.stream()
        .map(
            t -> {
              ActionViewResponse resp = new ActionViewResponse();
              resp.setTemplateId(t.getId());
              resp.setActionName(t.getActionName());
              resp.setDescription(t.getDescription());
              resp.setRequired(t.getRequired() != null && t.getRequired() == 1);
              SalesOpportunityAction doneAction = done.get(t.getId());
              resp.setCompleted(doneAction != null);
              resp.setCompletedBy(doneAction == null ? null : doneAction.getCompletedBy());
              resp.setCompletedAt(doneAction == null ? null : doneAction.getCompletedAt());
              return resp;
            })
        .toList();
  }

  /** 勾选完成动作（同一机会同一动作唯一）。 */
  @Transactional
  public ActionViewResponse complete(Long opportunityId, Long templateId) {
    SalesOpportunity so = requireOpportunity(opportunityId);
    if (so.getClosedAt() != null) {
      throw new BusinessException(ErrorCode.PLAYBOOK_OPPORTUNITY_CLOSED);
    }
    StageActionTemplate template = templateMapper.selectById(templateId);
    if (template == null) {
      throw new BusinessException(ErrorCode.PLAYBOOK_TEMPLATE_NOT_FOUND);
    }
    Long exists =
        actionMapper.selectCount(
            new LambdaQueryWrapper<SalesOpportunityAction>()
                .eq(SalesOpportunityAction::getOpportunityId, opportunityId)
                .eq(SalesOpportunityAction::getTemplateId, templateId));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.PLAYBOOK_ACTION_ALREADY_DONE);
    }
    SalesOpportunityAction action = new SalesOpportunityAction();
    action.setOpportunityId(opportunityId);
    action.setTemplateId(templateId);
    action.setCompletedBy(SecurityUtil.currentUserId());
    action.setCompletedAt(LocalDateTime.now());
    actionMapper.insert(action);

    ActionViewResponse resp = new ActionViewResponse();
    resp.setTemplateId(templateId);
    resp.setActionName(template.getActionName());
    resp.setDescription(template.getDescription());
    resp.setRequired(template.getRequired() != null && template.getRequired() == 1);
    resp.setCompleted(true);
    resp.setCompletedBy(action.getCompletedBy());
    resp.setCompletedAt(action.getCompletedAt());
    return resp;
  }

  /** 当前阶段是否存在未完成必做动作（供流转提示）。 */
  public boolean hasPendingRequired(Long opportunityId) {
    SalesOpportunity so = requireOpportunity(opportunityId);
    List<StageActionTemplate> templates =
        enabledTemplates(so.getStage()).stream()
            .filter(t -> t.getRequired() != null && t.getRequired() == 1)
            .toList();
    if (templates.isEmpty()) {
      return false;
    }
    Long done =
        actionMapper.selectCount(
            new LambdaQueryWrapper<SalesOpportunityAction>()
                .eq(SalesOpportunityAction::getOpportunityId, opportunityId)
                .in(
                    SalesOpportunityAction::getTemplateId,
                    templates.stream().map(StageActionTemplate::getId).toList()));
    return done == null || done < templates.size();
  }

  private List<StageActionTemplate> enabledTemplates(String stage) {
    return templateMapper.selectList(
        new LambdaQueryWrapper<StageActionTemplate>()
            .eq(StageActionTemplate::getStage, stage)
            .eq(StageActionTemplate::getEnabled, 1)
            .orderByAsc(StageActionTemplate::getSortOrder)
            .orderByAsc(StageActionTemplate::getId));
  }

  private SalesOpportunity requireOpportunity(Long id) {
    SalesOpportunity so = soMapper.selectById(id);
    if (so == null) {
      throw new BusinessException(ErrorCode.SALES_OPPORTUNITY_NOT_FOUND);
    }
    return so;
  }

  /** 校验机会归属（供其他模块复用：动作必须属于可访问机会）。 */
  public boolean belongsToOpportunity(Long opportunityId, Long soId) {
    return Objects.equals(opportunityId, soId);
  }
}
