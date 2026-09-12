package com.crm.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 出站地址统一校验器（083-engineering-consolidation FR-G13，T032）。
 *
 * <p><b>解决的问题</b>：webhook 回调与集成通道的地址由用户填写、由服务端发起请求。改造前集成通道只判 {@code startsWith("http://")}、webhook
 * 则完全不判，于是任何能创建这两类配置的账号都可以让服务端去请求 {@code http://169.254.169.254/…}（云元数据，可取实例凭据）、{@code
 * http://127.0.0.1:8081/…}（本机其他服务）等地址—— 即服务端请求伪造（SSRF）。
 *
 * <p><b>判定顺序（三段，白名单优先）</b>：
 *
 * <ol>
 *   <li><b>协议</b>：仅 {@code http}／{@code https}，其余一律拒（含 {@code file:}／{@code ftp:}／{@code jar:}）。
 *   <li><b>显式白名单</b>：主机名或地址命中 {@code crm.outbound.allowed-hosts} → <b>放行并结束判定</b>。
 *   <li><b>网段判定</b>：命中回环／私有／链路本地／保留网段 → 以"内网目标"为由拒绝；否则以"不在白名单内"为由拒绝。
 * </ol>
 *
 * <p><b>为什么白名单优先于网段判定</b>：FR-G13 要求白名单是"合法内网集成的显式出口"。若网段判定优先，则白名单永远无法放行私网地址，
 * 而"拒绝私网"与"支持合法内网集成"这两个要求就会互相矛盾。白名单来自<b>部署方的环境变量</b>，是可信输入渠道，不是攻击面。
 *
 * <p><b>默认全拒</b>：{@code crm.outbound.allowed-hosts} 留空即不放行<b>任何</b>出站目标，公网地址同样不放行（data-model.md
 * §4）。 因此第 3 段的网段判定在默认配置下<b>不改变结论</b>——它改变的是<b>拒绝的理由</b>。这不是冗余：理由决定了该控制是否可用
 * （运维看到"目标是内网地址"会去白名单里补内网主机，看到"不在白名单内"才知道原来出站默认是关的）， 也是 FR-G13 显式列出的判定类别在代码中的落点（手工验证 SC-G05
 * 正是按目标类别核对）。
 *
 * <p><b>为何不做 DNS 解析（残余风险的边界，须如实知悉）</b>：解析主机名并检查解析结果，只能拦住"未列入白名单的名字"，而那类名字
 * <b>本来就已被默认全拒</b>，故解析不改变任何结论；反而会让这项安全判定依赖 DNS 可用性。真正解析才拦得住的情形是 <b>白名单内的主机名其解析结果指向内网</b>（DNS
 * 重绑定），本类<b>不覆盖</b>该情形——白名单是部署方的显式声明。 需要在拉长的时间窗内防这条，应白名单<b>字面地址</b>而非名字。此边界在此记录，不靠"我们校验了出站地址"一句话掩盖。
 *
 * <p><b>重定向</b>：本类提供 {@link #resolveRedirect}，但覆盖重定向的前提是调用方<b>不自动跟随 3xx</b>——HTTP 客户端默认跟随重定向，一条
 * {@code 302 Location: http://169.254.169.254/…} 就能绕开创建时的校验。故接入方必须关闭自动跟随， 并对每一跳调用本类（见 {@code
 * WebhookDeliverer}）。
 */
@Component
public class OutboundUrlValidator {

  /** 白名单配置项；留空表示默认全拒（data-model.md §4）。 */
  public static final String ALLOWED_HOSTS_PROPERTY = "crm.outbound.allowed-hosts";

  private final Set<String> allowedHosts;

  public OutboundUrlValidator(
      @Value("${" + ALLOWED_HOSTS_PROPERTY + ":}") String allowedHostsConfig) {
    this.allowedHosts = parseAllowedHosts(allowedHostsConfig);
  }

  /**
   * 校验一个出站地址；不合法即抛例外。
   *
   * <p>{@code onReject} 由调用方给出，使各业务域沿用<b>既有</b>的错误码（回调地址用 {@code OPEN_WEBHOOK_URL_INVALID}、 集成通道用
   * {@code INTEGRATION_URL_INVALID}），不因"统一校验"而把既有错误码改掉——调用方据此可区分是哪类配置出的问题。
   */
  public void validate(String url, ErrorCode onReject) {
    URI uri = parse(url, onReject);

    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw reject(onReject, "出站地址仅支持 http/https");
    }

    String host = normalize(uri.getHost());
    if (host.isEmpty()) {
      throw reject(onReject, "出站地址缺少主机名");
    }
    if (allowedHosts.contains(host)) {
      return;
    }
    if (isDeniedTarget(host)) {
      throw reject(onReject, "出站目标 " + host + " 属于回环／私有／链路本地／保留网段，不允许出站");
    }
    throw reject(onReject, "出站目标 " + host + " 不在白名单内（" + ALLOWED_HOSTS_PROPERTY + " 默认全拒）");
  }

  /**
   * 解析重定向的下一跳并校验之，返回可继续请求的绝对地址。
   *
   * <p>相对地址（如 {@code Location: /next}）按当前地址解析后仍落在<b>同一个已校验过的主机</b>上，故此处的再校验是幂等的； 绝对地址才是需要拦的对象。
   *
   * @param current 当前请求的绝对地址（须是已通过 {@link #validate} 的地址）
   * @param location 响应头 {@code Location} 的原始值
   */
  public URI resolveRedirect(URI current, String location, ErrorCode onReject) {
    if (location == null || location.isBlank()) {
      throw reject(onReject, "重定向缺少 Location");
    }
    URI target;
    try {
      target = current.resolve(location.trim());
    } catch (IllegalArgumentException ex) {
      throw reject(onReject, "重定向地址无法解析");
    }
    validate(target.toString(), onReject);
    return target;
  }

  /**
   * 主机是否落在被拒网段。
   *
   * <p>包级可见是为了让单元测试能逐网段枚举（{@code OutboundUrlValidatorTest}）——网段判定在默认全拒下不改变结论，
   * 因此只能直接驱动这一层才测得到，灰盒比黑盒更能说明它确实在判定。
   */
  boolean isDeniedTarget(String rawHost) {
    String host = normalize(rawHost);
    if (host.isEmpty()) {
      return true;
    }
    if (isLocalName(host)) {
      return true;
    }
    if (isIpv4Literal(host)) {
      return isDeniedIpv4(host);
    }
    if (isIpv6Literal(host)) {
      return isDeniedIpv6(host);
    }
    // 普通主机名：此层不判定（见类注释"为何不做 DNS 解析"），交由白名单决定。
    return false;
  }

  // ===== 内部判定 =====

  private URI parse(String url, ErrorCode onReject) {
    if (url == null || url.isBlank()) {
      throw reject(onReject, "出站地址不能为空");
    }
    try {
      return new URI(url.trim());
    } catch (URISyntaxException ex) {
      throw reject(onReject, "出站地址格式不合法");
    }
  }

  /**
   * 归一化主机：转小写、去 IPv6 方括号、去名称结尾的点。
   *
   * <p>取的是 {@link URI#getHost()} 而非自行切分字符串：它已把 {@code userInfo} 与端口摘掉，故 {@code
   * http://169.254.169.254@evil.com/} 得到 {@code evil.com}、{@code http://evil.com@169.254.169.254/}
   * 得到 {@code 169.254.169.254}——自行按"@ 之后"或"第一个斜杠之后"切分正是这类绕过常踩的坑。
   */
  private String normalize(String host) {
    if (host == null) {
      return "";
    }
    String h = host.trim().toLowerCase(Locale.ROOT);
    if (h.startsWith("[") && h.endsWith("]")) {
      h = h.substring(1, h.length() - 1);
    }
    if (h.endsWith(".")) {
      h = h.substring(0, h.length() - 1);
    }
    return h;
  }

  /** 本机名。{@code *.localhost} 按 RFC 6761 亦保留给本机。 */
  private boolean isLocalName(String host) {
    return "localhost".equals(host)
        || host.endsWith(".localhost")
        || "localhost.localdomain".equals(host)
        || "ip6-localhost".equals(host)
        || "ip6-loopback".equals(host);
  }

  private boolean isIpv4Literal(String host) {
    if (host.chars().filter(ch -> ch == '.').count() != 3) {
      return false;
    }
    for (String part : host.split("\\.", -1)) {
      if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
        return false;
      }
    }
    return true;
  }

  private boolean isDeniedIpv4(String host) {
    String[] parts = host.split("\\.", -1);
    int a = Integer.parseInt(parts[0]);
    int b = Integer.parseInt(parts[1]);
    int c = Integer.parseInt(parts[2]);
    if (a == 0) {
      return true; // 0.0.0.0/8「本网络」
    }
    if (a == 10) {
      return true; // 10.0.0.0/8 私有
    }
    if (a == 127) {
      return true; // 127.0.0.0/8 回环
    }
    if (a == 169 && b == 254) {
      return true; // 169.254.0.0/16 链路本地（含 169.254.169.254 云元数据端点）
    }
    if (a == 172 && b >= 16 && b <= 31) {
      return true; // 172.16.0.0/12 私有
    }
    if (a == 192 && b == 168) {
      return true; // 192.168.0.0/16 私有
    }
    if (a == 100 && b >= 64 && b <= 127) {
      return true; // 100.64.0.0/10 运营商级 NAT
    }
    if (a == 192 && b == 0 && c == 0) {
      return true; // 192.0.0.0/24 IETF 协议专用
    }
    if (a == 198 && (b == 18 || b == 19)) {
      return true; // 198.18.0.0/15 基准测试
    }
    return a >= 224; // 224.0.0.0/4 组播 + 240.0.0.0/4 保留 + 广播
  }

  private boolean isIpv6Literal(String host) {
    if (!host.contains(":")) {
      return false;
    }
    // 只认十六进制、冒号与点（IPv4 内嵌形式），避免把普通名字交给解析器触发 DNS
    return host.chars().allMatch(ch -> Character.digit(ch, 16) >= 0 || ch == ':' || ch == '.');
  }

  private boolean isDeniedIpv6(String host) {
    java.net.InetAddress addr;
    try {
      // 冒号形式按字面量解析，不查 DNS；解析失败即当作非法目标
      addr = java.net.InetAddress.getByName(host);
    } catch (java.net.UnknownHostException ex) {
      return true;
    }
    if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()) {
      return true; // ::1 / ::
    }
    if (addr.isLinkLocalAddress()) {
      return true; // fe80::/10
    }
    if (addr.isSiteLocalAddress()) {
      return true; // fec0::/10（已废弃的站点本地）
    }
    if (addr.isMulticastAddress()) {
      return true; // ff00::/8
    }
    byte[] bytes = addr.getAddress();
    if (bytes.length == 4) {
      // IPv4 内嵌形式（::ffff:a.b.c.d）——解析器会折成 Inet4Address，按 IPv4 规则复用同一判定
      return isDeniedIpv4(addr.getHostAddress());
    }
    return (bytes[0] & 0xfe) == 0xfc; // fc00::/7 唯一本地地址
  }

  private Set<String> parseAllowedHosts(String config) {
    Set<String> hosts = new LinkedHashSet<>();
    if (config == null) {
      return hosts;
    }
    Arrays.stream(config.split(","))
        .map(this::normalize)
        .filter(h -> !h.isEmpty())
        .forEach(hosts::add);
    return hosts;
  }

  private BusinessException reject(ErrorCode onReject, String reason) {
    return new BusinessException(onReject, reason);
  }
}
