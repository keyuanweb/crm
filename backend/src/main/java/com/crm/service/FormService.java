package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.form.FormRequest;
import com.crm.dto.form.FormResponse;
import com.crm.dto.form.SubmissionResponse;
import com.crm.entity.Form;
import com.crm.entity.FormSubmission;
import com.crm.entity.Lead;
import com.crm.repository.FormMapper;
import com.crm.repository.FormSubmissionMapper;
import com.crm.repository.LeadMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 在线表单服务（036-online-forms，FR-001~006）：表单 CRUD + 发布 + 公开匿名提交（字段校验/防重复/内存频控/建线索 + 快照）。 */
@Service
public class FormService {

  private static final Logger log = LoggerFactory.getLogger(FormService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int RATE_LIMIT = 3;
  private static final long RATE_WINDOW_MS = 60_000L;

  /** IP → 最近提交时间戳队列（内存频控，防爬）。 */
  private final Map<String, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

  private final FormMapper formMapper;
  private final FormSubmissionMapper submissionMapper;
  private final LeadMapper leadMapper;
  private final AuditService auditService;

  public FormService(
      FormMapper formMapper,
      FormSubmissionMapper submissionMapper,
      LeadMapper leadMapper,
      AuditService auditService) {
    this.formMapper = formMapper;
    this.submissionMapper = submissionMapper;
    this.leadMapper = leadMapper;
    this.auditService = auditService;
  }

  public List<FormResponse> list() {
    return formMapper.selectList(new LambdaQueryWrapper<Form>().orderByDesc(Form::getId)).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public FormResponse create(FormRequest req) {
    validate(req);
    Form form = new Form();
    form.setName(req.getName().trim());
    form.setFields(toJson(req.getFields()));
    form.setSuccessMessage(req.getSuccessMessage());
    form.setSource(StringUtils.hasText(req.getSource()) ? req.getSource() : "WEBSITE");
    form.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "ENABLED");
    form.setSubmissionCount(0);
    form.setCreatedBy(SecurityUtil.currentUserId());
    formMapper.insert(form);
    auditService.record("CREATE", "FORM", form.getId(), "创建在线表单：" + form.getName());
    return toResponse(form);
  }

  @Transactional
  public FormResponse update(Long id, FormRequest req) {
    Form form = require(id);
    if (StringUtils.hasText(req.getName())) {
      form.setName(req.getName().trim());
    }
    if (req.getFields() != null) {
      form.setFields(toJson(req.getFields()));
    }
    if (req.getSuccessMessage() != null) {
      form.setSuccessMessage(req.getSuccessMessage());
    }
    if (req.getSource() != null) {
      form.setSource(req.getSource());
    }
    if (req.getStatus() != null) {
      form.setStatus(req.getStatus());
    }
    formMapper.updateById(form);
    return toResponse(form);
  }

  @Transactional
  public void delete(Long id) {
    Form form = require(id);
    submissionMapper.delete(
        new LambdaQueryWrapper<FormSubmission>().eq(FormSubmission::getFormId, id));
    formMapper.deleteById(id);
    auditService.record("DELETE", "FORM", id, "删除在线表单：" + form.getName());
  }

  @Transactional
  public FormResponse toggle(Long id) {
    Form form = require(id);
    form.setStatus("ENABLED".equals(form.getStatus()) ? "DISABLED" : "ENABLED");
    formMapper.updateById(form);
    return toResponse(form);
  }

  /** 提交记录分页。 */
  public PageResult<SubmissionResponse> submissions(Long formId, long page, long pageSize) {
    require(formId);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<FormSubmission> p =
        submissionMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
            new LambdaQueryWrapper<FormSubmission>()
                .eq(FormSubmission::getFormId, formId)
                .orderByDesc(FormSubmission::getId));
    return PageResult.of(
        p.getRecords().stream().map(this::toSubmissionResponse).toList(),
        p.getTotal(),
        page,
        pageSize);
  }

  /** 公开提交：匿名（需表单启用）。返回 { submissionId, leadId, message }。 */
  @Transactional
  public Map<String, Object> submit(
      Long formId, Map<String, Object> payload, HttpServletRequest request) {
    Form form = require(formId);
    if (!"ENABLED".equals(form.getStatus())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "表单已停用");
    }
    String ip = request == null ? "unknown" : clientIp(request);
    checkRateLimit(ip);

    List<Map<String, Object>> fields = parseFields(form.getFields());
    Map<String, String> values = new LinkedHashMap<>();
    for (Map<String, Object> field : fields) {
      String key = String.valueOf(field.getOrDefault("field", ""));
      boolean required = Boolean.TRUE.equals(field.get("required"));
      String raw = payload.get(key) == null ? null : String.valueOf(payload.get(key)).trim();
      if (required && !StringUtils.hasText(raw)) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "字段[" + key + "]为必填");
      }
      if (StringUtils.hasText(raw) && raw.length() > 200) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "字段[" + key + "]超长");
      }
      if (StringUtils.hasText(raw)) {
        values.put(key, raw);
      }
    }
    // 防重复：email 或 phone 已存在未删除 lead
    String phone = values.get("phone");
    String email = values.get("email");
    if (StringUtils.hasText(phone) || StringUtils.hasText(email)) {
      Long exists =
          leadMapper.selectCount(
              new LambdaQueryWrapper<Lead>()
                  .and(
                      w -> {
                        if (StringUtils.hasText(phone)) {
                          w.eq(Lead::getPhone, phone);
                        }
                        if (StringUtils.hasText(email)) {
                          if (StringUtils.hasText(phone)) {
                            w.or();
                          }
                          w.eq(Lead::getEmail, email);
                        }
                      }));
      if (exists != null && exists > 0) {
        throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "已收到您的申请，请勿重复提交");
      }
    }
    // 建线索
    Lead lead = new Lead();
    lead.setName(values.getOrDefault("name", "表单线索"));
    lead.setCompany(values.getOrDefault("company", "表单提交"));
    lead.setPhone(values.get("phone"));
    lead.setEmail(values.get("email"));
    lead.setSource(form.getSource());
    lead.setStatus("NEW");
    lead.setCreatedBy(SecurityUtil.currentUserId());
    leadMapper.insert(lead);
    // 快照
    FormSubmission submission = new FormSubmission();
    submission.setFormId(formId);
    submission.setPayload(toJson(values));
    submission.setClientIp(ip);
    submission.setLeadId(lead.getId());
    submission.setCreatedAt(LocalDateTime.now());
    submissionMapper.insert(submission);
    // 计数
    form.setSubmissionCount(
        (form.getSubmissionCount() == null ? 0 : form.getSubmissionCount()) + 1);
    formMapper.updateById(form);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("submissionId", submission.getId());
    result.put("leadId", lead.getId());
    result.put(
        "message",
        StringUtils.hasText(form.getSuccessMessage()) ? form.getSuccessMessage() : "提交成功");
    return result;
  }

  /** 公开提交：匿名（无需登录）。 */
  public Form requirePublic(Long id) {
    return require(id);
  }

  /** 表单字段（公开页渲染用）。 */
  public List<Map<String, Object>> fieldsOf(Long id) {
    Form form = require(id);
    return parseFields(form.getFields());
  }

  private void checkRateLimit(String ip) {
    long now = System.currentTimeMillis();
    Deque<Long> queue = rateBuckets.computeIfAbsent(ip, k -> new ArrayDeque<>());
    synchronized (queue) {
      while (!queue.isEmpty() && now - queue.peekFirst() > RATE_WINDOW_MS) {
        queue.pollFirst();
      }
      if (queue.size() >= RATE_LIMIT) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "提交过于频繁，请稍后再试");
      }
      queue.addLast(now);
    }
    cleanupRateBuckets();
  }

  /** 定期清理过期频控桶，防止内存泄漏。 */
  private void cleanupRateBuckets() {
    long now = System.currentTimeMillis();
    if (rateBuckets.size() > 1000) {
      rateBuckets
          .entrySet()
          .removeIf(
              e -> {
                Deque<Long> q = e.getValue();
                synchronized (q) {
                  while (!q.isEmpty() && now - q.peekFirst() > RATE_WINDOW_MS) {
                    q.pollFirst();
                  }
                  return q.isEmpty();
                }
              });
    }
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwarded)) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private void validate(FormRequest req) {
    if (!StringUtils.hasText(req.getName())
        || req.getFields() == null
        || req.getFields().isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "表单名与字段不能为空");
    }
  }

  private Form require(Long id) {
    Form form = formMapper.selectById(id);
    if (form == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "表单不存在");
    }
    return form;
  }

  private List<Map<String, Object>> parseFields(String json) {
    if (!StringUtils.hasText(json)) {
      return new ArrayList<>();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception ex) {
      return new ArrayList<>();
    }
  }

  private String toJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "序列化失败");
    }
  }

  private FormResponse toResponse(Form form) {
    FormResponse r = new FormResponse();
    r.setId(form.getId());
    r.setName(form.getName());
    r.setFields(form.getFields());
    r.setSuccessMessage(form.getSuccessMessage());
    r.setSource(form.getSource());
    r.setStatus(form.getStatus());
    r.setSubmissionCount(form.getSubmissionCount());
    r.setCreatedAt(form.getCreatedAt());
    return r;
  }

  private SubmissionResponse toSubmissionResponse(FormSubmission s) {
    SubmissionResponse r = new SubmissionResponse();
    r.setId(s.getId());
    r.setPayload(s.getPayload());
    r.setClientIp(s.getClientIp());
    r.setLeadId(s.getLeadId());
    r.setCreatedAt(s.getCreatedAt());
    return r;
  }
}
