package com.lis.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 无主标本（仪器未匹配结果）批量转质控标本请求体
 * <p>
 * 将 {@code lis_instrument_unmatched_result} 中挂起的待处理结果
 * 按用户选择的质控品批号 (qcLotId) 写入 {@code lis_qc_record}，
 * 并把无主记录状态更新为 3=已转质控。
 */
@Data
public class UnmatchedBatchToQcDTO {

    /** 待转换的无主标本明细 ID 列表 (lis_instrument_unmatched_result.id) */
    private List<Long> ids;

    /** 目标质控品批号 ID (lis_qc_lot.id). 必须已绑定检验指标+仪器 */
    private Long qcLotId;

    /** 操作人 ID (当前登录员工ID, 可选) */
    private Long operatorId;
}
