package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDTO {

    private Long sampleId;

    private Long patientId;

    private Integer reportType;

    private String reportDoctor;

    private String checker;

    private String remark;
}
