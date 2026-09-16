package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 用户（认证主体，角色：ADMIN/SALES/SUPPORT）。 */
@Getter
@Setter
@TableName("`user`")
public class User extends BaseEntity {

  private String username;
  private String passwordHash;
  private String displayName;
  private String email;
  private String role;

  /** 所属部门（012）。 */
  private Long departmentId;

  /** 数据权限范围（012）：SELF/DEPT/DEPT_AND_CHILD/ALL。 */
  private String dataScope;

  private Boolean enabled;
  private LocalDateTime lastLoginAt;

  /** 密码变更/重置后递增，旧访问令牌据此失效（FR-005/FR-006）。 */
  private Integer tokenVersion;

  /** 是否启用 TOTP 双因素认证（082）。未启用者登录行为与既有完全一致（FR-M14）。 */
  private Boolean twoFactorEnabled;

  /**
   * AES-256-GCM 密文，格式 {@code base64(iv):base64(ciphertext||tag)}（082）。
   *
   * <p>明文只在 {@code POST /auth/2fa/setup} 的响应里一次性返回；<b>任何接口都不得回显本字段</b>。
   */
  private String totpSecretEncrypted;

  /** 2FA 绑定时间（082）。未启用时为 null。 */
  private LocalDateTime twoFactorEnabledAt;

  /**
   * 最近一次二次验证通过时间（082）。
   *
   * <p><b>为何要显式标注列名</b>：本仓开了 {@code map-underscore-to-camel-case}，但那只管 <i>结果列 → 属性</i> 这一向；<i>属性 →
   * 列</i> 是 MyBatis-Plus 自己按 camelToUnderline 推的， 它只在<b>大写字母</b>前插下划线 ⇒ 属性名 {@code
   * last2faVerifiedAt} 推出的是 {@code last2fa_verified_at}（"last" 与 "2fa" 之间<b>没有</b>下划线），与真实列 {@code
   * last_2fa_verified_at} <b>不符</b>，且症状是运行时 Unknown column 而编译期无任何提示。
   * 段首为数字的列名在本仓仅此一处，故没有可参照的先例，只能显式声明。
   */
  @TableField("last_2fa_verified_at")
  private LocalDateTime last2faVerifiedAt;
}
