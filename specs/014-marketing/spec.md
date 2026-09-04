# 功能规格：市场营销模块

**功能分支**: `014-marketing`

**创建日期**: 2026-08-22

**状态**: 草稿

**输入**: 路线图第四阶段（P2）——营销动作缺乏管理：市场活动无登记、渠道效果无统计、线索来源（source 字段）已有但无营销归因。需要营销活动管理（活动登记、预算/成本、渠道）、活动关联线索与客户（归因）、渠道 ROI 统计，帮助衡量营销投入产出。

## 用户场景与测试（必填）

### 用户故事 1 - 营销活动管理（优先级：P0）

市场/管理员维护营销活动（活动名称、渠道、预算、成本、起止日期、状态），活动列表支持搜索与筛选。

**优先级理由**: 活动是营销归因的载体，P0 最先交付。

**独立测试**: 创建活动→列表可见→编辑→停用。

**验收场景**:

1. **Given** 市场人员创建营销活动（名称、渠道=官网/广告/展会/转介绍等、预算与成本），**Then** 活动出现在活动列表。
2. **Given** 活动存在，**When** 编辑预算或日期，**Then** 更新生效。
3. **Given** 活动结束，**When** 标记结束，**Then** 不再参与进行中统计。

---

### 用户故事 2 - 营销归因（优先级：P0）

创建线索/客户时可选关联营销活动（归因）；活动详情展示归因的线索数与客户数。

**优先级理由**: 归因是 ROI 计算的基础，P0 交付。

**独立测试**: 创建活动→创建线索时选活动→活动详情线索数+1。

**验收场景**:

1. **Given** 营销活动存在，**When** 创建线索时选择该活动，**Then** 线索记录活动归属。
2. **Given** 线索关联活动，**When** 查看活动详情，**Then** 归因线索数正确。
3. **Given** 线索关联活动并转化客户，**When** 查看活动详情，**Then** 归因客户数正确。

---

### 用户故事 3 - 渠道 ROI 统计（优先级：P1）

按渠道聚合活动统计：活动数、总成本、归因线索数、归因客户数、线索转化率、ROI（预估收益/成本）。

**优先级理由**: ROI 是营销模块的核心价值输出，P1 交付。

**独立测试**: 造多渠道活动数据→渠道统计正确→ROI 计算正确。

**验收场景**:

1. **Given** 多渠道活动与归因数据，**When** 查看渠道统计，**Then** 各渠道活动数/成本/线索数/客户数正确。
2. **Given** 某渠道成本 1000、归因客户预估收益 5000，**When** 查看 ROI，**Then** ROI=5.0。
3. **Given** 渠道无归因数据，**When** 查看统计，**Then** 显示 0 而非报错。

### 边界情况

- 活动渠道为固定枚举：WEBSITE（官网）/AD（广告）/EXHIBITION（展会）/REFERRAL（转介绍）/EMAIL（邮件）/SOCIAL（社媒）/OTHER。
- 活动状态：PLANNING（筹备）/RUNNING（进行中）/ENDED（已结束）。
- 成本 ≥0；预算 ≥0；结束日期不早于开始日期。
- 归因线索的 source 与活动渠道可不同（归因以活动为准）。
- 活动删除为逻辑删除；有归因数据的活动不可删除（409）。

## 需求（必填）

### 功能需求

#### 活动管理

- **FR-M01**: 系统必须支持活动 CRUD（名称、渠道、预算、成本、起止日期、状态）。
- **FR-M02**: 系统必须支持活动分页列表，按关键字/渠道/状态筛选。
- **FR-M03**: 系统必须支持活动状态流转（PLANNING→RUNNING→ENDED；ENDED 不可回退）。

#### 营销归因

- **FR-M04**: 系统必须支持创建线索/客户时关联营销活动（可选）。
- **FR-M05**: 系统必须展示活动归因的线索数与客户数。

#### 渠道 ROI

- **FR-M06**: 系统必须按渠道聚合统计：活动数、总成本、归因线索数、归因客户数、线索转化率、ROI。
- **FR-M07**: ROI = 归因客户预估收益（按商机预期金额估算）/ 总成本。

#### 权限与约束

- **FR-M08**: 活动管理：ADMIN + 市场角色（复用 SALES 或新增 MARKETING？——沿用 ADMIN+SALES）；查看全员。
- **FR-M09**: 有归因数据的活动不可删除（409）。

### 关键实体（涉及数据）

- **营销活动（MarketingCampaign）**: 新增实体；属性包括名称、渠道（枚举）、预算、成本、开始日期、结束日期、状态、逻辑删除、乐观锁、创建人、时间戳。
- **线索（Lead）**: 修改既有实体；新增 campaign_id（可选，归因）。
- **客户（Customer）**: 修改既有实体；新增 campaign_id（可选，归因，转化时带入）。

## 成功标准（必填）

### 可度量结果

- **SC-M01**: 活动创建后 1 秒内出现在列表（≤1s）。
- **SC-M02**: 归因计数正确率 100%（抽查 10 条）。
- **SC-M03**: 渠道 ROI 与手算一致（抽查 10 组）。
- **SC-M04**: 有归因活动删除被拒（正确率 100%）。

## 假设

- 邮件营销/落地页表单为活动渠道记录（EMAIL 渠道 + 归因），不做邮件发送与表单引擎（后续增强）。
- ROI 的收益估算 = 归因客户关联商机的 expected_amount_max 合计（简单口径）。
- 渠道 ROI 统计为管理端聚合报表（前端表格/卡片）。
- 不做活动审批流（后续工作流模块可扩展触发）。

## 国际化（i18n）

### 已完成页面

| 页面 | 文件路径 | 翻译键数 | 状态 |
|---|---|---|---|
| 营销活动 | `frontend/src/pages/marketing/CampaignListPage.tsx` | 18 | ✅ 已完成 |
| 邮件营销容器 | `frontend/src/pages/marketing/EmailMarketingPage.tsx` | 2 | ✅ 已完成 |
| 邮件群发 | `frontend/src/pages/marketing/EmailCampaignPage.tsx` | 24 | ✅ 已完成 |
| 在线表单 | `frontend/src/pages/marketing/OnlineFormPage.tsx` | 30 | ✅ 已完成 |
| 托管落地页 | `frontend/src/pages/marketing/PublicFormPage.tsx` | 12 | ✅ 已完成 |
| 落地页列表 | `frontend/src/pages/landing/LandingPageListPage.tsx` | 37 | ✅ 已完成 |
| 落地页查看 | `frontend/src/pages/landing/LandingPageView.tsx` | 15 | ✅ 已完成 |
| 邮件退订名单 | `frontend/src/pages/email/EmailUnsubscribePage.tsx` | 8 | ✅ 已完成 |

### 翻译键结构

```
pages.marketing.campaign.*          - 营销活动页面（18 个键）
pages.marketing.emailMarketing.*    - 邮件营销容器页（2 个键）
pages.marketing.emailCampaign.*     - 邮件群发页面（24 个键）
pages.marketing.onlineForm.*        - 在线表单页面（30 个键）
pages.marketing.publicForm.*        - 托管落地页（12 个键）
```

### 关键翻译键示例

| 键 | 中文 | English |
|---|---|---|
| `pages.marketing.campaign.title` | 营销活动 | Marketing Campaigns |
| `pages.marketing.campaign.colName` | 活动名称 | Campaign Name |
| `pages.marketing.campaign.colChannel` | 渠道 | Channel |
| `pages.marketing.campaign.colStatus` | 状态 | Status |
| `pages.marketing.campaign.btnCreate` | 新增活动 | Create Campaign |
| `pages.marketing.emailCampaign.title` | 邮件退订名单 | Email Unsubscribes |
| `pages.marketing.onlineForm.title` | 在线表单 | Online Forms |
| `pages.marketing.publicForm.title` | 托管落地页 | Landing Pages |

## 变更记录

| 日期 | 变更内容 |
|---|---|
| 2026-08-22 | 初始规格创建 |
| 2026-08-30 | 完成 5 个营销页面国际化（86 个翻译键） |
