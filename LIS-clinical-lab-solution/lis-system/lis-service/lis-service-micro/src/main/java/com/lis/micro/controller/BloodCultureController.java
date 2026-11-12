package com.lis.micro.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.micro.entity.BloodCulture;
import com.lis.micro.entity.BloodCultureSubculture;
import com.lis.micro.service.BloodCultureService;
import com.lis.micro.service.BloodCultureSubcultureService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 血培养 Controller
 */
@RestController
@RequestMapping("/api/micro/blood-culture")
@RequiredArgsConstructor
public class BloodCultureController {

    private final BloodCultureService bloodCultureService;
    private final BloodCultureSubcultureService subcultureService;

    @GetMapping
    public R<PageResult<BloodCulture>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Long sampleId,
                                             @RequestParam(required = false) Integer status) {
        Page<BloodCulture> page = bloodCultureService.queryPage(current, size, sampleId, status);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/culturing")
    public R<List<BloodCulture>> listCulturing() {
        return R.ok(bloodCultureService.listCulturing());
    }

    @GetMapping("/{id}")
    public R<BloodCulture> getById(@PathVariable Long id) {
        return R.ok(bloodCultureService.getById(id));
    }

    @GetMapping("/sample/{sampleId}")
    public R<BloodCulture> getBySampleId(@PathVariable Long sampleId) {
        return R.ok(bloodCultureService.getBySampleId(sampleId));
    }

    @PostMapping("/inoculate")
    public R<BloodCulture> inoculate(@RequestBody BloodCulture bloodCulture) {
        return R.ok(bloodCultureService.inoculate(bloodCulture));
    }

    @PostMapping("/{id}/alarm")
    public R<BloodCulture> receiveAlarm(@PathVariable Long id,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime alarmTime,
                                         @RequestParam(required = false) Long operatorId) {
        return R.ok(bloodCultureService.receiveAlarm(id, alarmTime, operatorId));
    }

    @PostMapping("/{id}/final")
    public R<BloodCulture> reportFinal(@PathVariable Long id,
                                        @RequestParam String finalResult,
                                        @RequestParam(required = false) Long operatorId) {
        return R.ok(bloodCultureService.reportFinal(id, finalResult, operatorId));
    }

    /**
     * 转种记录
     */
    @GetMapping("/{id}/subcultures")
    public R<List<BloodCultureSubculture>> listSubcultures(@PathVariable Long id) {
        return R.ok(subcultureService.listByBloodCultureId(id));
    }

    @PostMapping("/{id}/subcultures")
    public R<BloodCultureSubculture> addSubculture(@PathVariable Long id,
                                                    @RequestBody BloodCultureSubculture subculture) {
        subculture.setBloodCultureId(id);
        return R.ok(subcultureService.addSubculture(subculture));
    }
}
