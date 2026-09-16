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
import com.crm.security.ClientIpResolver;
import com.crm.security.RateLimiter;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 在线表单服务（036-online-forms，FR-001~006）：表单 CRUD + 发布 + 公开匿名提交（字段校验/防重复/内存频控/建线索 + 快照）。
 *
 * <p>⚠️ <b>2026-09-16（100-rate-limit-consolidation）</b>：上面那句里的「<b>内存频控</b>」<b>原文逐字保留</b>， 但它
 * <b>今天已不成立</b> —— 频控已从本类的私有内存桶（字段 {@code rateBuckets}（{@code Map<String, Deque<Long>>}，一张 IP →
 * 最近提交时间戳的表）+ 它的清理逻辑 {@code cleanupRateBuckets}） 改为委托共享限流件（Redis 固定窗口）。保留原文是因为它记录了本类改造前的形态； 新形态见
 * {@link #checkRateLimit(String)}，配额与窗口（3 次 / 60 秒）逐字未变 —— 只有<b>窗口算法</b>从滑动窗口变成固定窗口（本批第 ⑥
 * 处对外可观测变更，见 plan.md）。 ⚠️ 旧字段名在这里<b>只作为留痕出现</b>（可执行位置零命中，见 quickstart §5 判据 ②）。
 */
@Service
public class FormService {

  private static final Logger log = LoggerFactory.getLogger(FormService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 公开提交的配额：**3 次 / 60 秒**（100-rate-limit-consolidation 改造前的数字，逐字未变）。 */
  private static final int RATE_LIMIT = 3;

  /**
   * 与改造前的 {@code RATE_WINDOW_MS = 60_000L} 同一个窗口（值未变，只把单位从毫秒改成秒 —— 共享件的 {@code windowSeconds}
   * 以秒为单位）。
   */
  private static final long RATE_WINDOW_SECONDS = 60L;

  private final FormMapper formMapper;
  private final FormSubmissionMapper submissionMapper;
  private final LeadMapper leadMapper;
  private final AuditService auditService;
  private final RateLimiter rateLimiter;
  private final ClientIpResolver ipResolver;

  public FormService(
      FormMapper formMapper,
      FormSubmissionMapper submissionMapper,
      LeadMapper leadMapper,
      AuditService auditService,
      RateLimiter rateLimiter,
      ClientIpResolver ipResolver) {
    this.formMapper = formMapper;
    this.submissionMapper = submissionMapper;
    this.leadMapper = leadMapper;
    this.auditService = auditService;
    this.rateLimiter = rateLimiter;
    this.ipResolver = ipResolver;
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

  /**
   * 公开提交：匿名（需表单启用）。返回 { submissionId, leadId, message }。
   *
   * <p>⚠️ <b>2026-09-16（100-rate-limit-consolidation）</b>：改造前这里调私有 {@code
   * FormService#clientIp(request)}（三份逐字副本之一，已随本批删除）⇒ 现在统一走 {@link ClientIpResolver} 的<b>实例</b>入口
   * （因此**读** {@code crm.rate-limit.trust-forwarded-for}，与本端点限流侧的口径一致）。 「{@code request == null} ⇒
   * {@code "unknown"}」这个退化字面量是改造前就有的，<b>逐字保留</b>：单测与内部调用会传 {@code null}。 另：{@code client_ip}
   * 快照列与限流分桶现在取<b>同一个值</b>（改造前也是），故 {@code trust-forwarded-for=false} 时该列记的是 {@code remoteAddr} ——
   * 这是那个开关的应有含义，已登记。
   */
  @Transactional
  public Map<String, Object> submit(
      Long formId, Map<String, Object> payload, HttpServletRequest request) {
    Form form = require(formId);
    if (!"ENABLED".equals(form.getStatus())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "表单已停用");
    }
    String ip =
        request == null
            ? "unknown"
            : ipResolver.resolve(
                request,
                StringUtils.hasText(request.getRemoteAddr()) ? request.getRemoteAddr() : "unknown");
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
    // 053：UTM 归因参数（从请求查询字符串捕获）
    applyUtm(submission, request);
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

  /**
   * 公开提交的 IP 频控：**3 次 / 60 秒**（改造前的数字，逐字不变），委托给共享限流件。
   *
   * <p><b>⚠️ 拒绝状态码由 400 订正为 429</b>（100-rate-limit-consolidation）：改造前这里抛 {@code
   * BusinessException(ErrorCode.BAD_REQUEST, "提交过于频繁，请稍后再试")} ⇒ **400**，而 036 的冻结契约 {@code
   * specs/036-online-forms/contracts/online-forms.md:44} 与 {@code
   * specs/036-online-forms/tasks.md:50} 都承诺 **429**（前者逐字：「**429**: 频控（同 IP 1 分钟超 3 次）。」）。 ⇒
   * 这是**实现向冻结契约靠拢**，不是行为破坏；照 085 的判例（见 {@code GlobalExceptionHandler} 里那段注释）， <b>契约一个字符不改，改的是实现</b>
   * —— <b>036 的任何工件本批都未改动</b>。 收敛后抛的是 {@link com.crm.common.RateLimitExceededException}（{@code
   * ErrorCode.RATE_LIMITED}，429 + {@code Retry-After}）。
   *
   * <p><b>为什么它留在服务里、而不是把配额挂到 {@code FormController#submit} 的注解上</b>（C3 对邮件追踪用的是注解， 两处形态**刻意不同**）：①
   * 被限流单元是<b>服务方法</b>（本方法在字段校验之前就跑），注解会把「服务自持配额」 这层语义挪到 controller；② 本方法拿的是**已解析好的 IP 字符串**，而注解只能从
   * {@code HttpServletRequest} 现取； ③ 调用点必须保留 {@code request == null → "unknown"}
   * 这个**退化字面量**（单测与内部调用会传 {@code null}）， 注解形态无处安放它。 ⇒ 端点侧的覆盖台账把 {@code FormController#submit}
   * 列入**带理由的豁免**（不是漏标）。
   */
  private void checkRateLimit(String ip) {
    rateLimiter.checkIp("public-form-submit", RATE_LIMIT, RATE_WINDOW_SECONDS, ip);
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

  /** 053：从请求查询字符串捕获 UTM 归因参数（可空）。 */
  private void applyUtm(FormSubmission submission, HttpServletRequest request) {
    if (request == null) {
      return;
    }
    submission.setUtmSource(utmParam(request, "utm_source"));
    submission.setUtmMedium(utmParam(request, "utm_medium"));
    submission.setUtmCampaign(utmParam(request, "utm_campaign"));
    submission.setUtmTerm(utmParam(request, "utm_term"));
    submission.setUtmContent(utmParam(request, "utm_content"));
  }

  private String utmParam(HttpServletRequest request, String name) {
    String v = request.getParameter(name);
    return v == null ? null : v.trim();
  }
}
