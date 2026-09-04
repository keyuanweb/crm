-- 078-sales-quota: 销售配额分解模块数据库迁移

-- 销售配额表
CREATE TABLE sales_quota (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT NULL,
  quarter SMALLINT NULL COMMENT '季度（1-4，年度配额为 NULL）',
  year INT NOT NULL,
  team_id BIGINT NULL COMMENT '团队 ID',
  user_id BIGINT NULL COMMENT '用户 ID',
  amount DECIMAL(15,2) NOT NULL COMMENT '配额金额（万元）',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：DRAFT/ACTIVE/CLOSED',
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_year_team_user UNIQUE (year, team_id, user_id),
  CONSTRAINT fk_sales_quota_parent FOREIGN KEY (parent_id) REFERENCES sales_quota(id),
  CONSTRAINT fk_sales_quota_team FOREIGN KEY (team_id) REFERENCES department(id),
  CONSTRAINT fk_sales_quota_user FOREIGN KEY (user_id) REFERENCES user(id)
);

-- 索引
CREATE INDEX idx_sales_quota_year_team ON sales_quota(year, team_id);
CREATE INDEX idx_sales_quota_year_user ON sales_quota(year, user_id);
CREATE INDEX idx_sales_quota_parent ON sales_quota(parent_id);
CREATE INDEX idx_sales_quota_status ON sales_quota(status);

-- 配额版本历史表
CREATE TABLE sales_quota_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  quota_id BIGINT NOT NULL,
  old_amount DECIMAL(15,2) NOT NULL,
  new_amount DECIMAL(15,2) NOT NULL,
  changed_by BIGINT NOT NULL,
  changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  change_reason VARCHAR(500) NULL,
  version_number INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sqv_quota FOREIGN KEY (quota_id) REFERENCES sales_quota(id),
  CONSTRAINT fk_sqv_user FOREIGN KEY (changed_by) REFERENCES user(id)
);

-- 索引
CREATE INDEX idx_sqv_quota_id ON sales_quota_version(quota_id);
CREATE INDEX idx_sqv_quota_version ON sales_quota_version(quota_id, version_number);

-- 配额分解关系表
CREATE TABLE sales_quota_breakdown (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_quota_id BIGINT NOT NULL,
  child_quota_id BIGINT NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_parent_child UNIQUE (parent_quota_id, child_quota_id),
  CONSTRAINT fk_sqb_parent FOREIGN KEY (parent_quota_id) REFERENCES sales_quota(id),
  CONSTRAINT fk_sqb_child FOREIGN KEY (child_quota_id) REFERENCES sales_quota(id)
);

-- 索引
CREATE INDEX idx_sqb_parent ON sales_quota_breakdown(parent_quota_id);
CREATE INDEX idx_sqb_child ON sales_quota_breakdown(child_quota_id);

-- 配额达成统计表
CREATE TABLE sales_quota_achievement (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  quota_id BIGINT NOT NULL,
  actual_amount DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '实际销售额（万元）',
  achievement_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '达成率（%）',
  calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  quota_year INT NOT NULL,
  quota_quarter SMALLINT NULL,
  quota_team_id BIGINT NULL,
  quota_user_id BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sqa_quota FOREIGN KEY (quota_id) REFERENCES sales_quota(id),
  CONSTRAINT fk_sqa_team FOREIGN KEY (quota_team_id) REFERENCES department(id),
  CONSTRAINT fk_sqa_user FOREIGN KEY (quota_user_id) REFERENCES user(id)
);

-- 索引
CREATE INDEX idx_sqa_quota_id ON sales_quota_achievement(quota_id);
CREATE INDEX idx_sqa_year_quarter_team ON sales_quota_achievement(quota_year, quota_quarter, quota_team_id);
CREATE INDEX idx_sqa_year_user ON sales_quota_achievement(quota_year, quota_user_id);
