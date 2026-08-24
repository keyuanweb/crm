package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.auth.CaptchaResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** CaptchaService 单元测试（017 T001）：生成/校验/一次性/过期。 */
class CaptchaServiceTest {

  private RedisTemplate<String, Object> redisTemplate;
  private ValueOperations<String, Object> valueOps;
  private CaptchaService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redisTemplate = mock(RedisTemplate.class);
    valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    service = new CaptchaService(redisTemplate);
  }

  @Test
  @DisplayName("生成：返回 captchaId + data URL 图片，答案写入 Redis（5 分钟 TTL）")
  void generateReturnsIdAndImage() {
    CaptchaResponse resp = service.generate();

    assertThat(resp.getCaptchaId()).isNotBlank();
    assertThat(resp.getImageBase64()).startsWith("data:image/png;base64,");
    verify(valueOps).set(anyString(), anyString(), eq(Duration.ofMinutes(5)));
  }

  @Test
  @DisplayName("校验成功：不区分大小写匹配，且一次性删除 Redis key")
  void validateSuccessCaseInsensitive() {
    when(valueOps.get("auth:captcha:id1")).thenReturn("7GK2");

    service.validate("id1", "7gk2");

    verify(redisTemplate).delete("auth:captcha:id1");
  }

  @Test
  @DisplayName("校验失败：答案不匹配抛 CAPTCHA_INVALID 并删除 key")
  void validateWrongThrows() {
    when(valueOps.get("auth:captcha:id1")).thenReturn("7GK2");

    assertThatThrownBy(() -> service.validate("id1", "AAAA"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CAPTCHA_INVALID);
    verify(redisTemplate).delete("auth:captcha:id1");
  }

  @Test
  @DisplayName("校验：验证码不存在/已过期抛 CAPTCHA_EXPIRED")
  void validateMissingThrowsExpired() {
    when(valueOps.get("auth:captcha:id1")).thenReturn(null);

    assertThatThrownBy(() -> service.validate("id1", "7GK2"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CAPTCHA_EXPIRED);
  }

  @Test
  @DisplayName("校验：captchaCode 为空抛 CAPTCHA_INVALID")
  void validateBlankCodeThrows() {
    assertThatThrownBy(() -> service.validate("id1", "  "))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CAPTCHA_INVALID);
  }
}
