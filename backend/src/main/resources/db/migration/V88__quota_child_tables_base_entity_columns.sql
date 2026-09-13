-- 083-engineering-consolidation T077：补齐 quota 三张子表的 BaseEntity 列

-- 事实（T074 发现，SalesQuotaIT.quotaChildTablesLackBaseEntityColumnsSoWritesFail 留痕）：
-- sales_quota_version / sales_quota_breakdown / sales_quota_achievement 三张表的实体
-- （SalesQuotaVersion / SalesQuotaBreakdown / SalesQuotaAchievement）都 extends BaseEntity，
-- 而 V71 建表时没给这三张表带上 BaseEntity 声明的四列。实体与 DDL 不一致的后果是
-- **078 的版本/分解写路径在任何数据库上都跑不通**：
--   * @TableLogic 的 deleted 会进每条 MP 生成的 SELECT 的 WHERE，selectCount 报 Unknown column 'deleted'
--   * @Version 的 version 与 @TableField(fill = INSERT_UPDATE) 的 updated_at 会进每条 INSERT 的列清单
--   * 用户可见形态：PUT /api/v1/sales-quota/{id} 与 POST /api/v1/sales-quota/{id}/breakdown 双双 5xx
-- 生产库同样是这个形态——不是"只有 H2 缺列"，故本迁移是修 078 的真实缺陷，而非补测试库。

-- 与 50 个继承 BaseEntity 的实体里另外 47 张表的约定对齐：deleted INT NOT NULL DEFAULT 0、
-- version INT NOT NULL DEFAULT 0、updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
-- ON UPDATE CURRENT_TIMESTAMP（形制取自 V71 的父表 sales_quota）。
-- 三张表原本就有 created_at，故只补其余三列（breakdown 已有 updated_at，只补两列）。

ALTER TABLE sales_quota_version
  ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      ON UPDATE CURRENT_TIMESTAMP,
  ADD COLUMN deleted INT NOT NULL DEFAULT 0,
  ADD COLUMN version INT NOT NULL DEFAULT 0;

ALTER TABLE sales_quota_breakdown
  ADD COLUMN deleted INT NOT NULL DEFAULT 0,
  ADD COLUMN version INT NOT NULL DEFAULT 0;

ALTER TABLE sales_quota_achievement
  ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      ON UPDATE CURRENT_TIMESTAMP,
  ADD COLUMN deleted INT NOT NULL DEFAULT 0,
  ADD COLUMN version INT NOT NULL DEFAULT 0;

-- 存量行由 DEFAULT 填 0/NULL 之外的语义：updated_at 取迁移时刻（DEFAULT CURRENT_TIMESTAMP 的取值），
-- deleted=0 意味着历史行全部保留，version=0 与实体的初始化值一致，不构成乐观锁冲突。
