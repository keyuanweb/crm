package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.auth.CaptchaResponse;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** 图形验证码服务（017）：生成 4~6 位字母数字图形验证码（JDK AWT），答案存 Redis（一次性、5 分钟过期）， 校验不区分大小写，校验后立即失效。 */
@Service
public class CaptchaService {

  private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);
  private static final String KEY_PREFIX = "auth:captcha:";
  private static final Duration TTL = Duration.ofMinutes(5);

  /** 字符集：排除易混淆的 0/O/1/I/l。 */
  private static final char[] CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

  private final RedisTemplate<String, Object> redisTemplate;
  private final SecureRandom random = new SecureRandom();

  public CaptchaService(RedisTemplate<String, Object> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /** 生成新验证码：写入 Redis 并返回 captchaId + 图片 data URL。 */
  public CaptchaResponse generate() {
    String captchaId = UUID.randomUUID().toString();
    String answer = randomAnswer();
    String imageBase64 = renderImage(answer);
    try {
      redisTemplate.opsForValue().set(KEY_PREFIX + captchaId, answer, TTL);
    } catch (Exception ex) {
      log.warn("Failed to store captcha: {}", ex.getMessage());
      throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
    log.info("Captcha generated for id={}", captchaId);
    return new CaptchaResponse(captchaId, imageBase64);
  }

  /** 校验验证码：不存在/已过期→CAPTCHA_EXPIRED，答案不匹配→CAPTCHA_INVALID，成功后删除（一次性）。 */
  public void validate(String captchaId, String captchaCode) {
    if (captchaCode == null || captchaCode.isBlank()) {
      throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
    }
    String key = KEY_PREFIX + captchaId;
    Object stored;
    try {
      stored = redisTemplate.opsForValue().get(key);
    } catch (Exception ex) {
      log.warn("Failed to read captcha: {}", ex.getMessage());
      throw new BusinessException(ErrorCode.CAPTCHA_EXPIRED);
    }
    try {
      redisTemplate.delete(key);
    } catch (Exception ex) {
      log.warn("Failed to delete captcha: {}", ex.getMessage());
    }
    if (stored == null) {
      throw new BusinessException(ErrorCode.CAPTCHA_EXPIRED);
    }
    if (!stored.toString().equalsIgnoreCase(captchaCode.trim())) {
      log.warn("Captcha mismatch for id={}", captchaId);
      throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
    }
  }

  private String randomAnswer() {
    int length = 4 + random.nextInt(3); // 4~6 位
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(CHARS[random.nextInt(CHARS.length)]);
    }
    return sb.toString();
  }

  /** 用 JDK AWT 绘制简单干扰字符图片，返回 PNG data URL。 */
  private String renderImage(String answer) {
    int width = 120;
    int height = 40;
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(new Color(245, 247, 250));
      g.fillRect(0, 0, width, height);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
      // 干扰线
      for (int i = 0; i < 4; i++) {
        g.setColor(randomColor(160, 210));
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(
            random.nextInt(width), random.nextInt(height),
            random.nextInt(width), random.nextInt(height));
      }
      // 逐字符绘制，轻微随机偏移与旋转
      int x = 10;
      for (int i = 0; i < answer.length(); i++) {
        g.setColor(randomColor(30, 120));
        double angle = (random.nextDouble() - 0.5) * 0.4;
        int y = 28 + random.nextInt(4);
        g.rotate(angle, x, y);
        g.drawString(String.valueOf(answer.charAt(i)), x, y);
        g.rotate(-angle, x, y);
        x += (width - 20) / answer.length();
      }
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ImageIO.write(image, "png", bos);
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to render captcha image", ex);
    } finally {
      g.dispose();
    }
  }

  private Color randomColor(int lo, int hi) {
    int r = lo + random.nextInt(hi - lo);
    int gr = lo + random.nextInt(hi - lo);
    int b = lo + random.nextInt(hi - lo);
    return new Color(r, gr, b);
  }
}
