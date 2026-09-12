package com.crm.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 出站地址校验器（FR-G13，T032）。
 *
 * <p><b>为什么这套断言是灰盒（直接驱动 {@code isDeniedTarget}）而不是只走黑盒</b>：本校验器在默认全拒的口径下，
 * 网段判定<b>不改变结论</b>——未列入白名单的地址无论落在哪个网段都会被拒（见 {@link OutboundUrlValidator} 类注释）。因此只用 {@code
 * validate()} 的黑盒断言"被拒"无法证明网段判定真的存在：一个只实现"白名单成员判定"的类能让那些断言全绿。
 * 逐网段枚举这一层，才使"回环／私有／链路本地／云元数据均被拒"成为可核对的清单。
 *
 * <p>{@code SecurityHardeningIT} 覆盖的是黑盒端到端（含创建接口的状态码），两者互补：这里管"判定对不对"，那里管"接没接上"。
 */
class OutboundUrlValidatorTest {

  /** 白名单同时含一个主机名与一个字面私网地址，用于验证两种形态与"白名单压过网段判定"。 */
  private static final OutboundUrlValidator VALIDATOR =
      new OutboundUrlValidator("allowed.example.com,10.9.9.9");

  /** 未配置白名单（默认全拒）。 */
  private static final OutboundUrlValidator DEFAULT_DENY = new OutboundUrlValidator("");

  @ParameterizedTest(name = "被拒网段：{0}")
  @ValueSource(
      strings = {
        // IPv4 回环与"本网络"
        "127.0.0.1",
        "127.1.2.3",
        "0.0.0.0",
        // IPv4 私有
        "10.1.2.3",
        "172.16.0.9",
        "172.31.255.255",
        "192.168.1.10",
        // 链路本地（含云元数据端点）
        "169.254.1.1",
        "169.254.169.254",
        // 运营商级 NAT
        "100.64.0.1",
        "100.127.255.255",
        // 协议专用与基准测试
        "192.0.0.1",
        "198.18.0.1",
        "198.19.255.255",
        // 组播／保留／广播
        "224.0.0.1",
        "240.0.0.1",
        "255.255.255.255",
        // IPv6 回环／未指定／链路本地／唯一本地／组播
        "::1",
        "::",
        "fe80::1",
        "fc00::1",
        "fd12:3456::1",
        "ff02::1",
        // IPv4 内嵌形式（解析器折成 Inet4Address 后走 IPv4 规则）
        "::ffff:127.0.0.1",
        "::ffff:169.254.169.254",
        // 本机名（含大小写与结尾点两种写法）
        "localhost",
        "LOCALHOST.",
        "a.localhost",
        "ip6-localhost"
      })
  @DisplayName("回环／私有／链路本地／云元数据网段均判为不可出站（FR-G13）")
  void deniedRangesAreDenied(String host) {
    assertTrue(VALIDATOR.isDeniedTarget(host), host + " 应判为不可出站目标");
  }

  @ParameterizedTest(name = "不在被拒网段：{0}")
  @ValueSource(
      strings = {
        "8.8.8.8",
        "1.1.1.1",
        "11.0.0.1", // 10/8 之外
        "172.32.0.1", // 172.16/12 之外
        "172.15.255.255", // 172.16/12 之外（下界）
        "192.169.1.1", // 192.168/16 之外
        "100.63.0.1", // 100.64/10 之外（下界）
        "100.128.0.1", // 100.64/10 之外（上界）
        "198.17.255.255", // 198.18/15 之外
        "2001:4860:4860::8888" // 公网 IPv6
      })
  @DisplayName("公网地址不落在被拒网段（避免判定过宽把正常出站也拦掉）")
  void publicAddressesAreNotDenied(String host) {
    assertFalse(VALIDATOR.isDeniedTarget(host), host + " 不应判为不可出站目标");
  }

  /**
   * 普通主机名在此层<b>不</b>判为拒绝——它有可能是白名单内的合法目标，网段判定只认字面地址与本机名。
   *
   * <p>这一条钉住的是"网段判定不得越界到名字"，否则白名单里写主机名就永远放行不了。
   */
  @Test
  @DisplayName("普通主机名不由网段判定拒绝（否则白名单里的主机名将永远无法放行）")
  void ordinaryHostNamesAreNotDeniedAtRangeLayer() {
    assertFalse(VALIDATOR.isDeniedTarget("allowed.example.com"));
    assertFalse(VALIDATOR.isDeniedTarget("internal.corp"));
  }

  @ParameterizedTest(name = "非 http(s) 协议被拒：{0}")
  @ValueSource(
      strings = {"file:///etc/passwd", "ftp://host/x", "jar:file:///x!/y", "gopher://host/1"})
  @DisplayName("出站地址仅允许 http/https（FR-G13）")
  void nonHttpSchemesAreRejected(String url) {
    assertThrows(
        BusinessException.class, () -> VALIDATOR.validate(url, ErrorCode.OPEN_WEBHOOK_URL_INVALID));
  }

  @Test
  @DisplayName("白名单内的地址被放行，且压过网段判定（FR-G13：白名单是合法内网集成的显式出口）")
  void whitelistedHostsAreAllowed() {
    assertDoesNotThrow(() -> VALIDATOR.validate("http://10.9.9.9/hook", onReject()));
    assertDoesNotThrow(() -> VALIDATOR.validate("http://allowed.example.com/hook", onReject()));
    // 大小写与结尾点不影响匹配
    assertDoesNotThrow(() -> VALIDATOR.validate("http://ALLOWED.example.com./hook", onReject()));
  }

  @Test
  @DisplayName("默认全拒：未配置白名单时，连公网地址也不放行（data-model.md §4）")
  void defaultDenyRejectsEverything() {
    assertThrows(
        BusinessException.class,
        () -> DEFAULT_DENY.validate("http://allowed.example.com/hook", onReject()));
    assertThrows(
        BusinessException.class, () -> DEFAULT_DENY.validate("http://8.8.8.8/hook", onReject()));
  }

  @Test
  @DisplayName("白名单外的主机被拒，且拒绝理由区分「内网目标」与「不在白名单」（决定该控制可否被运维理解）")
  void rejectionReasonDistinguishesRangeFromWhitelist() {
    BusinessException range =
        assertThrows(
            BusinessException.class, () -> VALIDATOR.validate("http://127.0.0.1/hook", onReject()));
    assertTrue(message(range).contains("网段"), "内网目标的拒绝理由应指明网段，实际：" + message(range));

    BusinessException notListed =
        assertThrows(
            BusinessException.class, () -> VALIDATOR.validate("http://8.8.8.8/hook", onReject()));
    assertTrue(message(notListed).contains("白名单"), "白名单外的拒绝理由应指明白名单，实际：" + message(notListed));
  }

  @Test
  @DisplayName("凭据信息（userInfo）不得成为绕过手段：取的是 URI 的主机部分，不是字符串切分")
  void userInfoCannotBypassHostCheck() {
    // 主机是 evil.com（不在白名单）→ 必须以"不在白名单"为由拒绝，而不是被当成 169.254.169.254 以外的什么
    BusinessException a =
        assertThrows(
            BusinessException.class,
            () -> VALIDATOR.validate("http://169.254.169.254@evil.com/hook", onReject()));
    assertTrue(message(a).contains("白名单"), "应按主机 evil.com 判定，实际：" + message(a));

    // 主机是元数据地址 → 必须以"网段"为由拒绝
    BusinessException b =
        assertThrows(
            BusinessException.class,
            () -> VALIDATOR.validate("http://evil.com@169.254.169.254/hook", onReject()));
    assertTrue(message(b).contains("网段"), "应按主机 169.254.169.254 判定，实际：" + message(b));
  }

  @Test
  @DisplayName("十进制等非点分写法的 IP 同样被拒（默认全拒不依赖能否识别出它的真实网段）")
  void nonDottedIpEncodingsAreStillRejected() {
    assertThrows(
        BusinessException.class, () -> VALIDATOR.validate("http://2130706433/hook", onReject()));
  }

  @Test
  @DisplayName("缺少主机名或地址为空的输入被拒")
  void malformedUrlsAreRejected() {
    assertThrows(BusinessException.class, () -> VALIDATOR.validate("http:///hook", onReject()));
    assertThrows(BusinessException.class, () -> VALIDATOR.validate("", onReject()));
    assertThrows(BusinessException.class, () -> VALIDATOR.validate(null, onReject()));
    assertThrows(BusinessException.class, () -> VALIDATOR.validate("not a url", onReject()));
  }

  @Test
  @DisplayName("重定向：相对地址解析后仍在校验过的主机上，故放行；绝对地址必须重新过一遍校验")
  void redirectsAreValidatedPerHop() {
    URI current = URI.create("http://allowed.example.com/a");

    URI relative = VALIDATOR.resolveRedirect(current, "/b", onReject());
    assertEquals("http://allowed.example.com/b", relative.toString());

    // 指向云元数据端点 → 拒
    assertThrows(
        BusinessException.class,
        () -> VALIDATOR.resolveRedirect(current, "http://169.254.169.254/x", onReject()));

    // 指向白名单外的公网主机 → 拒（默认全拒对重定向同样成立）
    assertThrows(
        BusinessException.class,
        () -> VALIDATOR.resolveRedirect(current, "http://other.example.com/x", onReject()));

    // 缺少 Location → 拒
    assertThrows(
        BusinessException.class, () -> VALIDATOR.resolveRedirect(current, "  ", onReject()));
  }

  private ErrorCode onReject() {
    return ErrorCode.OPEN_WEBHOOK_URL_INVALID;
  }

  private String message(BusinessException ex) {
    String message = ex.getMessage();
    assertNotNull(message, "拒绝必须带可读理由，否则运维无从判断该改哪一项配置");
    return message;
  }
}
