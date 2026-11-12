package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentPurchaseApplyItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * ReagentPurchaseApplyItem Mapper。
 *
 * <p>仅继承 BaseMapper，动态条件一律由 Service 层用 LambdaQueryWrapper 构造，
 * 不做字符串拼接。</p>
 */
@Mapper
public interface ReagentPurchaseApplyItemMapper extends BaseMapper<ReagentPurchaseApplyItem> {
}
