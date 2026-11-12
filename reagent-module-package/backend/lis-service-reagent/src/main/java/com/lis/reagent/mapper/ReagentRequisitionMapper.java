package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentRequisition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 试剂业务单（领用 / 退回 / 报废 / 调拨 / 借用 / 归还）Mapper。
 *
 * <p>六类单据共用一张表，用 {@code req_type} 区分；类型差异由纯 POJO
 * {@code com.lis.reagent.support.RequisitionTypeSpec} 描述，Mapper 不做类型分支。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentRequisitionMapper extends BaseMapper<ReagentRequisition> {

    /**
     * 悲观行锁读取业务单，用于审批 / 执行等并发写场景，必须在同一事务内调用。
     *
     * @param id 业务单 ID，不可为 null
     * @return 加锁后的业务单，不存在时返回 null
     */
    @Select("SELECT * FROM lis_reagent_requisition WHERE id = #{id} FOR UPDATE")
    ReagentRequisition selectByIdForUpdate(@Param("id") Long id);

    /**
     * 统计某源单（借用单 / 领用单）已产生的、未作废的二阶段单据数量。
     *
     * <p>用于「借用 → 归还」「领用 → 退库」的重复发起校验。状态码见
     * {@link com.lis.reagent.enums.ReqStatus}：
     * {@link com.lis.reagent.enums.ReqStatus#DRAFT}(0) 草稿 /
     * {@link com.lis.reagent.enums.ReqStatus#PENDING}(1) 待审 /
     * {@link com.lis.reagent.enums.ReqStatus#APPROVED}(2) 已审 /
     * {@link com.lis.reagent.enums.ReqStatus#EXECUTED}(3) 已执行 /
     * {@link com.lis.reagent.enums.ReqStatus#REJECTED}(4) 已驳回 /
     * {@link com.lis.reagent.enums.ReqStatus#CANCELLED}(5) 已作废；
     * 此处排除 {@link com.lis.reagent.enums.ReqStatus#CANCELLED}(5)；
     * 禁止散落魔法数字（共享知识第 14 条）。</p>
     *
     * @param sourceReqId 源单 ID，不可为 null
     * @param reqType     二阶段单据类型码，见 {@code com.lis.reagent.enums.ReqType}
     * @return 数量，无数据时返回 0
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_requisition "
            + "WHERE source_req_id = #{sourceReqId} AND req_type = #{reqType} AND status <> 5")
    int countBySourceAndType(@Param("sourceReqId") Long sourceReqId,
                             @Param("reqType") Integer reqType);

    /**
     * 汇总某源单已归还 / 已退库的数量合计（仅统计状态=已执行(3) 的单据）。
     *
     * <p>用于借用归还的「超量归还」校验：已归还合计 + 本次归还量 不得超过借出量。
     * 仅统计状态 = {@link com.lis.reagent.enums.ReqStatus#EXECUTED}(3) 已执行的单据，
     * 与 SQL 中 {@code r.status = 3} 一致；禁止散落魔法数字（共享知识第 14 条）。</p>
     *
     * @param sourceReqId 源单 ID，不可为 null
     * @param reqType     二阶段单据类型码，见 {@code com.lis.reagent.enums.ReqType}
     * @return 数量合计，无数据时返回 0
     */
    @Select("SELECT COALESCE(SUM(i.quantity), 0) FROM lis_reagent_requisition r "
            + "JOIN lis_reagent_requisition_item i ON i.req_id = r.id "
            + "WHERE r.source_req_id = #{sourceReqId} AND r.req_type = #{reqType} AND r.status = 3")
    BigDecimal sumReturnedQtyBySource(@Param("sourceReqId") Long sourceReqId,
                                      @Param("reqType") Integer reqType);

    /**
     * 取当日同前缀的最大单号，供 {@code com.lis.reagent.support.BizNoGenerator} 续号。
     *
     * @param head 前缀 + yyyyMMdd 头部（如 {@code REQ20260804}），不可为 null
     * @return 最大单号；当日尚无单据时返回 null
     */
    @Select("SELECT MAX(req_no) FROM lis_reagent_requisition WHERE req_no LIKE #{head} || '%'")
    String selectMaxNo(@Param("head") String head);
}
