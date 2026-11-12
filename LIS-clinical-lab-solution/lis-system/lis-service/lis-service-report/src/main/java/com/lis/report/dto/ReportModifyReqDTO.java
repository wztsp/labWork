package com.lis.report.dto;

import lombok.Data;

import java.util.List;

/**
 * 报告修改请求 DTO (前端按 resultId 修改结果值并填写修改原因)
 */
@Data
public class ReportModifyReqDTO {

    /** 报告 ID */
    private Long reportId;

    /** 待修改的结果列表 */
    private List<ModifyItem> results;

    @Data
    public static class ModifyItem {
        /** 结果 ID */
        private Long resultId;
        /** 新结果值 */
        private String resultValue;
        /** 修改原因 */
        private String modifyReason;
    }
}
