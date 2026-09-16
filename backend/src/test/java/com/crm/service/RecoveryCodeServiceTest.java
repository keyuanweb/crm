package com.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.entity.UserRecoveryCode;
import com.crm.repository.UserRecoveryCodeMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link RecoveryCodeService} 的<b>调用形状</b>与静态纯逻辑（082 第 7 步）。
 *
 * <p><b>为什么必须断言"SQL 是怎么被拼的"，而不只断言返回 true/false</b>：条件 UPDATE（{@code WHERE id=? AND used=0}
 * 且要求影响行数恰为 1）与它的劣解「{@code selectOne} 查到未使用行 → 比对 → {@code updateById}」在 <b>单线程下的可观测行为完全相同</b> —— 而
 * {@code MockMvc} 是单线程的。⇒ 即使把「同一个恢复码用两次」断言得非常细 （第二次本来就是 {@code
 * false}，因为第一次已经把它改掉了），<b>集成测试在结构上也区分不出这两者</b>。 钉住 SC-M05（并发下同一个码只能消费一次）的，只有下面那几条对 wrapper 的断言。
 *
 * <p>本类的断言目标是<b>别人最可能改错的那一处</b>：谓词里到底还有没有 {@code used=0}。
 */
class RecoveryCodeServiceTest {

  private static final long USER = 7L;

  private static final int CODE_COUNT = 10;

  private static final long ROW_ID = 1234L;

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-16T10:00:00Z"), ZoneOffset.UTC);

  private UserRecoveryCodeMapper mapper;

  private RecoveryCodeService service;

  /**
   * MP 的 {@code LambdaUpdateWrapper} 把 lambda 解析成列名时要查 {@code TableInfo} —— 那是 mapper 扫描阶段（{@code
   * MybatisConfiguration} 装载时）建立并缓存起来的。纯 Mockito 用例里没有那一步， 于是 {@code .set(Entity::getUsed, true)}
   * 会抛 "can not find lambda cache for this entity"。 初始化一次即可，与 {@code ApiKeyServiceTest} 同款。
   */
  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, UserRecoveryCode.class);
  }

  @BeforeEach
  void setUp() {
    mapper = mock(UserRecoveryCodeMapper.class);
    service = new RecoveryCodeService(mapper, CLOCK, CODE_COUNT);
  }

  // ===== consume 的调用形状 =====

  @Test
  @DisplayName("consume：条件 UPDATE 的谓词里必须有 used=0，且要求影响行数恰为 1")
  void consumeUsesConditionalUpdateWithUnusedPredicate() {
    Issued issued = issue();
    UserRecoveryCode row = issued.rows().get(0);
    row.setId(ROW_ID);
    when(mapper.selectList(any())).thenReturn(List.of(row));
    when(mapper.update(isNull(), any())).thenReturn(1);

    assertTrue(service.consume(USER, issued.codes().get(0)));

    LambdaUpdateWrapper<UserRecoveryCode> update = capturedUpdate();
    // 先取 SqlSegment 再读参数表：MP 的 wrapper 是**惰性**的，`eq(...)` 只是往
    // MergeSegments 里挂了个未求值的 ISqlSegment，列名解析与参数落到 paramNameValuePairs
    // 都发生在 getSqlSegment() 那一刻。反过来读会拿到一个空表 —— 断言就永远不可能失败。
    String segment = update.getSqlSegment();
    Collection<Object> params = update.getParamNameValuePairs().values();
    // `used=0` 谓词：删掉它，UPDATE 就会把一个已消费的行再"消费"一次（并发下同一个码两次进门）。
    assertTrue(
        params.contains(Boolean.FALSE),
        "条件 UPDATE 缺少 used=0 谓词 —— 参数值里应同时出现 set 的 true 与谓词的 false，实际=" + params);
    assertTrue(params.contains(Boolean.TRUE), "条件 UPDATE 应把 used 置为 true，实际=" + params);
    assertTrue(params.contains(ROW_ID), "条件 UPDATE 应按主键定位，实际=" + params);
    assertTrue(segment.contains("used"), "谓词里应出现 used 列，实际=" + segment);
    assertTrue(segment.contains("id"), "谓词里应出现 id 列，实际=" + segment);

    // updateById 把整行实体写回去：既没有 created_at/updated_at 的填充语义，也让"改哪一行"不再受 WHERE 约束
    // （等于回到先判断后写入）。
    verify(mapper, never()).updateById(any());
  }

  @Test
  @DisplayName("consume：命中但影响行数为 0（并发下被别人抢先）⇒ false")
  void consumeReturnsFalseWhenTheConditionalUpdateTouchesNoRow() {
    Issued issued = issue();
    UserRecoveryCode row = issued.rows().get(0);
    row.setId(ROW_ID);
    when(mapper.selectList(any())).thenReturn(List.of(row));
    when(mapper.update(isNull(), any())).thenReturn(0);

    assertFalse(
        service.consume(USER, issued.codes().get(0)), "命中不等于消费成功：必须等 UPDATE 的影响行数说话，否则又成了先判断后写入");
  }

  @Test
  @DisplayName("consume：只比对哈希，不比对明文")
  void consumeComparesHashesNotPlaintext() {
    Issued issued = issue();
    String stored = issued.rows().get(0).getCodeHash();
    String other = issued.codes().get(1);
    assertFalse(stored.contains(other), "存储串里不应出现明文");
    when(mapper.selectList(any())).thenReturn(List.of(issued.rows().get(0)));

    assertFalse(service.consume(USER, other), "另一个（合法的）码不应命中这一行");
    verify(mapper, never()).update(isNull(), any());
  }

  @Test
  @DisplayName("consume：库中哈希形态不合法时当作不匹配，不抛")
  void consumeTreatsMalformedStoredHashAsNoMatch() {
    String code = issue().codes().get(0);
    for (String malformed :
        new String[] {"不是哈希", "", "AAA", ":abc", "!!!:abc", "AAAA:zz", "AAAA:"}) {
      when(mapper.selectList(any())).thenReturn(List.of(row(ROW_ID, malformed)));
      assertFalse(service.consume(USER, code), "形态不合法的存储串应判为不匹配：" + malformed);
    }
    verify(mapper, never()).update(isNull(), any());
  }

  @Test
  @DisplayName("consume：输入规范化 —— 小写、空格、连字符都认")
  void consumeAcceptsNormalizedInput() {
    Issued issued = issue();
    UserRecoveryCode row = issued.rows().get(0);
    row.setId(ROW_ID);
    String code = issued.codes().get(0);
    when(mapper.selectList(any())).thenReturn(List.of(row));
    when(mapper.update(isNull(), any())).thenReturn(1);

    assertTrue(service.consume(USER, " " + code.toLowerCase() + " "));
    assertTrue(service.consume(USER, code.substring(0, 4) + "-" + code.substring(4)));
    assertTrue(service.consume(USER, code));
  }

  @Test
  @DisplayName("consume：形不成合法码的输入一律直接拒，且不查库")
  void consumeRejectsUnusableInputWithoutQuerying() {
    String[] bad = {null, "", "   ", "ABCD234", "ABCD23456", "ABCD23O5", "ABCD23I5", "ABCD23中"};
    for (String input : bad) {
      assertFalse(service.consume(USER, input), "应拒：" + input);
    }
    verify(mapper, never()).selectList(any());
    verify(mapper, never()).update(isNull(), any());
  }

  // ===== regenerate =====

  @Test
  @DisplayName("regenerate：先整体软删、再插入 N 行，返回 N 个互不相同的 8 位明文码")
  void regenerateRevokesThenInserts() {
    List<String> codes = service.regenerate(USER);

    assertEquals(CODE_COUNT, codes.size());
    assertEquals(CODE_COUNT, codes.stream().distinct().count(), "同一批码不应重复");
    for (String code : codes) {
      assertEquals(RecoveryCodeService.CODE_LENGTH, code.length());
      for (char ch : code.toCharArray()) {
        assertTrue(new String(RecoveryCodeService.ALPHABET).indexOf(ch) >= 0, "字母表外的字符：" + ch);
      }
    }

    ArgumentCaptor<UserRecoveryCode> captor = ArgumentCaptor.forClass(UserRecoveryCode.class);
    verify(mapper, times(CODE_COUNT)).insert(captor.capture());
    List<UserRecoveryCode> rows = captor.getAllValues();
    for (int i = 0; i < CODE_COUNT; i++) {
      assertEquals(USER, rows.get(i).getUserId());
      assertEquals(Boolean.FALSE, rows.get(i).getUsed());
      assertNull(rows.get(i).getUsedAt(), "未消费的行不应有消费时刻");
      String codeHash = rows.get(i).getCodeHash();
      assertFalse(codeHash.contains(codes.get(i)), "库里不得出现明文");
      // 16 字节 ⇒ 标准 base64 的 22 个数据字符 + 2 个 '=' 填充 ⇒ 冒号在第 24 位。
      assertTrue(
          codeHash.matches("[A-Za-z0-9+/]{22}==:([0-9a-f]{2}){32}"),
          "形态应为 base64(盐):hex(sha256)，实际=" + codeHash);
    }
    // 换批之前必须先作废：否则旧码会与新码并存，用户手里那张纸还能用。
    verify(mapper).delete(any());
  }

  @Test
  @DisplayName("regenerate：每行一个独立的盐（盐不是常量）")
  void regenerateSaltsEachRowIndependently() {
    service.regenerate(USER);
    service.regenerate(USER);

    ArgumentCaptor<UserRecoveryCode> captor = ArgumentCaptor.forClass(UserRecoveryCode.class);
    verify(mapper, times(CODE_COUNT * 2)).insert(captor.capture());
    List<UserRecoveryCode> rows = captor.getAllValues();
    // 明文是随机的，故"哈希不同"几乎必然成立、证明不了盐；要比的是":"之前的**盐**本身。
    // 盐若被写成常量，这里会相等 —— 那意味着彩虹表可以跨行复用。
    // 论点顺序：JUnit 是 assertEquals(expected, actual)，反过来会把失败信息印反（破坏留痕时踩到过）。
    assertEquals(24, rows.get(0).getCodeHash().indexOf(':'), "盐应为 16 字节 base64（24 字符）");
    assertNotEquals(saltOf(rows.get(0)), saltOf(rows.get(CODE_COUNT)), "两批之间盐也不应相同");
    assertNotEquals(saltOf(rows.get(0)), saltOf(rows.get(1)), "同一批内盐也不应相同");
  }

  @Test
  @DisplayName("regenerate → consume 往返：刚生成的码能用，比对走哈希")
  void generatedCodeRoundTripsThroughConsume() {
    Issued issued = issue();
    List<UserRecoveryCode> rows = issued.rows();
    for (int i = 0; i < rows.size(); i++) {
      rows.get(i).setId(ROW_ID + i);
    }
    when(mapper.selectList(any())).thenReturn(rows);
    when(mapper.update(isNull(), any())).thenReturn(1);

    assertTrue(service.consume(USER, issued.codes().get(0)), "刚生成的码必须能用");
    assertTrue(service.consume(USER, issued.codes().get(9)), "下一个码也应能用");
    assertFalse(service.consume(USER, "AAAA2222"), "不在库里的明文不应命中任何一行");
    verify(mapper, times(2)).update(isNull(), any());
  }

  // ===== countRemaining / revokeAll =====

  @Test
  @DisplayName("countRemaining：统计条件里必须有 used=0")
  void countRemainingFiltersUnusedOnly() {
    when(mapper.selectCount(any())).thenReturn(3L);

    assertEquals(3, service.countRemaining(USER));

    LambdaQueryWrapper<UserRecoveryCode> query = capturedSelect();
    query.getSqlSegment(); // 惰性求值：见 consumeUsesConditionalUpdateWithUnusedPredicate 的说明
    Collection<Object> params = query.getParamNameValuePairs().values();
    assertTrue(params.contains(Boolean.FALSE), "计数必须排除已消费的行，实际谓词参数=" + params);
    assertTrue(params.contains(USER), "计数必须限定到该用户，实际谓词参数=" + params);
  }

  @Test
  @DisplayName("countRemaining：查不出结果（null）时按 0，不 NPE")
  void countRemainingTreatsNullAsZero() {
    when(mapper.selectCount(any())).thenReturn(null);

    assertEquals(0, service.countRemaining(USER));
  }

  @Test
  @DisplayName("revokeAll：按用户软删（不给主键），且不整行 update")
  void revokeAllSoftDeletesByUser() {
    service.revokeAll(USER);

    LambdaQueryWrapper<UserRecoveryCode> deletion = capturedDelete();
    deletion.getSqlSegment(); // 惰性求值：见 consumeUsesConditionalUpdateWithUnusedPredicate 的说明
    Collection<Object> params = deletion.getParamNameValuePairs().values();
    assertTrue(params.contains(USER), "作废条件里应有 userId，实际=" + params);
    assertFalse(params.contains(ROW_ID), "作废不应退化成按主键逐行删，实际=" + params);
    verify(mapper, never()).deleteById(anyLong());
    verify(mapper, never()).update(isNull(), any());
  }

  // ===== 静态纯逻辑 =====

  @Test
  @DisplayName("字母表逐字符钉死：31 符号、去 O/0/I/L/1、无重复")
  void alphabetIsPinned() {
    String alphabet = new String(RecoveryCodeService.ALPHABET);
    assertEquals("ABCDEFGHJKMNPQRSTUVWXYZ23456789", alphabet);
    assertEquals(31, alphabet.length());
    for (char ambiguous : new char[] {'O', '0', 'I', 'L', 'l', '1'}) {
      assertTrue(alphabet.indexOf(ambiguous) < 0, "易混淆字符不应在字母表里：" + ambiguous);
    }
    assertEquals(31, alphabet.chars().distinct().count(), "字母表不应有重复符号");
    assertEquals(8, RecoveryCodeService.CODE_LENGTH);
  }

  @Test
  @DisplayName("normalize：丢分隔符是单射的（字母表里没有空白与连字符）")
  void normalizeDropsSeparatorsInjectively() {
    assertEquals("ABCD2345", RecoveryCodeService.normalize("ab-cd-23-45"));
    assertEquals("ABCD2345", RecoveryCodeService.normalize("ABCD2345"));
    assertEquals("ABCD2345", RecoveryCodeService.normalize("\tabcd\n2345 "));
    // 单射性的前提：被丢掉的字符不可能属于任何一个存储码。
    String alphabet = new String(RecoveryCodeService.ALPHABET);
    assertTrue(alphabet.indexOf('-') < 0, "字母表里若出现连字符，丢弃就不再是单射");
    for (char ch : alphabet.toCharArray()) {
      assertFalse(Character.isWhitespace(ch), "字母表不应含空白：" + (int) ch);
    }
    assertNotEquals(
        RecoveryCodeService.normalize("ABCD-2345"), RecoveryCodeService.normalize("ABCD-2346"));
  }

  @Test
  @DisplayName("normalize：字符合法但长度不对 ⇒ null（不进库里比对）")
  void normalizeRejectsWrongLength() {
    assertNull(RecoveryCodeService.normalize("ABCD234"));
    assertNull(RecoveryCodeService.normalize("ABCD23456"));
    assertNull(RecoveryCodeService.normalize(""));
    assertNull(RecoveryCodeService.normalize("---"));
    assertNull(RecoveryCodeService.normalize(null));
  }

  @Test
  @DisplayName("normalize：字母表外的字符 ⇒ null（含中文与标点）")
  void normalizeRejectsOutOfAlphabetChars() {
    assertNull(RecoveryCodeService.normalize("ABCD234中"));
    assertNull(RecoveryCodeService.normalize("ABCD234!"));
    assertNull(RecoveryCodeService.normalize("ABCD_234"));
  }

  @Test
  @DisplayName("构造：非正的 recovery-code-count 启动即抛，且消息里点名属性")
  void constructorRejectsNonPositiveCodeCount() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> new RecoveryCodeService(mapper, CLOCK, 0));
    assertTrue(ex.getMessage().contains("crm.security.mfa.recovery-code-count"), ex.getMessage());
  }

  // ===== 辅助 =====

  /** 一次真实生成的结果：明文码与它们落库的行（同一顺序）。 */
  private record Issued(List<String> codes, List<UserRecoveryCode> rows) {}

  /**
   * 跑一次真实的 {@link RecoveryCodeService#regenerate}，把明文与"库里的行"配对取出。
   *
   * <p>本类的用例不接受"手写一个假的哈希串"：那样"生成"与"比对"两侧就各写各的，两边一起写错也照样全绿。 从真实生成路径取哈希，才能让"生成 → 校验"的往返成为一条被观测到的性质。
   */
  private Issued issue() {
    List<String> codes = service.regenerate(USER);
    ArgumentCaptor<UserRecoveryCode> captor = ArgumentCaptor.forClass(UserRecoveryCode.class);
    verify(mapper, times(CODE_COUNT)).insert(captor.capture());
    return new Issued(codes, new ArrayList<>(captor.getAllValues()));
  }

  private static String saltOf(UserRecoveryCode row) {
    String stored = row.getCodeHash();
    return stored.substring(0, stored.indexOf(':'));
  }

  private static UserRecoveryCode row(long id, String codeHash) {
    UserRecoveryCode row = new UserRecoveryCode();
    row.setId(id);
    row.setUserId(USER);
    row.setCodeHash(codeHash);
    row.setUsed(false);
    return row;
  }

  @SuppressWarnings("unchecked")
  private LambdaUpdateWrapper<UserRecoveryCode> capturedUpdate() {
    ArgumentCaptor<LambdaUpdateWrapper<UserRecoveryCode>> captor =
        ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    verify(mapper).update(isNull(), captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings("unchecked")
  private LambdaQueryWrapper<UserRecoveryCode> capturedSelect() {
    ArgumentCaptor<LambdaQueryWrapper<UserRecoveryCode>> captor =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectCount(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings("unchecked")
  private LambdaQueryWrapper<UserRecoveryCode> capturedDelete() {
    ArgumentCaptor<LambdaQueryWrapper<UserRecoveryCode>> captor =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper, times(1)).delete(captor.capture());
    return captor.getValue();
  }
}
