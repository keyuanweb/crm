-- follow_up 表支持线索关联：customer_id 改为可空（与 lead_id 二选一）
ALTER TABLE follow_up MODIFY COLUMN customer_id BIGINT NULL;
