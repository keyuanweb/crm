package com.crm.repository.quota;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.SalesQuotaAchievement;
import org.apache.ibatis.annotations.Mapper;

/** 配额达成统计 Repository（078-sales-quota）。 */
@Mapper
public interface SalesQuotaAchievementRepository extends BaseMapper<SalesQuotaAchievement> {}
