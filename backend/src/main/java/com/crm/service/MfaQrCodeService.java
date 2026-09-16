package com.crm.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * 绑定页需要的两样东西：{@code otpauth://} 链接与它的二维码 PNG（082，FR-M03，tasks T008）。
 *
 * <p><b>为什么二维码走 PNG 而不是 SVG / 前端库</b>：前端零新增依赖（本批的裁决之一）， 而服务端已经有一条现成的通路——图形验证码就是"服务端出图、以 data URL
 * 内联"（{@code CaptchaService}）。 于是这里照抄那个形态：{@code data:image/png;base64,…}，前端一个 {@code <img
 * src={…}>} 就完事。
 *
 * <p><b>PNG 编码用 JDK 的 {@link ImageIO}，不引 {@code zxing:javase}</b>：那个包提供的 {@code
 * MatrixToImageWriter} 只省下下面那十行像素搬运，代价是给一个鉴权路径再添一个依赖 ——而它做的事（把 {@code BitMatrix} 涂成 {@code
 * BufferedImage}）没有任何需要被复用的复杂度。
 *
 * <p><b>二维码本身不是秘密</b>：它编码的是 {@code otpauth} URI，而那个 URI 里含密钥。所以它的定位与 {@code secret}
 * 完全一样——只在绑定响应里出现一次，不落库、不进日志、不复用为任何接口的入参。
 */
@Service
public class MfaQrCodeService {

  /**
   * 生成图的边长（像素）。
   *
   * <p>固定值而不是按内容长度算：{@code otpauth} URI 的长度只随用户名变化（几十个字符）， 200×200 对 QR 版本 5~8 都留得住一个模块级的余量，认证器 App
   * 在手机屏幕上扫得动。
   */
  private static final int SIZE_PX = 200;

  /** PNG 的魔数前缀：与图形验证码同一个形态，前端两条路径可以共用渲染方式。 */
  private static final String DATA_URL_PREFIX = "data:image/png;base64,";

  /**
   * 拼 {@code otpauth://totp/…} 链接（Key Uri Format）。
   *
   * <p><b>label 与 issuer 都必须 URL 编码</b>，而且是在拼接<b>之前</b>分别编码：URI 的 {@code /} 之后那一段里，{@code :} 是
   * label 的分隔符（{@code issuer:account}）， 若用户名或发行方里出现 {@code :}、{@code ?}、{@code #} 或空格而没有编码， 认证器就会把
   * URI 解析成完全另一个东西（或直接拒绝），而失败现场是"用户扫不上码"—— 排查会从二维码生成查起，而问题在字符串拼接里。
   *
   * <p><b>为什么把 {@code +} 换回 {@code %20}</b>：{@link URLEncoder} 实现的是 {@code
   * application/x-www-form-urlencoded}（空格 → {@code +}），而 {@code +} 只在查询串里等于空格；
   * 在路径段里它是一个<b>字面</b>加号。这是本类唯一一处"用对了库但仍然要手工补一刀"的地方。
   *
   * <p>{@code secret} 不加编码：它在 Base32 字母表内（{@code A-Z} / {@code 2-7}），没有需要转义的字符。
   *
   * @param issuer 认证器 App 里的分组名（{@code crm.security.mfa.issuer}）
   * @param accountName 账号标识，取<b>用户名</b>而不是显示名：显示名可改、可重复，而 App 里的条目 是对着"哪个账号"配的
   * @param secret Base32 密钥
   * @param period 时间步长（秒），必须与 {@code TotpService} 校验时用的一致 —— 不一致会让 App 显示的码在服务器上永远算不出来
   */
  public String otpauthUrl(String issuer, String accountName, String secret, int period) {
    String label = encode(issuer) + ":" + encode(accountName);
    return "otpauth://totp/"
        + label
        + "?secret="
        + secret
        + "&issuer="
        + encode(issuer)
        + "&algorithm=SHA1&digits=6&period="
        + period;
  }

  /**
   * 把任意文本渲染成二维码，返回内联 PNG 的 data URL。
   *
   * <p><b>留白（quiet zone）用 zxing 的默认值 4 个模块</b>，不为了"图更满"去调小它：安静区是二维码
   * 规范的一部分，削掉它会让一部分扫描器（尤其手机在光线不好时）直接识别失败——而失败现场是 "有些人扫得上、有些人扫不上"。
   *
   * @throws IllegalStateException 内容为空、过长（QR 装不下）或 PNG 编码失败。三者都是服务端故障而非用户输入 问题：内容由本服务自己拼，长度只随用户名变化
   */
  public String pngDataUrl(String content) {
    if (content == null || content.isEmpty()) {
      // zxing 对空内容抛的是 IllegalArgumentException("Found empty contents")——一个从第三方栈里冒出来的
      // 消息，读的人看不出这是"本服务拼错了 URL"还是"用户输入的"。转成自己的异常，把归因说清楚。
      // 正常路径上不可达：内容由 otpauthUrl(...) 拼出，最短也是 "otpauth://totp/:" 那一段。
      throw new IllegalStateException("二维码内容为空 —— 调用方没有传入 otpauth 链接");
    }
    BitMatrix matrix;
    try {
      matrix =
          new QRCodeWriter()
              .encode(
                  content,
                  BarcodeFormat.QR_CODE,
                  SIZE_PX,
                  SIZE_PX,
                  // 内容里可能有非 ASCII 用户名，显式声明 UTF-8，不依赖平台默认编码。
                  Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name()));
    } catch (WriterException ex) {
      throw new IllegalStateException("生成二维码失败（内容 " + content.length() + " 字符）", ex);
    }
    BufferedImage image =
        new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < matrix.getHeight(); y++) {
      for (int x = 0; x < matrix.getWidth(); x++) {
        image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", out);
    } catch (IOException ex) {
      // 往 ByteArrayOutputStream 写不会因磁盘/网络失败，这里不可达；仍然抛而不是吞，
      // 否则会变成"绑定页上有一张空白图"这种没人报的故障。
      throw new IllegalStateException("二维码 PNG 编码失败", ex);
    }
    return DATA_URL_PREFIX + Base64.getEncoder().encodeToString(out.toByteArray());
  }

  private static String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
        .replace("+", "%20");
  }
}
