/** 数据保留策略 Repository（080-data-retention）。 */
package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.model.entity.DataRetentionPolicy;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataRetentionPolicyRepository extends BaseMapper<DataRetentionPolicy> {}
