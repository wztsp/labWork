package com.lis.report.controller;

import com.lis.common.core.result.R;
import com.lis.report.dto.CriticalRecordDTO;
import com.lis.report.service.CriticalValueService;
import com.lis.report.vo.CriticalRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 危急值 Controller
 */
@RestController
@RequestMapping("/api/report/critical")
@RequiredArgsConstructor
public class CriticalValueController {

    private final CriticalValueService criticalValueService;

    /** 获取危急值列表 (含处理记录: 通知人/通知时间/发送人/发送时间/备注/状态), 支持按日期范围查询 */
    @GetMapping("/list")
    public R<List<CriticalRecordVO>> list(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(criticalValueService.getCriticalList(startDate, endDate));
    }

    /** 触发危急值通知 (MQ 消息) */
    @PostMapping("/{resultId}/alert")
    public R<Boolean> alert(@PathVariable Long resultId) {
        return R.ok(criticalValueService.alertCritical(resultId));
    }

    /** 危急值通知: 保存/更新处理记录并发送通知 (通知人/通知时间/发送人/发送时间/备注) */
    @PostMapping("/{resultId}/notify")
    public R<Boolean> notifyCritical(@PathVariable Long resultId, @RequestBody CriticalRecordDTO dto) {
        return R.ok(criticalValueService.notifyCritical(resultId, dto));
    }

    /** 取消通知: 状态置为已取消(2) */
    @PostMapping("/{resultId}/cancel-notify")
    public R<Boolean> cancelNotify(@PathVariable Long resultId) {
        return R.ok(criticalValueService.cancelNotify(resultId));
    }

    /** 修改危急值处理记录 (可修改通知人/通知时间/发送人/发送时间/备注) */
    @PutMapping("/{resultId}/record")
    public R<Boolean> updateRecord(@PathVariable Long resultId, @RequestBody CriticalRecordDTO dto) {
        return R.ok(criticalValueService.updateRecord(resultId, dto));
    }

    /** 确认危急值 (按 resultId 查询参数) */
    @PutMapping("/confirm")
    public R<Boolean> confirmByParam(@RequestParam Long resultId, @RequestParam Long confirmerId) {
        return R.ok(criticalValueService.confirmCritical(resultId, confirmerId));
    }

    /** 确认危急值 (RESTful 路径参数, 兼容旧接口) */
    @PostMapping("/{resultId}/confirm")
    public R<Boolean> confirm(@PathVariable Long resultId, @RequestParam Long confirmerId) {
        return R.ok(criticalValueService.confirmCritical(resultId, confirmerId));
    }

    /** 检查是否危急值 */
    @GetMapping("/{resultId}/check")
    public R<Boolean> check(@PathVariable Long resultId) {
        return R.ok(criticalValueService.checkCritical(resultId));
    }

    /** 危急值统计: 数量、确认率、平均确认时长 */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats(@RequestParam String startDate,
                                        @RequestParam String endDate) {
        return R.ok(criticalValueService.getStats(startDate, endDate));
    }
}