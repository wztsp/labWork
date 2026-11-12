package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentApprovalNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审批节点配置 Mapper。
 *
 * <p>查出的节点集合交由纯 POJO {@code com.lis.reagent.support.ApprovalNodeResolver}
 * 按触发条件（ALWAYS / OVER_BUDGET / HAZARD）过滤并重排层级，Mapper 不做业务判定。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentApprovalNodeMapper extends BaseMapper<ReagentApprovalNode> {

    /**
     * 按业务类型取全部启用中的审批节点，已按层级升序排列。
     *
     * @param bizType 业务类型编码（如 {@code PURCHASE_APPLY}），不可为 null
     * @return 启用中的节点列表，无配置时返回空列表
     */
    @Select("SELECT * FROM lis_reagent_approval_node "
            + "WHERE biz_type = #{bizType} AND status = 1 ORDER BY node_level ASC")
    List<ReagentApprovalNode> selectEnabledByBizType(@Param("bizType") String bizType);
}
