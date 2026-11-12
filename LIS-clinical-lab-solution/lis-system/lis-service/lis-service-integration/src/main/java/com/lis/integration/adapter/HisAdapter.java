package com.lis.integration.adapter;

import com.lis.api.dto.OrderDTO;
import com.lis.integration.dto.CriticalValueAlert;
import com.lis.integration.dto.FeeBilling;
import com.lis.integration.dto.LabReport;
import com.lis.integration.dto.PatientInfo;
import com.lis.integration.dto.SampleStatusUpdate;

import java.util.Date;
import java.util.List;

/**
 * HIS 适配器统一接口
 * <p>
 * 屏蔽不同 HIS 厂商的差异 (REST / HL7v2 / FHIR)，对上层提供统一调用。
 */
public interface HisAdapter {

    /**
     * 适配器唯一标识 (即 HIS 厂商编码)
     */
    String getAdapterId();

    /**
     * 健康检查
     */
    boolean healthCheck();

    // ============== HIS → LIS ==============

    /**
     * 从 HIS 拉取检验申请
     */
    List<OrderDTO> receiveOrders(Date startTime, Date endTime);

    /**
     * 查询患者信息
     */
    PatientInfo queryPatient(String patientId, String visitId);

    // ============== LIS → HIS ==============

    /**
     * 回传检验结果
     */
    boolean sendResults(LabReport report);

    /**
     * 回传标本状态
     */
    boolean sendSampleStatus(SampleStatusUpdate update);

    /**
     * 回传危急值通知
     */
    boolean sendCriticalValueAlert(CriticalValueAlert alert);

    /**
     * 回传费用账单
     */
    boolean sendFeeBilling(FeeBilling billing);
}
