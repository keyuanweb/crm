package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.landing.LandingPagePublicResponse;
import com.crm.dto.landing.LandingPageRequest;
import com.crm.dto.landing.LandingPageResponse;
import com.crm.dto.landing.UtmStatsResponse;
import com.crm.entity.Form;
import com.crm.entity.FormSubmission;
import com.crm.entity.LandingPage;
import com.crm.repository.FormMapper;
import com.crm.repository.FormSubmissionMapper;
import com.crm.repository.LandingPageMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 托管落地页服务（053，FR-L01~L07）：CRUD/公开渲染/UTM 统计。 */
@Service
public class LandingPageService {

  private final LandingPageMapper landingPageMapper;
  private final FormMapper formMapper;
  private final FormSubmissionMapper submissionMapper;
  private final FormService formService;
  private final AuditService auditService;

  public LandingPageService(
      LandingPageMapper landingPageMapper,
      FormMapper formMapper,
      FormSubmissionMapper submissionMapper,
      FormService formService,
      AuditService auditService) {
    this.landingPageMapper = landingPageMapper;
    this.formMapper = formMapper;
    this.submissionMapper = submissionMapper;
    this.formService = formService;
    this.auditService = auditService;
  }

  public PageResult<LandingPageResponse> page(String keyword, long page, long pageSize) {
    LambdaQueryWrapper<LandingPage> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      qw.like(LandingPage::getTitle, keyword.trim());
    }
    qw.orderByDesc(LandingPage::getId);
    Page<LandingPage> p = landingPageMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public LandingPageResponse create(LandingPageRequest req) {
    validateForm(req.getFormId());
    LandingPage lp = new LandingPage();
    lp.setTitle(req.getTitle().trim());
    lp.setSubtitle(req.getSubtitle());
    lp.setDescription(req.getDescription());
    lp.setThemeColor(req.getThemeColor());
    lp.setFormId(req.getFormId());
    lp.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
    lp.setCreatedBy(SecurityUtil.currentUserId());
    landingPageMapper.insert(lp);
    auditService.record("CREATE", "LANDING_PAGE", lp.getId(), "创建落地页：" + lp.getTitle());
    return toResponse(landingPageMapper.selectById(lp.getId()));
  }

  @Transactional
  public LandingPageResponse update(Long id, LandingPageRequest req) {
    LandingPage existing = require(id);
    validateForm(req.getFormId());
    existing.setTitle(req.getTitle().trim());
    existing.setSubtitle(req.getSubtitle());
    existing.setDescription(req.getDescription());
    existing.setThemeColor(req.getThemeColor());
    existing.setFormId(req.getFormId());
    existing.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
    existing.setVersion(req.getVersion());
    landingPageMapper.updateById(existing);
    auditService.record("UPDATE", "LANDING_PAGE", id, "编辑落地页：" + existing.getTitle());
    return toResponse(landingPageMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    LandingPage lp = require(id);
    landingPageMapper.deleteById(id);
    auditService.record("DELETE", "LANDING_PAGE", id, "删除落地页：" + lp.getTitle());
  }

  /** 公开渲染（落地页 + 表单，均须启用）。 */
  public LandingPagePublicResponse publicView(Long id) {
    LandingPage lp = require(id);
    Form form = formMapper.selectById(lp.getFormId());
    if (lp.getEnabled() == null
        || lp.getEnabled() != 1
        || form == null
        || !"ENABLED".equals(form.getStatus())) {
      throw new BusinessException(ErrorCode.LANDING_PAGE_UNAVAILABLE);
    }
    LandingPagePublicResponse resp = new LandingPagePublicResponse();
    resp.setId(lp.getId());
    resp.setTitle(lp.getTitle());
    resp.setSubtitle(lp.getSubtitle());
    resp.setDescription(lp.getDescription());
    resp.setThemeColor(lp.getThemeColor());
    resp.setEnabled(true);
    LandingPagePublicResponse.FormMeta meta = new LandingPagePublicResponse.FormMeta();
    meta.setId(form.getId());
    meta.setName(form.getName());
    meta.setSuccessMessage(form.getSuccessMessage());
    meta.setFields(formService.fieldsOf(form.getId()));
    resp.setForm(meta);
    return resp;
  }

  /** UTM 归因统计（按 source/campaign 聚合提交数）。 */
  public UtmStatsResponse stats(Long landingPageId, LocalDateTime from, LocalDateTime to) {
    require(landingPageId);
    LambdaQueryWrapper<FormSubmission> qw = new LambdaQueryWrapper<>();
    qw.eq(FormSubmission::getFormId, require(landingPageId).getFormId());
    if (from != null) {
      qw.ge(FormSubmission::getCreatedAt, from);
    }
    if (to != null) {
      qw.le(FormSubmission::getCreatedAt, to);
    }
    List<FormSubmission> submissions = submissionMapper.selectList(qw);
    UtmStatsResponse resp = new UtmStatsResponse();
    resp.setLandingPageId(landingPageId);
    resp.setTotal(submissions.size());
    resp.setFrom(from == null ? null : from.toLocalDate().toString());
    resp.setTo(to == null ? null : to.toLocalDate().toString());
    resp.setBySource(groupBy(submissions, s -> s.getUtmSource()));
    resp.setByCampaign(groupBy(submissions, s -> s.getUtmCampaign()));
    return resp;
  }

  private List<UtmStatsResponse.DimensionCount> groupBy(
      List<FormSubmission> submissions, java.util.function.Function<FormSubmission, String> dim) {
    return submissions.stream()
        .filter(s -> dim.apply(s) != null && !dim.apply(s).isBlank())
        .collect(Collectors.groupingBy(dim, Collectors.counting()))
        .entrySet()
        .stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .map(
            e -> {
              UtmStatsResponse.DimensionCount c = new UtmStatsResponse.DimensionCount();
              c.setDimension(e.getKey());
              c.setCount(e.getValue());
              return c;
            })
        .toList();
  }

  private void validateForm(Long formId) {
    Form form = formMapper.selectById(formId);
    if (form == null || !"ENABLED".equals(form.getStatus())) {
      throw new BusinessException(ErrorCode.LANDING_FORM_INVALID);
    }
  }

  private LandingPage require(Long id) {
    LandingPage lp = landingPageMapper.selectById(id);
    if (lp == null) {
      throw new BusinessException(ErrorCode.LANDING_PAGE_NOT_FOUND);
    }
    return lp;
  }

  private LandingPageResponse toResponse(LandingPage lp) {
    LandingPageResponse resp = new LandingPageResponse();
    resp.setId(lp.getId());
    resp.setTitle(lp.getTitle());
    resp.setSubtitle(lp.getSubtitle());
    resp.setDescription(lp.getDescription());
    resp.setThemeColor(lp.getThemeColor());
    resp.setFormId(lp.getFormId());
    Form form = formMapper.selectById(lp.getFormId());
    resp.setFormName(form == null ? null : form.getName());
    resp.setEnabled(lp.getEnabled() != null && lp.getEnabled() == 1);
    resp.setVersion(lp.getVersion());
    resp.setCreatedAt(lp.getCreatedAt());
    return resp;
  }
}
