# Specify 功能规范

---

## 1. 模块 1：客户管理模块

### 功能 1.1：客户列表查询

**用户故事**：
As a 销售人员，I want 查询客户列表，So that 我能快速了解所有客户信息。

**验收条件**：
- Given 客户表中存在 10 条数据
- When 访问 GET /api/v1/customers?page=1&size=5
- Then 返回前 5 条数据，total=10, page=1, size=5, pages=2

### 功能 1.2：客户添加

**验收条件**：
- Given 当前没有 id=1 的客户
- When POST /api/v1/customers，请求体：{name, phone, email, company, industry, source}
- Then 返回 201，响应体包含创建成功的客户信息

---

## 2. 模块 2：商机管理模块

### 功能 2.1：商机列表查询

**验收条件**：
- Given 商机表中存在 10 条数据
- When 访问 GET /api/v1/opportunities?stage=potential&page=1&size=5
- Then 返回前 5 条数据

---

## 3. API 端点汇总

### 客户管理模块

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/customers | 客户列表（分页、搜索、筛选） |
| GET | /api/v1/customers/{id} | 客户详情 |
| POST | /api/v1/customers | 创建客户 |
| PUT | /api/v1/customers/{id} | 更新客户 |
| DELETE | /api/v1/customers/{id} | 删除客户 |

---

## 4. 下一步

阅读 [04-Clarify澄清](04-Clarify澄清.md)。
