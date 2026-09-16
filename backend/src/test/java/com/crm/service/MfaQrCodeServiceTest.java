package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MfaQrCodeService} 的纯逻辑用例（082 第 8 步）：otpauth 链接的形状，以及"这张图真的能被扫出那段文本"。
 *
 * <p><b>为什么要把 PNG 解回来</b>：只断言"以 {@code data:image/png;base64,} 开头、是合法 PNG、边长 200" 的用例，在一张<b>全白</b>的
 * 200×200 PNG 上同样全绿——而那意味着每个用户都扫不上码， 且失败现场（"App 说二维码无效"）离"像素画反了"很远。本机没有认证器 App， 所以用 zxing 自己的
 * {@link QRCodeReader} 把图解回文本，与拼进去的那串逐字比。
 *
 * <p>用 {@link RGBLuminanceSource}（在 {@code core} 里）而不是 {@code javase} 的 {@code
 * BufferedImageLuminanceSource}：本批刻意不引 {@code javase}（见 {@code MfaQrCodeService} 的
 * javadoc），测试若为了解码把它拉进 test 路径，就等于那条"只引 core"的界线只对主代码成立。
 */
class MfaQrCodeServiceTest {

  private final MfaQrCodeService service = new MfaQrCodeService();

  private static final String SECRET = "JBSWY3DPEHPK3PXP";

  @Test
  @DisplayName("otpauth 链接的形状：label 是 issuer:account，且 algorithm/digits/period 与校验侧一致")
  void otpauthUrlShape() {
    String url = service.otpauthUrl("CRM", "alice", SECRET, 30);

    assertThat(url).startsWith("otpauth://totp/");
    assertThat(url).contains("?secret=" + SECRET);
    assertThat(url).contains("&issuer=CRM");
    // 这三项必须与 TotpService 校验时用的算法/位数/步长一致；不一致的后果是"App 显示的码服务器永远算不出来"。
    assertThat(url).contains("algorithm=SHA1");
    assertThat(url).contains("digits=6");
    assertThat(url).contains("period=30");
    assertThat(url)
        .isEqualTo(
            "otpauth://totp/CRM:alice?secret="
                + SECRET
                + "&issuer=CRM&algorithm=SHA1&digits=6&period=30");
  }

  @Test
  @DisplayName("issuer 与账号在拼接前被分别编码：空格是 %20 不是 +，保留字符不破坏 URI 结构")
  void issuerAndAccountAreEncodedBeforeConcatenation() {
    // 用户名里出现 `:` 而不编码，URI 的 label 就从 "issuer:account" 变成三段，App 会把后面的当成别的东西。
    String url = service.otpauthUrl("My CRM Co", "a:b&c=d", SECRET, 30);

    assertThat(url).contains("/My%20CRM%20Co:a%3Ab%26c%3Dd?");
    // `+` 只在查询串里等于空格，在路径段里是字面加号 —— URLEncoder 给的是表单编码，必须换回 %20。
    assertThat(url).doesNotContain("My+CRM");
    assertThat(url).contains("&issuer=My%20CRM%20Co");
    // 密钥不加编码：它在 Base32 字母表内，不该被改动一个字符 —— 它一旦被转义，App 装进去的就是另一把密钥。
    assertThat(url).contains("?secret=" + SECRET + "&");
  }

  @Test
  @DisplayName("data URL 前缀与图形验证码同形态，内容是边长 200 的真 PNG")
  void pngDataUrlIsAnInlinePng() throws Exception {
    String dataUrl = service.pngDataUrl("otpauth://totp/x?secret=" + SECRET);

    assertThat(dataUrl).startsWith("data:image/png;base64,");
    byte[] png = Base64.getDecoder().decode(dataUrl.substring("data:image/png;base64,".length()));
    // PNG 魔数：证明这是 PNG，而不是"能存成文件但打开报损坏"的字节。
    assertThat(png).startsWith(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    assertThat(image).isNotNull();
    assertThat(image.getWidth()).isEqualTo(200);
    assertThat(image.getHeight()).isEqualTo(200);
    // 非 ASCII 用户名下二维码里必须有真实的黑模块：整图单色说明内容为空或渲染没发生。
    assertThat(service.pngDataUrl("x")).isNotEqualTo(service.pngDataUrl("y"));
  }

  @Test
  @DisplayName("二维码可被解回原文（含中文用户名），即用户手机扫出来的就是这串 otpauth URI")
  void pngDecodesBackToTheExactContent() throws Exception {
    String content = service.otpauthUrl("CRM", "张三", SECRET, 30);

    byte[] png =
        Base64.getDecoder()
            .decode(service.pngDataUrl(content).substring("data:image/png;base64,".length()));
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    int width = image.getWidth();
    int height = image.getHeight();
    int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
    LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
    Result decoded = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)));

    assertThat(decoded.getText()).isEqualTo(content);
  }

  @Test
  @DisplayName("同一内容两次生成逐字节相同（没有把随机数混进渲染）")
  void renderingIsDeterministic() {
    String text = "otpauth://totp/CRM:alice?secret=" + SECRET;

    assertThat(service.pngDataUrl(text)).isEqualTo(service.pngDataUrl(text));
  }

  @Test
  @DisplayName("空内容被拒且报的是**本服务的**IllegalStateException，不是 zxing 的 IllegalArgumentException")
  void emptyContentIsRejectedWithOurOwnException() {
    // 这条断言是实测得来的：zxing 的 QRCodeWriter 对空内容抛 IllegalArgumentException("Found empty contents")，
    // 而不是 WriterException —— 于是它会绕过 pngDataUrl 里那个 catch 直接冒到调用方，
    // 消息里也看不出这是"本服务拼错了 URL"还是"用户输入的"。故在入口处显式拦成自己的异常。
    assertThatThrownBy(() -> service.pngDataUrl(""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("二维码内容为空");
    assertThatThrownBy(() -> service.pngDataUrl(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("二维码内容为空");
  }
}
