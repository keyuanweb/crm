package com.crm.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.AbstractIntegrationTest;
import com.crm.entity.User;
import com.crm.entity.UserRecoveryCode;
import com.crm.repository.UserMapper;
import com.crm.repository.UserRecoveryCodeMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * V89 的**列级**映射守卫（082-two-factor-auth）。
 *
 * <p><b>为何需要本类</b>：{@code SchemaParityIT} 自己写明它<b>不覆盖列级漂移</b>——它只核对 「迁移建的表在镜像里有 CREATE
 * TABLE」。于是下面两类缺陷它一概看不见，而两者都<b>编译期无提示、 只在运行时以 {@code Unknown column} 炸掉</b>：
 *
 * <ol>
 *   <li><b>属性名推不出列名</b>：{@code User.last2faVerifiedAt} 经 MyBatis-Plus 的 camelToUnderline 推出的是
 *       {@code last2fa_verified_at}（"last" 与 "2fa" 之间没有下划线），而真实列是 {@code
 *       last_2fa_verified_at}。段首为数字的列名在本仓仅此一处，没有先例可循。
 *   <li><b>实体继承了 BaseEntity 而表缺列</b>：{@code deleted} 会进每条 SELECT 的 WHERE、 {@code version} 与 {@code
 *       updated_at} 会进每条 INSERT 的列清单。V88 修的正是这一类 （那次的用户可见形态是 078 的两个端点在<b>生产库</b>上 500，而不只是测试库）。
 * </ol>
 *
 * <p>两条用例都是**真写库再读回**，不是断言 DDL 文本——DDL 文本一致而映射错位的组合恰恰是最可能的形态。
 */
class TwoFactorSchemaMappingIT extends AbstractIntegrationTest {

  @Autowired private UserMapper userMapper;
  @Autowired private UserRecoveryCodeMapper recoveryCodeMapper;

  @Test
  @DisplayName("user 的 4 个 V89 列可写可读回（含 last_2fa_verified_at 的显式列名映射）")
  void userTwoFactorColumnsRoundTrip() {
    LocalDateTime enabledAt = LocalDateTime.of(2026, 9, 16, 10, 23, 45);
    LocalDateTime verifiedAt = LocalDateTime.of(2026, 9, 16, 11, 30, 5);

    User user = new User();
    user.setUsername("mfa_roundtrip");
    user.setPasswordHash("$2a$10$notarealhashnotarealhashnotarealhashnotarealhashno");
    user.setDisplayName("MFA 往返");
    user.setRole("SALES");
    user.setEnabled(true);
    user.setTokenVersion(0);
    user.setTwoFactorEnabled(true);
    user.setTotpSecretEncrypted(
        "AAAAAAAAAAAAAAAAAAAAAA==:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
    user.setTwoFactorEnabledAt(enabledAt);
    // 本行是**唯一**会走到 last_2fa_verified_at 的地方：注释掉或漏掉，本用例就只覆盖了另外 3 列。
    user.setLast2faVerifiedAt(verifiedAt);

    assertEquals(1, userMapper.insert(user), "插入 user 应当影响 1 行（失败多为缺列或列名不符）");
    assertNotNull(user.getId(), "自增主键应当回填");

    User reloaded = userMapper.selectById(user.getId());
    assertNotNull(reloaded, "应当能按主键读回（读不到多为 @TableLogic 的 deleted 列缺失）");
    assertEquals(Boolean.TRUE, reloaded.getTwoFactorEnabled());
    assertEquals(user.getTotpSecretEncrypted(), reloaded.getTotpSecretEncrypted());
    assertEquals(enabledAt, reloaded.getTwoFactorEnabledAt());
    assertEquals(
        verifiedAt,
        reloaded.getLast2faVerifiedAt(),
        "last_2fa_verified_at 未映射成功——见本类 javadoc 第 1 条：属性名推不出这个列名");
    // BaseEntity 的列（V89 未新增，但 user 表在本批被 ALTER，顺带钉住）
    assertEquals(0, reloaded.getVersion());
    assertNotNull(reloaded.getCreatedAt());
  }

  @Test
  @DisplayName("user_recovery_code 可写可读回：BaseEntity 四列齐全，used 默认为假")
  void recoveryCodeRoundTripsThroughTheV89Table() {
    User user = new User();
    user.setUsername("mfa_recovery_owner");
    user.setPasswordHash("$2a$10$notarealhashnotarealhashnotarealhashnotarealhashno");
    user.setDisplayName("恢复码主");
    user.setRole("SALES");
    user.setEnabled(true);
    user.setTokenVersion(0);
    user.setTwoFactorEnabled(true);
    userMapper.insert(user);

    UserRecoveryCode code = new UserRecoveryCode();
    code.setUserId(user.getId());
    code.setCodeHash(
        "c2FsdHNhbHRzYWx0c2FsdA==:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    code.setUsed(false);

    assertEquals(
        1,
        recoveryCodeMapper.insert(code),
        "插入恢复码应当影响 1 行——失败多为 user_recovery_code 缺 deleted/version/updated_at 之一"
            + "（@TableLogic 与 @Version 会把它们带进 INSERT 的列清单）");
    assertNotNull(code.getId(), "自增主键应当回填");
    assertNotNull(code.getCreatedAt(), "@TableField(fill = INSERT) 应当回填 created_at");
    assertNotNull(code.getUpdatedAt(), "@TableField(fill = INSERT_UPDATE) 应当回填 updated_at");
    assertEquals(0, code.getVersion(), "@Version 的初始值应当为 0");

    UserRecoveryCode reloaded = recoveryCodeMapper.selectById(code.getId());
    assertNotNull(reloaded, "应当能按主键读回（读不到多为缺 deleted 列，@TableLogic 会把它放进 WHERE）");
    assertEquals(user.getId(), reloaded.getUserId());
    assertEquals(code.getCodeHash(), reloaded.getCodeHash());
    assertFalse(reloaded.getUsed(), "未消费的码读回应为 false");
    assertEquals(0, reloaded.getVersion());
  }

  @Test
  @DisplayName("恢复码的『一次性』由条件 UPDATE 的行数表达，而不是先查后写")
  void conditionalConsumeAffectsExactlyOneRow() {
    User user = new User();
    user.setUsername("mfa_consume");
    user.setPasswordHash("$2a$10$notarealhashnotarealhashnotarealhashnotarealhashno");
    user.setDisplayName("消费");
    user.setRole("SALES");
    user.setEnabled(true);
    user.setTokenVersion(0);
    userMapper.insert(user);

    UserRecoveryCode code = new UserRecoveryCode();
    code.setUserId(user.getId());
    code.setCodeHash("c2FsdA==:00");
    code.setUsed(false);
    recoveryCodeMapper.insert(code);

    // 与 RecoveryCodeService.consume 同形：实体传 null，谓词里带 used = false，靠影响行数定胜负。
    int first =
        recoveryCodeMapper.update(
            null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<
                    UserRecoveryCode>()
                .eq(UserRecoveryCode::getId, code.getId())
                .eq(UserRecoveryCode::getUsed, false)
                .set(UserRecoveryCode::getUsed, true)
                .set(UserRecoveryCode::getUsedAt, LocalDateTime.now()));

    int second =
        recoveryCodeMapper.update(
            null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<
                    UserRecoveryCode>()
                .eq(UserRecoveryCode::getId, code.getId())
                .eq(UserRecoveryCode::getUsed, false)
                .set(UserRecoveryCode::getUsed, true)
                .set(UserRecoveryCode::getUsedAt, LocalDateTime.now()));

    assertEquals(1, first, "首次消费应当恰好影响 1 行");
    assertEquals(
        0,
        second,
        "同一行第二次消费必须影响 0 行——这正是『一次性』的判据所在；" + "若这里得到 1，说明 used = false 谓词没生效或 @TableLogic 把行藏掉了");
    assertTrue(recoveryCodeMapper.selectById(code.getId()).getUsed());
  }
}
