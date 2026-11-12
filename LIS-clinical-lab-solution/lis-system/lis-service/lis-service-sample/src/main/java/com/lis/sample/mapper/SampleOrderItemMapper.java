package com.lis.sample.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.sample.entity.SampleOrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标本-申请项目关联 Mapper
 */
@Mapper
public interface SampleOrderItemMapper extends BaseMapper<SampleOrderItem> {
}
