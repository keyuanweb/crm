# Research: 联系人管理模块

**Branch**: `005-contact-management` | **Date**: 2026-08-22

## 1. 独立联系人实体

**Decision**: 新增 `contact` 表（Flyway V10），客户下联系人一对多（customer_id NOT NULL）。客户详情页聚合联系人列表，联系人详情含所属客户名称。

**Rationale**: 一个客户往往有多个决策角色（决策者/影响者/评估者等）；独立实体比客户内嵌字段更利于检索与筛选（FR-C01/C02）。

## 2. 唯一性与逻辑删除

**Decision**: 同一客户下（姓名+电话）组合唯一（Service 层校验，重复 409 CONTACT_DUPLICATE）；删除采用逻辑删除（@TableLogic），客户逻辑删除后其联系人在列表/搜索中不可见。

**Rationale**: FR-C05/C06；软删除保护审计与历史关联。

## 3. 角色枚举

**Decision**: 角色固定枚举 DECISION_MAKER（决策者）/INFLUENCER（影响者）/EVALUATOR（评估者）/CHAMPION（支持者）/OTHER（其他），默认 OTHER。

**Rationale**: FR-C07；自定义角色留待 016 自定义字段模块。

## 4. 批量装配

**Decision**: 列表页批量查询客户名映射到联系人响应，避免逐行查询（N+1，章程原则五）。

**Rationale**: 联系人量级可达数千，分页必须可控。
