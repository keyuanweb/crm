# Data Model: 市场营销模块

**Branch**: `014-marketing` | **Date**: 2026-08-22

## 新增实体

### MarketingCampaign（营销活动）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| name | VARCHAR(100) | NOT NULL | 活动名称 |
| channel | VARCHAR(20) | NOT NULL | WEBSITE/AD/EXHIBITION/REFERRAL/EMAIL/SOCIAL/OTHER |
| budget | BIGINT | NOT NULL DEFAULT 0 | 预算（分） |
| cost | BIGINT | NOT NULL DEFAULT 0 | 成本（分） |
| start_date | DATE | NULL | 开始日期 |
| end_date | DATE | NULL | 结束日期 |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PLANNING' | PLANNING/RUNNING/ENDED |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**: `idx_campaign_channel_status(channel, status)`、`idx_campaign_deleted_name(deleted, name)`。

## 修改既有实体

### Lead / Customer — 新增字段

| 实体 | 字段 | 类型 | 说明 |
|---|---|---|---|
| Lead | campaign_id | BIGINT NULL | 营销归因（可选） |
| Customer | campaign_id | BIGINT NULL | 营销归因（可选，转化时带入） |

## 归因与统计口径

- 活动归因线索数 = `lead WHERE campaign_id = ? AND deleted = 0`。
- 活动归因客户数 = `customer WHERE campaign_id = ? AND deleted = 0`。
- 渠道 ROI：按 channel 聚合——活动数、Σcost、Σ归因线索、Σ归因客户、转化率=客户/线索、收益=归因客户商机 expected_amount_max 合计、ROI=收益/cost（cost=0 → null）。
- 线索转化客户：campaign_id 随转化带入（LeadService.convert 补充）。
