# Data Model: 批量导入增强（线索/联系人导入）

## 导入结果（复用 ImportResult）

| 字段 | 类型 | 说明 |
|---|---|---|
| successCount | int | 成功条数 |
| failureCount | int | 失败条数 |
| failures | List<ImportFailure> | 失败明细（row 行号 + message 原因） |

## 导入模板（生成 xlsx，无表）

### 线索模板列

| 列 | 必填 | 校验 |
|---|---|---|
| 姓名 | ✓ | |
| 公司 | ✓ | |
| 职位 | | |
| 电话 | | |
| 邮箱 | | |
| 来源 | | 枚举 WEBSITE/AD/EXHIBITION/REFERRAL/COLD_CALL/OTHER |
| 评分 | | 0-100 整数（可选） |
| 备注 | | |

### 联系人模板列

| 列 | 必填 | 校验 |
|---|---|---|
| 姓名 | ✓ | |
| 客户名称 | ✓ | 精确匹配（不区分大小写） |
| 职位 | | |
| 电话 | | |
| 邮箱 | | |
| 角色 | | |
| 备注 | | |

## 约束

- 无新表；导入结果实时返回。
- 导入实体 created_by = 当前用户。
