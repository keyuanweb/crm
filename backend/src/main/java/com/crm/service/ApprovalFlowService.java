package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.approval.FlowRequest;
import com.crm.dto.approval.FlowResponse;
import com.crm.entity.ApprovalFlow;
import com.crm.entity.ApprovalInstance;
import com.crm.repository.ApprovalFlowMapper;
import com.crm.repository.ApprovalInstanceMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 审批流定义服务（033-approval-flow，FR-001）：定义 CRUD + 条件分支解析 + 节点序列。 */
@Service
public class ApprovalFlowService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ApprovalFlowMapper flowMapper;
  private final ApprovalInstanceMapper instanceMapper;
  private final AuditService auditService;

  public ApprovalFlowService(
      ApprovalFlowMapper flowMapper,
      ApprovalInstanceMapper instanceMapper,
      AuditService auditService) {
    this.flowMapper = flowMapper;
    this.instanceMapper = instanceMapper;
    this.auditService = auditService;
  }

  public List<FlowResponse> list(String businessType) {
    LambdaQueryWrapper<ApprovalFlow> qw =
        new LambdaQueryWrapper<ApprovalFlow>().orderByAsc(ApprovalFlow::getId);
    if (StringUtils.hasText(businessType)) {
      qw.eq(ApprovalFlow::getBusinessType, businessType.trim());
    }
    return flowMapper.selectList(qw).stream().map(this::toResponse).toList();
  }

  @Transactional
  public FlowResponse create(FlowRequest req) {
    validate(req);
    ApprovalFlow flow = new ApprovalFlow();
    flow.setName(req.getName().trim());
    flow.setBusinessType(req.getBusinessType());
    flow.setNodes(toJson(req.getNodes()));
    flow.setConditionJson(req.getConditionJson() == null ? null : toJson(req.getConditionJson()));
    flow.setEnabled(req.getEnabled() == null || req.getEnabled());
    flowMapper.insert(flow);
    auditService.record("CREATE", "APPROVAL_FLOW", flow.getId(), "创建审批流：" + flow.getName());
    return toResponse(flow);
  }

  @Transactional
  public FlowResponse update(Long id, FlowRequest req) {
    ApprovalFlow flow = require(id);
    if (StringUtils.hasText(req.getName())) {
      flow.setName(req.getName().trim());
    }
    if (req.getBusinessType() != null) {
      flow.setBusinessType(req.getBusinessType());
    }
    if (req.getNodes() != null) {
      flow.setNodes(toJson(req.getNodes()));
    }
    if (req.getConditionJson() != null) {
      flow.setConditionJson(toJson(req.getConditionJson()));
    } else if (req.getConditionJson() == null && flow.getConditionJson() != null) {
      // 条件未传保持
    }
    if (req.getEnabled() != null) {
      flow.setEnabled(req.getEnabled());
    }
    flowMapper.updateById(flow);
    return toResponse(flow);
  }

  @Transactional
  public void delete(Long id) {
    ApprovalFlow flow = require(id);
    Long used =
        instanceMapper.selectCount(
            new LambdaQueryWrapper<ApprovalInstance>().eq(ApprovalInstance::getFlowId, id));
    if (used != null && used > 0) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "该流程已被审批实例使用，不可删除");
    }
    flowMapper.deleteById(id);
  }

  /** 按业务类型取启用流程。 */
  public ApprovalFlow enabledFlow(String businessType) {
    return flowMapper.selectOne(
        new LambdaQueryWrapper<ApprovalFlow>()
            .eq(ApprovalFlow::getBusinessType, businessType)
            .eq(ApprovalFlow::getEnabled, 1)
            .last("LIMIT 1"));
  }

  /** 解析节点序列（基础节点 + 条件命中追加节点）。 */
  public List<Map<String, Object>> resolveNodes(ApprovalFlow flow, double amount) {
    List<Map<String, Object>> nodes = parseList(flow.getNodes());
    List<Map<String, Object>> result = new ArrayList<>(nodes);
    if (StringUtils.hasText(flow.getConditionJson())) {
      try {
        Map<String, Object> cond =
            MAPPER.readValue(flow.getConditionJson(), new TypeReference<>() {});
        Double threshold = cond.get("value") instanceof Number n ? n.doubleValue() : null;
        String op = (String) cond.get("op");
        if (threshold != null && op != null && match(amount, op, threshold)) {
          Object extra = cond.get("extraNodes");
          if (extra instanceof List<?> list) {
            for (Object o : list) {
              result.add(MAPPER.convertValue(o, new TypeReference<Map<String, Object>>() {}));
            }
          }
        }
      } catch (Exception ex) {
        // 条件解析失败忽略追加
      }
    }
    return result;
  }

  private boolean match(double amount, String op, double threshold) {
    return switch (op) {
      case "GT" -> amount > threshold;
      case "GTE" -> amount >= threshold;
      case "LT" -> amount < threshold;
      case "LTE" -> amount <= threshold;
      default -> false;
    };
  }

  public ApprovalFlow require(Long id) {
    ApprovalFlow flow = flowMapper.selectById(id);
    if (flow == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "审批流不存在");
    }
    return flow;
  }

  private void validate(FlowRequest req) {
    if (!StringUtils.hasText(req.getName())
        || !StringUtils.hasText(req.getBusinessType())
        || req.getNodes() == null
        || req.getNodes().isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "流程名/业务类型/节点不能为空");
    }
  }

  private String toJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "序列化失败");
    }
  }

  private List<Map<String, Object>> parseList(String json) {
    if (!StringUtils.hasText(json)) {
      return new ArrayList<>();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception ex) {
      return new ArrayList<>();
    }
  }

  private FlowResponse toResponse(ApprovalFlow flow) {
    FlowResponse r = new FlowResponse();
    r.setId(flow.getId());
    r.setName(flow.getName());
    r.setBusinessType(flow.getBusinessType());
    r.setNodes(flow.getNodes());
    r.setConditionJson(flow.getConditionJson());
    r.setEnabled(flow.getEnabled());
    return r;
  }
}
