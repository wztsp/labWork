package com.lis.integration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.integration.entity.HisInterfaceLog;
import com.lis.integration.service.HisInterfaceLogService;
import com.lis.integration.zl.HisStandardService;
import com.lis.integration.zl.ZlHisClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中联新标准服务接口(检验)。
 * <p>
 * HIS 提供的查询/字典/费用服务走这里调用；LIS 回写 HIS 的 S4009/S4010/S4011/S3103
 * 既可自动推送，也可通过这里手工补推。
 */
@RestController
@RequestMapping("/api/his/zl")
@RequiredArgsConstructor
public class HisStandardController {

    private final HisStandardService service;
    private final ZlHisClient client;
    private final HisInterfaceLogService logService;

    @GetMapping("/config")
    public R<Map<String, Object>> config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", client.enabled());
        data.put("url", client.cfg("his.zl.url", ""));
        data.put("sysno", client.cfg("his.zl.sysno", "LIS"));
        data.put("tarno", client.cfg("his.zl.tarno", "ZLHIS"));
        return R.ok(data);
    }

    /**
     * 接口调用日志分页查询（前台 HIS接口日志 页面）
     */
    @GetMapping("/logs")
    public R<PageResult<HisInterfaceLog>> logs(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String serviceNo,
                                               @RequestParam(required = false) Integer direction,
                                               @RequestParam(required = false) Integer success,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String beginTime,
                                               @RequestParam(required = false) String endTime) {
        return R.ok(logService.queryPage(current, size, serviceNo, direction, success,
                keyword, beginTime, endTime));
    }

    // ---------- HIS 提供的查询/字典服务 ----------

    @PostMapping("/s1001/staff")
    public R<JsonNode> staff(@RequestBody(required = false) Map<String, Object> req) {
        return R.ok(service.staff(req));
    }

    @PostMapping("/s1002/depts")
    public R<JsonNode> depts(@RequestBody(required = false) Map<String, Object> req) {
        return R.ok(service.depts(req));
    }

    @PostMapping("/s1003/dept-staff")
    public R<JsonNode> deptStaff(@RequestBody(required = false) Map<String, Object> req) {
        return R.ok(service.deptStaff(req));
    }

    @PostMapping("/s1008/items")
    public R<JsonNode> dictItems(@RequestBody(required = false) Map<String, Object> req) {
        return R.ok(service.dictItems(req));
    }

    @PostMapping("/s1009/fees")
    public R<JsonNode> dictFees(@RequestBody(required = false) Map<String, Object> req) {
        return R.ok(service.dictFees(req));
    }

    @PostMapping("/s3123/patient-orders")
    public R<JsonNode> patientOrders(@RequestBody(required = false) Map<String, Object> req) {
        return R.ok(service.patientOrders(req));
    }

    // ---------- 费用服务 ----------

    @PostMapping("/s3001/fee/create")
    public R<JsonNode> feeCreate(@RequestBody(required = false) JsonNode body) {
        return R.ok(service.feeCreate(body));
    }

    @PostMapping("/s3002/fee/delete")
    public R<JsonNode> feeDelete(@RequestBody(required = false) JsonNode body) {
        return R.ok(service.feeDelete(body));
    }

    @PostMapping("/s3031/fee/status")
    public R<JsonNode> feeStatus(@RequestBody(required = false) Map<String, Object> req) {
        return R.ok(service.feeStatus(req));
    }

    // ---------- LIS 回写 HIS ----------

    @PostMapping("/s4009/sample/{sampleId}")
    public R<Boolean> pushSampleStatus(@PathVariable Long sampleId) {
        return R.ok(service.pushSampleStatus(sampleId));
    }

    @PostMapping("/s4010/report/{reportId}")
    public R<Boolean> pushReport(@PathVariable Long reportId) {
        return R.ok(service.pushReport(reportId));
    }

    @PostMapping("/s4011/report/{reportId}")
    public R<Boolean> cancelReport(@PathVariable Long reportId) {
        return R.ok(service.cancelReport(reportId));
    }

    @PostMapping("/s3103/result/{resultId}")
    public R<Boolean> pushCritical(@PathVariable Long resultId) {
        return R.ok(service.pushCritical(resultId));
    }

    /**
     * 立即执行一轮待推送扫描(标本状态/危急值/取消报告)
     */
    @PostMapping("/push/pending")
    public R<Map<String, Integer>> pushPending() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("sampleStatus", service.pushPendingSampleStatus());
        result.put("critical", service.pushPendingCritical());
        result.put("reportCancel", service.pushPendingReportCancel());
        return R.ok(result);
    }

    // ---------- S4020 查询申请 ----------

    @PostMapping("/s4020/applies/query")
    public R<JsonNode> queryApplies(@RequestBody(required = false) Map<String, Object> req) {
        return R.ok(service.queryApplies(req));
    }

    /**
     * 查询 HIS 检验申请并导入 LIS 为待采集任务，返回导入条数
     */
    @PostMapping("/s4020/applies/pull")
    public R<Integer> pullApplies(@RequestBody(required = false) Map<String, Object> req) {
        return R.ok(service.pullApplies(req));
    }
}
