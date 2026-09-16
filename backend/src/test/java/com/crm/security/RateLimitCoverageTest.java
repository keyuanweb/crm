package com.crm.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 限流覆盖台账（100-rate-limit-consolidation，FR-030~FR-032）。
 *
 * <p><b>防的是什么</b>：{@link RateLimit} 是 <b>opt-in</b> 注解 —— 一个新增端点若忘了标注，它就没有限流，
 * 而且<b>没有任何用例会红</b>（没有限流本身不产生失败）。这与 {@link RequirePermission} 当年的处境一样，处置办法也一样：
 * 用<b>字节码扫描台账</b>把「默认拒绝」补回来——每个端点必须**要么**有 {@link RateLimit}，**要么**在豁免清单里， 两者都没有即失败。
 *
 * <p>扫描手法与 {@code RequirePermissionScanTestSupport} 一致（{@link MetadataReader} 一次遍历，拿到类 + 被标注的
 * 方法）。这里只查 {@code @RequestMapping} 一种：Spring 的 {@code getAnnotatedMethods} 会**沿元注解上溯**， 而
 * {@code @GetMapping}/{@code @PostMapping} 等六个都以 {@code @RequestMapping} 为元注解 ⇒ 查它就等于查到全部端点，
 * 且能拿到合并后的 {@code path} 与 {@code method} 属性（已实测）。
 *
 * <p><b>豁免的两级结构</b>（{@link #EXEMPT} 显式条目 + {@link #RULES} 具名规则）：
 *
 * <ul>
 *   <li><b>显式条目</b>：{@code 类#方法 → 理由}，粒度必须到方法（**不是 URI 前缀**：{@code /api/v1/public/**} 内部
 *       风险差一个量级，且其中两条已限流，用前缀会「一放一大片」），理由**非空**（无理由判失败，防「随手加一行让测试变绿」）。
 *   <li><b>具名规则</b>：承载 FR-029 里数量大、判断一致的那几类（已认证的常规读/写接口）。每条规则自带非空理由， 且断言**命中数 &gt;
 *       0**（规则写错成永假同样是护栏失效）。
 * </ul>
 *
 * <p>⚠️ <b>实做订正（2026-09-17，逐字保留 FR-031 原文）</b>：FR-031 写道「③ FR-029 的**逐条**豁免」并列举 初始四类。实做清点后：全仓端点
 * <b>3xx 个</b>，其中「已认证的常规读接口」一类就占一百多 ⇒ 逐条手写既不可读、也会让「理由」栏退化成一百多行同样的字——那正是 FR-031 自己
 * 要防的形态。故实做为：**需要判断的有限集**逐条列（{@link #EXEMPT}），**判断一致的两大类**由具名规则承载 （{@link
 * #RULES}），规则的**结构**本身就排除了匿名面与开放 API 面。另：FR-031 ①「{@code EmailTrackController} 两个」 <b>不需要条目</b>——C2
 * 实做把 {@code @RateLimit} 挂在了两个 handler 上（而不是留在私有方法里）， 它们是**已标注**端点；{@link
 * #everyExemptionEntryIsLiveAndNotAnnotated} 会把这类「已被注解覆盖、却还留在豁免表里」 的条目判红，所以这里刻意不抄那一行。
 *
 * <p><b>为什么规则里看不见「响应是不是文件字节」</b>：{@code MetadataReader} 的 {@code getReturnTypeName()} 是**擦除后**的
 * 名字（{@code ResponseEntity<ByteArrayResource>} 与 {@code ResponseEntity<Void>} 都读作 {@code
 * org.springframework.http.ResponseEntity}，已实测）。故文件型端点靠「返回类型不是 {@code ApiResponse}」这条
 * 侧面判据兜住：常规读接口的返回类型在本仓是 {@code ApiResponse}，凡是偏离它的读端点（下载/模板/附件流/ ByteArrayResource）一律要求显式条目——本批 13
 * 个导出里 9 个正是这种形状，这条判据就是为它们 以及将来同形状的新端点设的。
 */
class RateLimitCoverageTest {

  private static final String REQUEST_MAPPING = RequestMapping.class.getName();
  private static final String REQUIRE_PERMISSION = RequirePermission.class.getName();
  private static final String API_RESPONSE = "com.crm.common.ApiResponse";

  /** 匿名可达面（FR-029：这些端点的滥用面与已认证端点不是一个量级，永不自动豁免）。 */
  private static final String ANON_PREFIX = "/api/v1/public/";

  /** 开放 API 面（055：X-API-Key 主体，零限制是它改造前的状态，本批已接）——同上，永不自动豁免。 */
  private static final String OPEN_API_PREFIX = "/api/v1/open/";

  /**
   * 需要逐条判断的豁免：{@code 类#方法 → 理由}（理由非空是硬要求）。
   *
   * <p>分四组：① 限流在服务内部的端点；② 登录的 2 层失败计数（用户裁决：不加限流，语义不同）； ③ 2FA（全仓唯一 fail-close 边界）；④ P1 待接入（本批 C6
   * 标注后，这些条目必须删除—— 否则 {@link #everyExemptionEntryIsLiveAndNotAnnotated} 会因「条目已被注解覆盖」判红）； ⑤ 返回
   * {@code ResponseEntity} 而非 {@code ApiResponse} 的常规读接口（本仓少数派风格，见类注释末段）。
   */
  private static final Map<String, String> EXEMPT = exempt();

  private static Map<String, String> exempt() {
    Map<String, String> map = new LinkedHashMap<>();

    // ① 限流在服务层内部（不是漏了，是挂在了别处）
    map.put(
        "FormController#submit",
        "限流在 FormService#submit 内部（RateLimiter.checkIp(\"public-form-submit\", 3, 60, ip)）："
            + "被限流的单位是服务方法、且 IP 已解析，不经过切面；阈值/窗口与改造前逐字相同。");

    // ② 登录的 2 层失败计数（017：用户名 5 次/15 分钟 + IP 10 次/15 分钟）
    map.put(
        "AuthController#login",
        "用户裁决（2026-09-16）：不给登录加限流。它已有三层防护（验证码始终要求 + 两层失败计数），"
            + "且失败计数是「直到解锁为止」的语义，与限流「窗口内容量」不同，叠加会让正常用户被误伤。");

    // ③ 2FA 两处（MfaStateStore，全仓唯一 fail-close 边界）
    map.put(
        "AuthController#verifyMfa",
        "2FA 校验走 MfaStateStore（fail-close，语义不可移植到限流）：锁定是「验证失败即锁」，"
            + "而限流是 fail-open 的旁路控制，两者混用会让 Redis 抖动时的行为不可预测。");
    map.put(
        "UserController#resetMfa", "管理员重置某用户的 2FA（082 FR-M10）：与上一条同属 MfaStateStore 的锁定语义，非请求速率。");

    // ④ P1 待接入（C6 = T041 标注 public-read 60/60s 后，这四条必须从本表删除）
    map.put(
        "FormController#meta",
        "**P1 待接入**（T041：scope=public-read 60/60s，by=IP，C6 标注）。匿名 + 打 DB，"
            + "但无写副作用、非凭证端点 ⇒ 排在 P0 之后；本条是过渡态，C6 标注后必须删除。");
    map.put(
        "LandingPageController#publicView",
        "**P1 待接入**（T041：scope=public-read 60/60s，by=IP，C6 标注）。落地页是爬虫与预取器都会打的路径，"
            + "但同样无写副作用 ⇒ 排在 P0 之后；本条是过渡态，C6 标注后必须删除。");
    map.put(
        "CustomerPortalController#articles",
        "**P1 待接入**（T041：scope=public-read 60/60s，by=IP，C6 标注）。匿名只读文章列表；" + "本条是过渡态，C6 标注后必须删除。");
    map.put(
        "CustomerPortalController#article",
        "**P1 待接入**（T041：scope=public-read 60/60s，by=IP，C6 标注）。匿名只读文章详情；" + "本条是过渡态，C6 标注后必须删除。");

    // ⑤ 返回 ResponseEntity 的常规读接口（非文件字节：数据保留策略 / 定时导出任务）
    map.put(
        "DataRetentionPolicyController#getAllPolicies",
        "常规读接口，但返回 ResponseEntity 而非 ApiResponse（本仓少数派风格，080 的写法）——"
            + "无写副作用、无文件字节，属 FR-029 ③ 的同一类。");
    map.put("DataRetentionPolicyController#getPolicy", "同上（数据保留策略详情，只读）。");
    map.put("DataRetentionPolicyController#getExecutions", "同上（执行记录列表，只读）。");
    map.put(
        "ScheduledExportController#getScheduledExports",
        "常规读接口，返回 ResponseEntity（079 的写法）——此处读的是任务定义，不跑导出；"
            + "真跑导出的是本类的 #createScheduledExport / #executeNow，两条都已标注 export-generate。");
    map.put("ScheduledExportController#getScheduledExport", "同上（任务详情，只读）。");
    map.put("ScheduledExportController#getExecutions", "同上（任务执行记录，只读）。");

    // ⑤ 续：SalesQuotaController 整类是同一形状（只读查询 + 返回 ResponseEntity），逐条列出。
    //    为什么值得逐条而不是放宽规则：规则一旦不要求 ApiResponse，本批 13 个导出里那 9 个「GET + 返回
    //    文件字节」的端点（模板/附件/导出流）以及将来同形状的新端点就会一并被自动放行 —— 那正是台账要防的。
    String readOnlyResponseEntity =
        "常规读接口，但返回 ResponseEntity 而非 ApiResponse（SalesQuotaController 的写法，"
            + "该类全部端点只读、不回文件字节、无写副作用）⇒ 属 FR-029 ③ 的同一类。";
    map.put("SalesQuotaController#getQuotas", readOnlyResponseEntity);
    map.put("SalesQuotaController#getQuota", readOnlyResponseEntity);
    map.put("SalesQuotaController#getBreakdown", readOnlyResponseEntity);
    map.put("SalesQuotaController#getAchievement", readOnlyResponseEntity);
    map.put("SalesQuotaController#getVersions", readOnlyResponseEntity);
    map.put("SalesQuotaController#getTeamRanking", readOnlyResponseEntity);
    map.put("SalesQuotaController#getSummary", readOnlyResponseEntity);

    // ⑥ 权限码属 export: 族、但不产出文件的任务管理动作（「码以 export: 开头即不自动豁免」这条的副作用）。
    //    它们的成本与常规 CRUD 同级，故显式列在这里；真正跑导出的是 #createScheduledExport / #executeNow，
    //    那两条已挂 export-generate 注解。
    String exportFamilyCrud =
        "定时导出任务的启停/删除：改的是任务定义，不跑导出、不回字节。此处需显式条目的唯一原因是它的权限码"
            + "（export:scheduled）落在「export: 族不得自动豁免」之内 —— 那条规则是为「建任务」形状的 POST 设的。";
    map.put("ScheduledExportController#updateStatus", exportFamilyCrud);
    map.put("ScheduledExportController#deleteScheduledExport", exportFamilyCrud);

    return Map.copyOf(map);
  }

  /**
   * 具名规则：{@code 规则名 → 理由}，命中数必须 &gt; 0（写错成永假 = 护栏静默失效）。
   *
   * <p>两条规则都**结构上**排除匿名面与开放 API 面，且都不覆盖 {@code export:*} 动作码——本批 13 个导出端点里， 4 个「建任务」形状的 POST（{@code
   * ExportController#create} · {@code ComplianceExportController#executeExport} · {@code
   * ScheduledExportController#createScheduledExport} / {@code #executeNow}）返回的是 {@code
   * ApiResponse}，若规则按返回类型放行，它们与将来同形状的新端点就会**悄悄绕过台账**， 所以写规则时额外以「权限码以 {@code export:} 开头 ⇒
   * 不得自动豁免」这条把它们按住。
   */
  private static final Map<String, String> RULES = rules();

  private static Map<String, String> rules() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(
        "已认证常规读接口",
        "FR-029 ③：业务主干读接口，滥用已被数据权限与分页约束。判据：读动词 ∧ 不在 "
            + ANON_PREFIX
            + "／"
            + OPEN_API_PREFIX
            + " 下 ∧ 返回类型是 "
            + API_RESPONSE
            + "（偏离它的读端点一律要求显式条目，见类注释末段）。");
    map.put(
        "已认证常规写接口",
        "FR-029 ③ 的同一类：CRUD/状态机动作，滥用已被权限码与数据权限约束。判据：写动词 ∧ 不在 "
            + ANON_PREFIX
            + "／"
            + OPEN_API_PREFIX
            + " 下 ∧ 声明的权限码不以 export: 开头（导出是「单次成本高 + 无范围过滤」的一类，"
            + "不得进自动豁免——这正是本批要堵的空档）。");
    return Map.copyOf(map);
  }

  /** 自检用的已知答案：这些端点已在 C2/C3/C5 标注过，扫描若没扫到它们说明扫描本身坏了。 */
  private static final Map<String, String> KNOWN_ANNOTATED = knownAnnotated();

  private static Map<String, String> knownAnnotated() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("ExportController#create", "export-generate");
    map.put("ExportController#download", "export-download");
    map.put("CustomerController#exportCustomers", "export-generate");
    map.put("CustomerController#importTemplate", "export-download");
    map.put("ContactController#importTemplate", "export-download");
    map.put("ContractAttachmentController#download", "export-download");
    map.put("LeadController#exportLeads", "export-generate");
    map.put("LeadController#importTemplate", "export-download");
    map.put("QuoteController#pdf", "export-generate");
    map.put("ReportController#export", "export-generate");
    map.put("ComplianceExportController#executeExport", "export-generate");
    map.put("ScheduledExportController#createScheduledExport", "export-generate");
    map.put("ScheduledExportController#executeNow", "export-generate");
    map.put("OpenPlatformController#openCustomers", "open-api-read");
    map.put("OpenPlatformController#openLeads", "open-api-read");
    map.put("OpenPlatformController#openCreateLead", "open-api-write");
    map.put("CustomerPortalController#submitTicket", "public-ticket-submit");
    map.put("CustomerPortalController#ticketStatus", "public-ticket-status");
    map.put("EmailUnsubscribeController#unsubscribe", "public-unsubscribe");
    map.put("EmailTrackController#trackOpen", "public-email-track");
    map.put("EmailTrackController#trackClick", "public-email-track");
    return Map.copyOf(map);
  }

  /**
   * 端点总数下界（自检：扫描 pattern 写错时「零违规」会假绿；先例 {@code RequirePermissionCatalogTest}）。
   *
   * <p>实做基线（2026-09-17，本仓 65 个控制器）：**368** 个端点 —— {@code @RateLimit} 21 · 显式条目 23 · 具名规则 324 （读 121
   * / 写 203）。下界留约 10% 余量：真删掉几十个端点不会误报，而丢掉一整个包（控制器）会。
   */
  private static final int MIN_ENDPOINTS = 330;

  @Test
  @DisplayName("自检：扫描确实扫到了端点，且已标注的那批都在（否则「零违规」是假绿）")
  void scanReallyFindsEndpoints() {
    List<Endpoint> endpoints = scan();

    assertThat(endpoints)
        .as(
            "扫描 %s 只找到 %d 个端点——低于下界 %d，说明扫描本身坏了（不是端点变少了）",
            REQUEST_MAPPING, endpoints.size(), MIN_ENDPOINTS)
        .hasSizeGreaterThanOrEqualTo(MIN_ENDPOINTS);

    Map<String, String> missing = new TreeMap<>();
    KNOWN_ANNOTATED.forEach(
        (key, scope) -> {
          if (endpoints.stream().noneMatch(e -> e.key().equals(key) && e.annotated())) {
            missing.put(key, scope);
          }
        });
    assertThat(missing).as("下列端点应有 @RateLimit（scope 见值）却不在扫描结果里：扫描漏了，或注解被删了").isEmpty();

    // 台账的**基数读数**打出来（surefire 收进 target/surefire-reports/*-output.txt）。
    // 为什么要有这一行：MIN_ENDPOINTS 与类注释里的分桶数字都是「某一时点的实测值」，端点数变化时
    // 需要重新取值 —— 没有这一行就只能事后补一个临时探针（本批就是这么量出 368 的），
    // 而探针用完即删 ⇒ 下一个人量不到。打印一行让「重新取值」变成跑一条命令。
    Map<String, Integer> buckets = new TreeMap<>();
    for (Endpoint endpoint : endpoints) {
      if (endpoint.annotated()) {
        buckets.merge("@RateLimit", 1, (a, b) -> a + b);
        continue;
      }
      String rule = matchedRule(endpoint);
      buckets.merge(
          rule == null ? "未分类（由 everyEndpointIsRateLimitedOrExplicitlyExempt 判红）" : rule,
          1,
          (a, b) -> a + b);
    }
    System.out.println("[RateLimitCoverageTest] 端点总数 = " + endpoints.size() + " " + buckets);
  }

  @Test
  @DisplayName("每个端点要么有 @RateLimit，要么在豁免清单里（否则新增端点等于默认无限流）")
  void everyEndpointIsRateLimitedOrExplicitlyExempt() {
    List<String> unclassified = new ArrayList<>();
    for (Endpoint endpoint : scan()) {
      if (!endpoint.annotated() && classifyWithoutRefusing(endpoint) == null) {
        unclassified.add(endpoint.describe());
      }
    }

    assertThat(unclassified)
        .as(
            "下列端点既没有 @RateLimit 也不在豁免清单里 ⇒ 请二选一：给它标注（scope/limit/windowSeconds/by），"
                + "或在 EXEMPT 里加一条「类#方法 → 理由」。⚠️ 不要用 URI 前缀放行，粒度必须到方法。")
        .isEmpty();
  }

  @Test
  @DisplayName("豁免条目必须指向真实端点、且不能是已被注解覆盖的陈旧条目")
  void everyExemptionEntryIsLiveAndNotAnnotated() {
    Map<String, Endpoint> byKey = new TreeMap<>();
    scan().forEach(e -> byKey.put(e.key(), e));

    Map<String, String> dangling = new TreeMap<>();
    Map<String, String> stale = new TreeMap<>();
    EXEMPT
        .keySet()
        .forEach(
            key -> {
              Endpoint found = byKey.get(key);
              if (found == null) {
                dangling.put(key, "扫描结果里没有这个端点：方法改名/删除后豁免条目没跟着改");
              } else if (found.annotated()) {
                stale.put(key, "该端点已有 @RateLimit，豁免条目是多余的（本批 P1 待接入的条目在其被标注后即属此类）");
              }
            });

    assertThat(dangling).as("豁免清单里的悬空条目（指向不存在的端点）").isEmpty();
    assertThat(stale).as("豁免清单里的陈旧条目（端点已被注解覆盖）⇒ 请从 EXEMPT 删除该行，它已经没有作用了").isEmpty();
  }

  @Test
  @DisplayName("每条豁免理由非空、每条具名规则命中数大于零")
  void everyExemptionCarriesAReasonAndEveryRuleMatchesSomething() {
    List<String> blankReasons = new ArrayList<>();
    EXEMPT.forEach(
        (key, reason) -> {
          if (reason == null || reason.isBlank()) {
            blankReasons.add(key);
          }
        });
    RULES.forEach(
        (rule, reason) -> {
          if (reason == null || reason.isBlank()) {
            blankReasons.add("规则「" + rule + "」");
          }
        });
    assertThat(blankReasons).as("豁免必须带非空理由：无理由的条目就是「随手加一行让测试变绿」，台账也就白设了").isEmpty();

    Map<String, Integer> hits = new TreeMap<>();
    RULES.keySet().forEach(rule -> hits.put(rule, 0));
    for (Endpoint endpoint : scan()) {
      if (endpoint.annotated()) {
        continue;
      }
      String rule = matchedRule(endpoint);
      if (rule != null) {
        hits.merge(rule, 1, (a, b) -> a + b);
      }
    }
    hits.forEach(
        (rule, count) ->
            assertThat(count).as("具名规则「%s」一个端点都没命中：规则写成了永假，等于这条豁免面无人看管", rule).isPositive());
  }

  // ===== 分类 =====

  /** 返回命中的规则名，没有规则命中则返回 {@code null}；{@code EXEMPT} 条目优先于规则。 */
  private static String matchedRule(Endpoint endpoint) {
    if (EXEMPT.containsKey(endpoint.key())) {
      return "显式条目";
    }
    if (endpoint.isUnder(ANON_PREFIX) || endpoint.isUnder(OPEN_API_PREFIX)) {
      return null;
    }
    if (endpoint.isReadVerb() && API_RESPONSE.equals(endpoint.returnType())) {
      return "已认证常规读接口";
    }
    if (!endpoint.isReadVerb() && !endpoint.declaresExportPermission()) {
      return "已认证常规写接口";
    }
    return null;
  }

  private static String classifyWithoutRefusing(Endpoint endpoint) {
    return matchedRule(endpoint);
  }

  // ===== 扫描 =====

  private record Endpoint(
      String key,
      String fullPath,
      String verb,
      String returnType,
      boolean annotated,
      String permissionCode) {

    boolean isReadVerb() {
      return "GET".equals(verb) || "HEAD".equals(verb);
    }

    boolean isUnder(String prefix) {
      return fullPath.startsWith(prefix);
    }

    boolean declaresExportPermission() {
      return permissionCode != null && permissionCode.startsWith("export:");
    }

    String describe() {
      return key + " [" + verb + " " + fullPath + " → " + returnType + "]";
    }
  }

  private static List<Endpoint> scan() {
    MetadataReaderFactory factory = new CachingMetadataReaderFactory();
    List<Endpoint> endpoints = new ArrayList<>();
    Set<String> keys = new TreeSet<>();
    try {
      Resource[] resources =
          new PathMatchingResourcePatternResolver().getResources("classpath*:com/crm/**/*.class");
      for (Resource resource : resources) {
        MetadataReader reader = factory.getMetadataReader(resource);
        AnnotationMetadata metadata = reader.getAnnotationMetadata();
        Map<String, Object> classMapping = metadata.getAnnotationAttributes(REQUEST_MAPPING);
        String simpleName = simpleNameOf(metadata.getClassName());
        for (MethodMetadata method : metadata.getAnnotatedMethods(REQUEST_MAPPING)) {
          Map<String, Object> mapping = method.getAnnotationAttributes(REQUEST_MAPPING);
          if (mapping == null) {
            continue;
          }
          // 同一个方法只应该产出一次：@GetMapping 等会被上溯到 @RequestMapping 而重复出现，
          // 这里按 key 去重（也顺带保证「类#方法」在豁免表里无歧义）。
          String key = simpleName + "#" + method.getMethodName();
          if (!keys.add(key)) {
            continue;
          }
          Map<String, Object> permission = method.getAnnotationAttributes(REQUIRE_PERMISSION);
          endpoints.add(
              new Endpoint(
                  key,
                  classRoute(classMapping) + firstPath(mapping),
                  verbOf(mapping),
                  method.getReturnTypeName(),
                  method.isAnnotated(RateLimit.class.getName()),
                  permission == null ? null : String.valueOf(permission.get("value"))));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("扫描 @RequestMapping 端点失败", e);
    }
    return endpoints;
  }

  /** 类级路由的**第一个**路径（本仓每个控制器恰好一条，见 65 个控制器）。 */
  private static String classRoute(Map<String, Object> classMapping) {
    return classMapping == null ? "" : firstPath(classMapping);
  }

  private static String firstPath(Map<String, Object> mapping) {
    Object value = mapping.get("path");
    if (value instanceof String[] paths && paths.length > 0) {
      return paths[0];
    }
    Object aliased = mapping.get("value");
    if (aliased instanceof String[] values && values.length > 0) {
      return values[0];
    }
    return "";
  }

  /** 无 {@code method} 属性的映射（本仓不存在）一律记作 {@code ANY}，不落入任何自动豁免。 */
  private static String verbOf(Map<String, Object> mapping) {
    Object value = mapping.get("method");
    if (value instanceof RequestMethod[] methods && methods.length == 1) {
      return methods[0].name();
    }
    return "ANY";
  }

  private static String simpleNameOf(String className) {
    return className.substring(className.lastIndexOf('.') + 1);
  }
}
