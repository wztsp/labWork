package com.lis.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.qc.entity.QCRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 质控记录 Mapper
 */
@Mapper
public interface QCRecordMapper extends BaseMapper<QCRecord> {
}
