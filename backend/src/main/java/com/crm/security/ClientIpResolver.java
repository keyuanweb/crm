package com.crm.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 客户端 IP 的解析（100-rate-limit-consolidation）：{@code X-Forwarded-For} 首个地址优先（代理场景）， 否则回退调用方给的
 * fallback（通常是 {@code request.getRemoteAddr()}）。
 *
 * <p><b>它是改造前三份副本的收敛落点</b>，语义取 {@code AuthService.resolveClientIp} 那份（**更严**的那份）。 ⚠️
 * 三份副本在**退化输入**上的差异是实测出来的，与立项期的推测**不同**（该推测已按实测订正，见 tasks.md §实做订正）：
 *
 * <ul>
 *   <li>三份<b>都</b>写 {@code forwarded.split(",")[0].trim()}。Java 的 {@code split} 会丢弃末尾空段， 故 {@code
 *       ","} 切出的是**长度 0 的数组** ⇒ 取 {@code [0]} 抛 {@link ArrayIndexOutOfBoundsException}
 *       （不是返回空串）。这在公开端点上是一条**潜伏的 500**：请求头由调用方任意构造。
 *   <li>{@code ", 1.2.3.4"} 这类输入才产生空串：{@code AuthService} 那份多一层「首段非空」判定 ⇒ 正确回退； 另两份只判整个头非空 ⇒
 *       返回<b>空串</b>，使所有这类请求共用<b>一个键为 {@code ""} 的桶</b> （一个客户端就能把别人的配额用光）。
 *   <li>⇒ 本类用 {@code indexOf(',')} 取首段而不是 {@code split}：三种退化输入一律回退 fallback，
 *       <b>既不返回空串、也不抛异常</b>。这不是"顺手加固"——它同时是「把三份改成一份」这个动作的 **净收益**（收敛处若照抄 split，只是把三份的同一个洞搬进一份）。
 * </ul>
 *
 * <p><b>⚠️ 它的前提与它<b>不是</b>什么（必须与 {@code crm.rate-limit.trust-forwarded-for} 一起读）</b>：
 *
 * <ul>
 *   <li>首段被<b>无条件</b>信任。这只在「反向代理是唯一入口、且代理自己重写 XFF」时成立（生产上 nginx 是唯一入口）。
 *   <li><b>直连后端端口时可以伪造</b>：此时「按 IP 分桶」形同虚设。<b>已知并登记为债务</b>（roadmap 的债务台账）， 修复动作是「可信代理网段白名单 + 从右往左取
 *       XFF」，本批不做。
 *   <li>⇒ 因此公开端点上的 IP 限流<b>不是抗敌手措施</b>，它的定位是<b>误用与意外的阻尼</b>——防一个死循环的前端 把工单表灌满、防一个预取器把落地页打成 DB
 *       热点。<b>不得在任何文档里把它宣传成攻击防护</b>。
 *   <li>{@code crm.rate-limit.trust-forwarded-for=false} 时完全不看 XFF（只用 fallback），供「直连在公网、
 *       前面没有可信代理」的部署使用。
 * </ul>
 */
@Component
public class ClientIpResolver {

  private final boolean trustForwardedFor;

  public ClientIpResolver(
      @Value("${crm.rate-limit.trust-forwarded-for:true}") boolean trustForwardedFor) {
    this.trustForwardedFor = trustForwardedFor;
  }

  /**
   * 解析客户端 IP。
   *
   * @param request 当前请求；为 {@code null} 时直接返回 fallback（调用方据此保留自己的「无请求」字面量）
   * @param fallback 无（可信的）转发头时的回退值
   */
  public String resolve(HttpServletRequest request, String fallback) {
    if (request == null || !trustForwardedFor) {
      return fallback;
    }
    String first = firstForwardedFor(request);
    return first == null ? fallback : first;
  }

  /**
   * <b>只解析、不做信任决策</b>：取 {@code X-Forwarded-For} 的首段并去空白；缺失、为空、退化输入一律返回 {@code null} （调用方自行决定回退成什么）。
   *
   * <p>它是本类与 {@code AuthService.resolveClientIp} 的<b>共用解析器</b>：后者是 {@code static}、在 Spring
   * 容器之外被调用，读不到 {@link #trustForwardedFor}，因此<b>只能</b>用这个静态入口。
   *
   * <p>⚠️ <b>它不读 {@code crm.rate-limit.trust-forwarded-for}</b>，这是<b>刻意的、有代价的</b>选择： {@link
   * #resolve} 是该开关的读取点，而 {@code AuthService} 的登录失败锁定<b>不再</b>受它管辖（该开关只收敛限流侧的 IP 解析）。 后果如实登记：把
   * {@code trust-forwarded-for} 设成 {@code false} 的部署里，<b>登录锁定的分桶仍看 XFF</b>，
   * 与限流侧的口径不一致——改造前登录就一直是这个行为，本批只是没把它一起收进来 （登录的两层锁定按用户裁决不动，改它会动 017 的既有语义）。
   *
   * @return 可用的首段；无可用值时为 {@code null}（<b>不返回空串</b>——空串会让所有畸形请求共用一个空键桶）
   */
  public static String firstForwardedFor(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.isBlank()) {
      return null;
    }
    // 不用 split(",")：对 "," 这类输入它会切出长度 0 的数组并让 [0] 抛 AIOOBE（见类 javadoc）。
    int comma = forwarded.indexOf(',');
    String first = (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
    return first.isBlank() ? null : first;
  }
}
