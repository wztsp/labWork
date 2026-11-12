package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentInvoice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 供应商发票 Mapper。
 *
 * <p>分页与唯一性校验复用 MyBatis-Plus 的条件构造器，无需额外手写 SQL。</p>
 */
@Mapper
public interface ReagentInvoiceMapper extends BaseMapper<ReagentInvoice> {
}
