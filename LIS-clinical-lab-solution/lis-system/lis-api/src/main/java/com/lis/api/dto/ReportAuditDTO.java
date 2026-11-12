package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportAuditDTO {

    private Long reportId;

    private Long auditorId;

    private boolean pass;

    private String reason;
}
