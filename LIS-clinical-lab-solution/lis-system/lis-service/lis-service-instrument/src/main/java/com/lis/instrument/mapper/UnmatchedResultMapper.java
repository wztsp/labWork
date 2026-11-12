package com.lis.instrument.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.instrument.entity.InstrumentUnmatchedResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仪器「无主标本」结果 Mapper。
 */
@Mapper
public interface UnmatchedResultMapper extends BaseMapper<InstrumentUnmatchedResult> {
}