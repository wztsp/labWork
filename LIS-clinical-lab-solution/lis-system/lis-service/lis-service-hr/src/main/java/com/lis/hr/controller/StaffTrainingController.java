package com.lis.hr.controller;

import com.lis.common.core.result.R;
import com.lis.hr.entity.StaffTraining;
import com.lis.hr.service.StaffTrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 培训记录 Controller
 */
@RestController
@RequestMapping("/api/hr/training")
@RequiredArgsConstructor
public class StaffTrainingController {

    private final StaffTrainingService trainingService;

    /**
     * 全部培训记录 (管理页面用)
     */
    @GetMapping
    public R<List<StaffTraining>> listAll() {
        return R.ok(trainingService.list());
    }

    @GetMapping("/staff/{staffId}")
    public R<List<StaffTraining>> listByStaff(@PathVariable Long staffId) {
        return R.ok(trainingService.listByStaffId(staffId));
    }

    @PostMapping
    public R<StaffTraining> create(@RequestBody StaffTraining training) {
        return R.ok(trainingService.create(training));
    }

    @PutMapping
    public R<StaffTraining> update(@RequestBody StaffTraining training) {
        return R.ok(trainingService.update(training));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        trainingService.delete(id);
        return R.ok();
    }
}
