package com.lis.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 患者信息 (从 HIS 查询)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientInfo {

    private String patientId;

    private String patientName;

    private String gender;

    private LocalDate birthday;

    private String phone;

    private String idCard;

    private String address;

    /** 就诊号 */
    private String visitId;

    /** 就诊类型: O=门诊 I=住院 E=急诊 */
    private String visitType;

    private String department;

    private String bedNo;

    private String doctorName;
}
