package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.AbstractIntegrationTest;
import com.crm.entity.User;
import com.crm.entity.UserRecoveryCode;
import com.crm.repository.UserMapper;
import com.crm.repository.UserRecoveryCodeMapper;
import com.crm.service.RecoveryCodeService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 恢复码的<b>行为层</b>用例（082 第 7 步）：真实 H2、真实的 SQL 走一遍。
 *
 * <p><b>本类证明不了什么，必须说清楚</b>：SC-M05（并发下同一个恢复码只能被消费一次）在这里<b>测不到</b> —— 单线程下"条件
 * UPDATE"与"先查后改"的可观测行为完全一样，而下面的"同一个码第二次被拒"在两种实现下<b>都是绿的</b> （第一种是靠 {@code used=0}
 * 谓词拦住，第二种是靠内存里刚读到的那行已经不是 {@code used=0}）。 钉住原子性的是 {@code RecoveryCodeServiceTest}
 * 的调用形状断言，两者不可互相替代。
 *
 * <p>本类管的是另外几件只能在这里问的事：条件 UPDATE 真的落到 H2 上没有（{@code used} 列是 BOOLEAN、 {@code @TableLogic} 软删、{@code
 * updated_at} 的填充），换批之后旧码真的作废了，以及一个码被消费不会连累同批的其它码。
 */
class RecoveryCodeServiceIT extends AbstractIntegrationTest {

  @Autowired private RecoveryCodeService recoveryCodes;
  @Autowired private UserMapper userMapper;
  @Autowired private UserRecoveryCodeMapper recoveryCodeMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("恢复码用过即拒，且同一批里的下一个码仍可用")
  void consumedCodeIsRejectedButTheNextOneStillWorks() {
    long userId = createUser("rc_flow");
    List<String> codes = recoveryCodes.regenerate(userId);
    assertThat(codes).hasSize(10);
    assertThat(recoveryCodes.countRemaining(userId)).isEqualTo(10);

    assertThat(recoveryCodes.consume(userId, codes.get(0))).isTrue();
    assertThat(recoveryCodes.countRemaining(userId)).isEqualTo(9);
    assertThat(recoveryCodes.consume(userId, codes.get(0))).isFalse();
    assertThat(recoveryCodes.consume(userId, codes.get(1))).isTrue();
    assertThat(recoveryCodes.countRemaining(userId)).isEqualTo(8);
    // 一次消费只吃掉一个码：中间那个没被动过。
    assertThat(recoveryCodes.consume(userId, codes.get(2))).isTrue();
  }

  @Test
  @DisplayName("库里存的是盐化哈希：不含明文，且消费后 used/used_at 有落库")
  void storedValueIsASaltedHashAndConsumptionIsPersisted() {
    long userId = createUser("rc_hash");
    List<String> codes = recoveryCodes.regenerate(userId);
    UserRecoveryCode first =
        recoveryCodeMapper
            .selectList(
                new LambdaQueryWrapper<UserRecoveryCode>()
                    .eq(UserRecoveryCode::getUserId, userId)
                    .orderByAsc(UserRecoveryCode::getId))
            .get(0);

    assertThat(first.getUsed()).isFalse();
    assertThat(first.getUsedAt()).isNull();
    assertThat(first.getCodeHash()).doesNotContain(codes.get(0));
    assertThat(first.getCodeHash()).matches("[A-Za-z0-9+/]{22}==:([0-9a-f]{2}){32}");

    assertThat(recoveryCodes.consume(userId, codes.get(0))).isTrue();
    UserRecoveryCode after = recoveryCodeMapper.selectById(first.getId());
    assertThat(after.getUsed()).isTrue();
    assertThat(after.getUsedAt()).isNotNull();
  }

  @Test
  @DisplayName("输入规范化：小写、空格、连字符都能换到一次消费")
  void normalizedInputIsAccepted() {
    long userId = createUser("rc_norm");
    List<String> codes = recoveryCodes.regenerate(userId);
    String code = codes.get(0);

    assertThat(recoveryCodes.consume(userId, " " + code.toLowerCase() + " ")).isTrue();
    assertThat(
            recoveryCodes.consume(
                userId, codes.get(1).substring(0, 4) + "-" + codes.get(1).substring(4)))
        .isTrue();
    // 形状不合法的输入不消耗任何东西，也不该把别人的码撞开。
    assertThat(recoveryCodes.consume(userId, "ABC")).isFalse();
    assertThat(recoveryCodes.consume(userId, "不知道")).isFalse();
    assertThat(recoveryCodes.countRemaining(userId)).isEqualTo(8);
  }

  @Test
  @DisplayName("重新生成：旧的一批全部作废，新的可用，且不会两批并存")
  void regenerateRevokesThePreviousBatch() {
    long userId = createUser("rc_regen");
    List<String> old = recoveryCodes.regenerate(userId);
    List<String> fresh = recoveryCodes.regenerate(userId);

    assertThat(recoveryCodes.countRemaining(userId)).isEqualTo(10);
    for (String code : old) {
      assertThat(recoveryCodes.consume(userId, code)).isFalse();
    }
    assertThat(recoveryCodes.consume(userId, fresh.get(0))).isTrue();
  }

  @Test
  @DisplayName("跨用户隔离：别人的码换不到我的会话")
  void codesAreScopedToTheirUser() {
    long mine = createUser("rc_mine");
    long other = createUser("rc_other");
    List<String> otherCodes = recoveryCodes.regenerate(other);

    assertThat(recoveryCodes.consume(mine, otherCodes.get(0))).isFalse();
    assertThat(recoveryCodes.consume(other, otherCodes.get(0))).isTrue();
    assertThat(recoveryCodes.countRemaining(mine)).isZero();
  }

  @Test
  @DisplayName("revokeAll：作废之后全部码都换不到东西")
  void revokeAllInvalidatesEverything() {
    long userId = createUser("rc_revoke");
    List<String> codes = recoveryCodes.regenerate(userId);
    recoveryCodes.consume(userId, codes.get(0));

    recoveryCodes.revokeAll(userId);

    assertThat(recoveryCodes.countRemaining(userId)).isZero();
    for (String code : codes) {
      assertThat(recoveryCodes.consume(userId, code)).isFalse();
    }
  }

  private long createUser(String username) {
    User user = new User();
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode("pass1234"));
    user.setDisplayName(username);
    user.setRole("SUPPORT");
    user.setEnabled(true);
    user.setTokenVersion(0);
    userMapper.insert(user);
    return user.getId();
  }
}
