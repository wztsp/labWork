package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentStockCheck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 盘点单 Mapper。
 *
 * <p>{@link #selectByIdForUpdate} 是缺陷 P 修复的关键：提交调账前必须用悲观行锁
 * 锁住盘点单，配合状态校验实现「已完成单据不可重复提交」的幂等 —— 单靠先查后改
 * 在并发下会出现两个请求同时读到「进行中」的窗口。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentStockCheckMapper extends BaseMapper<ReagentStockCheck> {

    /**
     * 悲观行锁加载盘点单，必须在事务内调用。
     *
     * @param id 盘点单 ID
     * @return 盘点单；不存在返回 null
     */
    @Select("SELECT * FROM lis_reagent_stock_check WHERE id = #{id} FOR UPDATE")
    ReagentStockCheck selectByIdForUpdate(@Param("id") Long id);
}
