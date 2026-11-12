package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentPurchaseApproval;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购审批流水 Mapper（只追加，不更新不删除）。
 *
 * <p>审批链的展示查询由 ServiceImpl 用 {@code LambdaQueryWrapper}
 * 按 {@code applyId} + {@code roundNo} + {@code nodeLevel} 排序完成；
 * 本接口只承载「当前轮次号」这类聚合 SQL。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentPurchaseApprovalMapper extends BaseMapper<ReagentPurchaseApproval> {

    /**
     * 取某申请单当前最大审批轮次号。
     *
     * <p>驳回后重新提交会开启新一轮审批（roundNo + 1），首次提交时本方法返回 0。</p>
     *
     * @param applyId 申请单 ID，不可为 null
     * @return 最大轮次号，无审批记录时返回 0
     */
    @Select("SELECT COALESCE(MAX(round_no), 0) FROM lis_reagent_purchase_approval "
            + "WHERE apply_id = #{applyId}")
    int maxRoundNo(@Param("applyId") Long applyId);

    /**
     * 统计某申请单某一轮次内已产生的审批动作数量，用于防重复审批。
     *
     * @param applyId   申请单 ID，不可为 null
     * @param roundNo   审批轮次号，不可为 null
     * @param nodeLevel 审批节点层级，不可为 null
     * @return 数量，无数据时返回 0
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_purchase_approval "
            + "WHERE apply_id = #{applyId} AND round_no = #{roundNo} AND node_level = #{nodeLevel}")
    int countByRoundAndNode(@Param("applyId") Long applyId,
                            @Param("roundNo") Integer roundNo,
                            @Param("nodeLevel") Integer nodeLevel);
}
