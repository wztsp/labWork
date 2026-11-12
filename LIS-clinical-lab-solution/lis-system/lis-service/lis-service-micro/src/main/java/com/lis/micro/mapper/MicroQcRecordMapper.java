package com.lis.micro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.micro.entity.MicroQcRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 微生物室内质控记录 Mapper
 */
@Mapper
public interface MicroQcRecordMapper extends BaseMapper<MicroQcRecord> {
}