package com.lis.integration.adapter;

import com.lis.api.dto.OrderDTO;
import com.lis.integration.dto.CriticalValueAlert;
import com.lis.integration.dto.FeeBilling;
import com.lis.integration.dto.LabReport;
import com.lis.integration.dto.PatientInfo;
import com.lis.integration.dto.SampleStatusUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * FHIR R4 适配器
 * <p>
 * 通过 RESTful 风格调用 HIS 的 FHIR R4 API。
 */
@Slf4j
@Component
public class FhirAdapter implements HisAdapter {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${integration.his.vendors.FHIR.base-url:http://his.example.com/fhir}")
    private String baseUrl;
    @Value("${integration.his.vendors.FHIR.auth-token:dev-token}")
    private String authToken;

    @Override
    public String getAdapterId() {
        return "FHIR";
    }

    @Override
    public boolean healthCheck() {
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/metadata", HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()), String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("FHIR 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<OrderDTO> receiveOrders(Date startTime, Date endTime) {
        // FHIR 通过 ServiceRequest 资源拉取检验申请
        // 简化: 返回空列表 (实际场景应查询 /ServiceRequest?category=laboratory)
        log.info("FHIR 拉取检验申请 (ServiceRequest): start={}, end={}", startTime, endTime);
        return Collections.emptyList();
    }

    @Override
    public PatientInfo queryPatient(String patientId, String visitId) {
        try {
            ResponseEntity<PatientInfo> resp = restTemplate.exchange(
                    baseUrl + "/Patient/" + patientId, HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()), PatientInfo.class);
            return resp.getBody();
        } catch (Exception e) {
            log.error("FHIR 查询患者失败: patientId={}", patientId, e);
            return null;
        }
    }

    @Override
    public boolean sendResults(LabReport report) {
        // FHIR 通过 DiagnosticReport + Observation 提交结果
        try {
            HttpEntity<LabReport> entity = new HttpEntity<>(report, buildHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/DiagnosticReport", HttpMethod.POST, entity, String.class);
            log.info("FHIR 回传结果: reportNo={}, resp={}", report.getReportNo(), resp.getStatusCode());
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("FHIR 回传结果失败", e);
            return false;
        }
    }

    @Override
    public boolean sendSampleStatus(SampleStatusUpdate update) {
        // FHIR 通过 Specimen 资源更新状态
        try {
            HttpEntity<SampleStatusUpdate> entity = new HttpEntity<>(update, buildHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/Specimen/" + update.getSampleBarcode(), HttpMethod.PUT,
                    entity, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("FHIR 更新标本状态失败", e);
            return false;
        }
    }

    @Override
    public boolean sendCriticalValueAlert(CriticalValueAlert alert) {
        // FHIR 通过 Observation + flag=critical 推送危急值
        try {
            HttpEntity<CriticalValueAlert> entity = new HttpEntity<>(alert, buildHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/Observation", HttpMethod.POST, entity, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("FHIR 推送危急值失败", e);
            return false;
        }
    }

    @Override
    public boolean sendFeeBilling(FeeBilling billing) {
        // FHIR 通过 ChargeItem 资源提交费用
        try {
            HttpEntity<FeeBilling> entity = new HttpEntity<>(billing, buildHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/ChargeItem", HttpMethod.POST, entity, String.class);
            log.info("FHIR 回传费用: orderId={}, resp={}", billing.getOrderId(), resp.getStatusCode());
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("FHIR 回传费用失败: orderId={}", billing.getOrderId(), e);
            return false;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + authToken);
        headers.set("Content-Type", "application/fhir+json");
        headers.set("Accept", "application/fhir+json");
        return headers;
    }
}
