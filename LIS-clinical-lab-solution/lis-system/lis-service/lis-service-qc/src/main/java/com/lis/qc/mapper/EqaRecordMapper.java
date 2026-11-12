package com.lis.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.qc.entity.EqaRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 室间质控 (EQA) 记录 Mapper
 */
@Mapper
public interface EqaRecordMapper extends BaseMapper<EqaRecord> {
}
