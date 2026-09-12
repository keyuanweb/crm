package com.crm.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus 配置：分页插件、乐观锁插件、公共字段自动填充（research.md R2/R4）。 */
@Configuration
public class MybatisPlusConfig {

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
    pagination.setMaxLimit(100L);
    interceptor.addInnerInterceptor(pagination);
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    return interceptor;
  }

  /**
   * 自动填充 createdAt / updatedAt / createdBy。
   *
   * <p><b>createdBy 的触发条件</b>：实体须在自己的 {@code createdBy} 字段上声明 {@code @TableField(fill =
   * FieldFill.INSERT)}——{@code strictInsertFill} 只填充 {@code TableInfo} 中带该标注的字段，没有标注就**静默跳过**。当前 40
   * 个实体 已全部标注，且有单元测试按"声明了 createdBy 就必须带标注"逐一校验，防止日后新增实体时漏掉（漏掉的后果是"新增时操作人为空"， 而审计与合规导出都以它为依据）。
   *
   * <p><b>只填 null</b>：{@code strictInsertFill} 在字段已有值时保持原样，故业务代码里显式 `setCreatedBy(...)` 的地方语义不变
   * （存量代码大多如此），本填充只兜住"忘了写"的那部分——即 FR-G26 要消除的系统性遗漏。
   *
   * <p><b>无会话时留空</b>：{@code SecurityUtil.currentUserId()} 在无认证主体时返回 null（应用启动期的 {@code
   * DataInitializer}、调度线程、纯 Mapper 调用的测试夹具），此时不填充，保持与改造前一致，不会写入一个假的操作人。
   *
   * <p><b>updatedBy 未实现</b>：40 个实体中声明 {@code updatedBy} 的有 **0 个**，且**无任何迁移含 {@code updated_by}
   * 列**——该字段在数据库里根本不存在。补它需要新增 Flyway 迁移，而 plan.md 已声明本 spec 不改 schema，故按 T058 记录转独立 spec。
   */
  @Bean
  public MetaObjectHandler metaObjectHandler() {
    return new MetaObjectHandler() {
      @Override
      public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        // 值为 null 时 strictInsertFill 自身会跳过，无需在此判空
        this.strictInsertFill(metaObject, "createdBy", Long.class, SecurityUtil.currentUserId());
      }

      @Override
      public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
      }
    };
  }
}
