package com.crm.security;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 限流切面（100-rate-limit-consolidation）：拦截标注了 {@link RateLimit} 的方法，按声明的配额计数， 超限抛 {@code
 * RateLimitExceededException}（由 {@code GlobalExceptionHandler} 映射为 429 + {@code Retry-After}）。
 *
 * <p><b>为什么是注解 + AOP 而不是 Servlet 过滤器</b>（本批的结构决策，理由逐条实测过）：
 *
 * <ul>
 *   <li>本仓「按端点声明横切策略」的<b>既有唯一范式</b>就是注解 + AOP（{@code @RequirePermission} + {@code
 *       PermissionAspect}）。新增第二种机制会制造「导出用注解、公开端点为什么用过滤器」这种 <b>没有判据</b>的问题。
 *   <li><b>裸 {@code @Component} 过滤器拿不到登录用户</b>：{@code LoggingFilter} 注册在 Security 链之外， {@code
 *       SecurityContextHolder} 为空。要靠过滤器按用户分桶，必须插进链内且排在鉴权<b>之后</b>——而现有 写法是 {@code SecurityConfig} 的
 *       {@code addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)}，
 *       照抄形状再加一个会落在两个鉴权过滤器<b>之前</b>，于是 {@code currentUserId()} 恒 {@code null} ⇒
 *       所有已认证用户共用匿名桶，<b>限流被静默降级成无效而全部 IT 照样绿</b>。这条顺序契约没有任何护栏。
 *   <li>本批的配额是<b>逐端点</b>的数字（导出 10/60s 与表单 3/60s 差 20 倍），而过滤器的自然形态是 「URI 前缀 →
 *       配额」的集中表——前缀在这里根本不成立（{@code /public/**} 内部风险差一个量级）。
 *   <li>切面在 DispatcherServlet <b>之内</b>抛异常 ⇒ 走 {@code GlobalExceptionHandler} 拿到统一 {@code
 *       ApiResponse} 信封；过滤器要么手写 JSON、要么走空体 401 那一套，会让全仓唯一的 429 与其他错误 <b>不同形</b>。
 *   <li>过滤器唯一的结构性优势是「响应已提交前改状态码」，而本仓<b>全部</b>导出都是非流式 （{@code XSSFWorkbook} + {@code
 *       ByteArrayOutputStream} 先物化进堆，{@code SXSSF}/{@code EasyExcel} 零命中）⇒ 那个优势在本批不存在。
 * </ul>
 *
 * <p><b>⚠️ 注解是 opt-in，这是本机制唯一的系统性弱点</b>：忘了标注 = 端点没有限流，而<b>没有任何用例会变红</b>
 * （不报错、响应不变）。补救不是「更认真地标注」，而是一条字节码扫描台账用例（{@code RateLimitCoverageTest}）：
 * 扫出全部端点，凡未标注又不在「类#方法」粒度的白名单里就判红，且白名单每一项必须带非空理由。
 *
 * <p><b>顺序</b>：{@code @Order(20)}，排在 {@code PermissionAspect}（{@code @Order(10)}）<b>之后</b>——
 * 权限先于限流，未授权者不消耗配额。两者都显式声明序，不依赖「谁先被扫到」。
 */
@Aspect
@Component
@Order(20)
public class RateLimitAspect {

  private final RateLimiter rateLimiter;
  private final boolean enabled;

  public RateLimitAspect(
      RateLimiter rateLimiter, @Value("${crm.rate-limit.enabled:true}") boolean enabled) {
    this.rateLimiter = rateLimiter;
    this.enabled = enabled;
  }

  @Before("@annotation(rateLimit)")
  public void enforce(RateLimit rateLimit) {
    if (!enabled) {
      return;
    }
    rateLimiter.check(rateLimit, currentRequest());
  }

  /**
   * 取当前请求。非 HTTP 线程（{@code @Scheduled}、异步任务、直接调用）返回 {@code null}，此时身份退化成 {@code ip:unknown} ——
   * **仍然在限流**，只是所有这类调用共用一个桶。退化成「不限流」是更坏的选择：它在 客户端行为上完全看不出来（见 {@link RateLimitIdentity}）。
   */
  private HttpServletRequest currentRequest() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return attrs.getRequest();
    }
    return null;
  }
}
