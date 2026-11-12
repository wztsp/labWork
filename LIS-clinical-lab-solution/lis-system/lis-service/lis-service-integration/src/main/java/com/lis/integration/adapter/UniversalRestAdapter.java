package com.lis.integration.adapter;

import com.lis.api.dto.OrderDTO;
import com.lis.api.dto.OrderItemDTO;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 通用 REST 适配器
 * <p>
 * 通过 RestTemplate 调用 HIS REST API。
 */
@Slf4j
@Component
public class UniversalRestAdapter implements HisAdapter {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${integration.his.vendors.UNIVERSAL_REST.base-url:http://his.example.com/api}")
    private String baseUrl;
    @Value("${integration.his.vendors.UNIVERSAL_REST.auth-token:dev-token}")
    private String authToken;

    @Override
    public String getAdapterId() {
        return "UNIVERSAL_REST";
    }

    @Override
    public boolean healthCheck() {
        try {
            HttpHeaders headers = buildHeaders();
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/health", HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("UNIVERSAL_REST 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<OrderDTO> receiveOrders(Date startTime, Date endTime) {
        try {
            HttpHeaders headers = buildHeaders();
            String url = baseUrl + "/orders?start=" + startTime.getTime() + "&end=" + endTime.getTime();
            ResponseEntity<OrderDTO[]> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), OrderDTO[].class);
            OrderDTO[] arr = resp.getBody();
            return arr == null ? Collections.emptyList() : List.of(arr);
        } catch (Exception e) {
            log.error("拉取 HIS 申请失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public PatientInfo queryPatient(String patientId, String visitId) {
        try {
            HttpHeaders headers = buildHeaders();
            String url = baseUrl + "/patients/" + patientId + "?visitId=" + visitId;
            ResponseEntity<PatientInfo> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), PatientInfo.class);
            return resp.getBody();
        } catch (Exception e) {
            log.error("查询 HIS 患者失败: patientId={}", patientId, e);
            return null;
        }
    }

    @Override
    public boolean sendResults(LabReport report) {
        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<LabReport> entity = new HttpEntity<>(report, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/reports", HttpMethod.POST, entity, String.class);
            log.info("回传结果到 HIS: reportNo={}, resp={}", report.getReportNo(), resp.getStatusCode());
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("回传 HIS 结果失败: reportNo={}", report.getReportNo(), e);
            return false;
        }
    }

    @Override
    public boolean sendSampleStatus(SampleStatusUpdate update) {
        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<SampleStatusUpdate> entity = new HttpEntity<>(update, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/samples/status", HttpMethod.POST, entity, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("回传标本状态失败: {}", update.getOrderNo(), e);
            return false;
        }
    }

    @Override
    public boolean sendCriticalValueAlert(CriticalValueAlert alert) {
        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<CriticalValueAlert> entity = new HttpEntity<>(alert, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/critical-alerts", HttpMethod.POST, entity, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("回传危急值失败", e);
            return false;
        }
    }

    @Override
    public boolean sendFeeBilling(FeeBilling billing) {
        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<FeeBilling> entity = new HttpEntity<>(billing, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/fees", HttpMethod.POST, entity, String.class);
            log.info("回传费用到 HIS: orderId={}, resp={}", billing.getOrderId(), resp.getStatusCode());
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("回传 HIS 费用失败: orderId={}", billing.getOrderId(), e);
            return false;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + authToken);
        headers.set("Content-Type", "application/json");
        return headers;
    }
}
