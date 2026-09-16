package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 一次性恢复码（082-two-factor-auth，FR-M05/FR-M06）。
 *
 * <p>明文恢复码**只在生成响应里返回一次**，落库的 {@link #codeHash} 是 {@code
 * base64(salt):hex(sha256(salt||code))}——每码独立随机盐，故同一明文码在库里也不可比对。
 *
 * <p>{@link #used} 的消费必须走**条件 UPDATE**（{@code SET used=1, used_at=? WHERE id=? AND used=0}）
 * 并检查影响行数为 1。若写成「先 {@code selectOne} 判断再 {@code updateById}」，两步之间存在 TOCTOU
 * 窗口，并发下**同一个恢复码能被消费两次**——那正是 SC-M05 要禁止的。
 *
 * <p>逻辑删除（{@link BaseEntity} 的 {@code deleted}）在这里承载「作废」语义： 关闭 2FA / 管理员重置 / 重新生成时由 {@code
 * RecoveryCodeService.revokeAll} 整体软删。 保留历史行而非物理删除，是为了让「某个码曾经存在过」在库上仍可追溯。
 */
@Getter
@Setter
@TableName("user_recovery_code")
public class UserRecoveryCode extends BaseEntity {

  /** 所属用户。 */
  private Long userId;

  /** 盐化哈希 {@code base64(salt):hex(sha256(salt||code))}，**不是**明文，也不是明文的裸哈希。 */
  private String codeHash;

  /** 是否已消费。 */
  private Boolean used;

  /** 消费时刻。未消费为 null。 */
  private LocalDateTime usedAt;
}
