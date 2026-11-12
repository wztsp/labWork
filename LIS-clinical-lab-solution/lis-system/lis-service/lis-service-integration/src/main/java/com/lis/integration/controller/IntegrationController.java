package com.lis.integration.controller;

import com.lis.api.dto.OrderDTO;
import com.lis.common.core.result.R;
import com.lis.integration.adapter.HisAdapter;
import com.lis.integration.adapter.HisAdapterFactory;
import com.lis.integration.dto.CriticalValueAlert;
import com.lis.integration.dto.FeeBilling;
import com.lis.integration.dto.LabReport;
import com.lis.integration.dto.PatientInfo;
import com.lis.integration.dto.SampleStatusUpdate;
import com.lis.integration.message.HL7MessageBuilder;
import com.lis.integration.message.HL7MessageParser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * HIS 对接 Controller
 */
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final HisAdapterFactory adapterFactory;
    private final HL7MessageParser hl7MessageParser;
    private final HL7MessageBuilder hl7MessageBuilder;
    private final com.lis.integration.service.HisResultPushService hisResultPushService;

    @Value("${integration.his.default-vendor:UNIVERSAL_REST}")
    private String defaultVendor;

    /** 获取可用适配器列表 */
    @GetMapping("/adapters")
    public R<Set<String>> adapters() {
        return R.ok(adapterFactory.listAdapterIds());
    }

    /** 健康检查 */
    @GetMapping("/health/{adapterId}")
    public R<Boolean> health(@PathVariable String adapterId) {
        return R.ok(adapterFactory.getAdapter(adapterId).healthCheck());
    }

    /** 手动触发申请同步 (使用默认适配器) */
    @PostMapping("/sync/orders")
    public R<List<OrderDTO>> syncOrders(@RequestParam(required = false) Long startTime,
                                        @RequestParam(required = false) Long endTime) {
        HisAdapter adapter = adapterFactory.getAdapter(defaultVendor);
        Date start = startTime == null ? new Date(System.currentTimeMillis() - 3600_000L) : new Date(startTime);
        Date end = endTime == null ? new Date() : new Date(endTime);
        return R.ok(adapter.receiveOrders(start, end));
    }

    /** 手动触发结果回传 */
    @PostMapping("/sync/results")
    public R<Boolean> syncResults(@RequestBody LabReport report,
                                  @RequestParam(required = false) String adapterId) {
        HisAdapter adapter = adapterFactory.getAdapter(
                adapterId == null ? defaultVendor : adapterId);
        return R.ok(adapter.sendResults(report));
    }

    /**
     * 按 HIS 4010 报文格式补推检验结果:
     * 不传 reportId 时批量推送待同步报告, 返回成功条数。
     */
    @PostMapping("/sync/results/push")
    public R<Integer> pushHisResults(@RequestParam(required = false) Long reportId) {
        if (reportId != null) {
            return R.ok(hisResultPushService.pushReport(reportId) ? 1 : 0);
        }
        return R.ok(hisResultPushService.pushPending());
    }

    /** 查询患者信息 */
    @GetMapping("/patients/{patientId}")
    public R<PatientInfo> queryPatient(@PathVariable String patientId,
                                       @RequestParam(required = false) String visitId,
                                       @RequestParam(required = false) String adapterId) {
        HisAdapter adapter = adapterFactory.getAdapter(
                adapterId == null ? defaultVendor : adapterId);
        return R.ok(adapter.queryPatient(patientId, visitId));
    }

    /** 回传标本状态 */
    @PostMapping("/samples/status")
    public R<Boolean> sendSampleStatus(@RequestBody SampleStatusUpdate update,
                                       @RequestParam(required = false) String adapterId) {
        HisAdapter adapter = adapterFactory.getAdapter(
                adapterId == null ? defaultVendor : adapterId);
        return R.ok(adapter.sendSampleStatus(update));
    }

    /** 回传危急值 */
    @PostMapping("/critical-alerts")
    public R<Boolean> sendCriticalAlert(@RequestBody CriticalValueAlert alert,
                                        @RequestParam(required = false) String adapterId) {
        HisAdapter adapter = adapterFactory.getAdapter(
                adapterId == null ? defaultVendor : adapterId);
        return R.ok(adapter.sendCriticalValueAlert(alert));
    }

    /** 费用回传 */
    @PostMapping("/fees")
    public R<Boolean> sendFeeBilling(@RequestBody FeeBilling billing,
                                     @RequestParam(required = false) String adapterId) {
        HisAdapter adapter = adapterFactory.getAdapter(
                adapterId == null ? defaultVendor : adapterId);
        return R.ok(adapter.sendFeeBilling(billing));
    }

    /** 接收 HIS HL7 消息 (ORM^O01 申请 / ADT^A01 患者) */
    @PostMapping("/hl7/receive")
    public R<String> receiveHl7(@RequestBody String hl7Message,
                                @RequestParam(defaultValue = "ORM_O01") String messageType) {
        switch (messageType.toUpperCase()) {
            case "ORM_O01" -> {
                OrderDTO order = hl7MessageParser.parseORM_O01(hl7Message);
                return R.ok("ORM^O01 解析成功: orderNo=" + (order == null ? null : order.getOrderNo()));
            }
            case "ADT_A01" -> {
                PatientInfo info = hl7MessageParser.parseADT_A01(hl7Message);
                return R.ok("ADT^A01 解析成功: patientId=" + (info == null ? null : info.getPatientId()));
            }
            default -> {
                return R.fail("不支持的消息类型: " + messageType);
            }
        }
    }
}
