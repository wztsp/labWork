package com.lis.base.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.base.entity.Instrument;
import com.lis.base.entity.InstrumentItemMap;
import com.lis.base.entity.InstrumentSpecimenMap;
import com.lis.base.entity.InstrumentStaffMap;
import com.lis.base.service.InstrumentService;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 仪器 Controller
 */
@RestController("baseInstrumentController")
@RequestMapping("/api/base/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final InstrumentService instrumentService;

    @GetMapping
    public R<PageResult<Instrument>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword) {
        Page<Instrument> page = instrumentService.queryPage(current, size, keyword);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/{id}")
    public R<Instrument> getById(@PathVariable Long id) {
        return R.ok(instrumentService.getDetail(id));
    }

    /**
     * 查询当前登录员工可操作的启用仪器（用于技师工作站等有操作权限范围的场景）
     */
    @GetMapping("/mine")
    public R<List<Instrument>> listMine(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(instrumentService.listMyInstruments(userId));
    }

    /**
     * 查询全部启用的仪器 (下拉选择用)
     */
    @GetMapping("/all")
    public R<List<Instrument>> listAll() {
        return R.ok(instrumentService.listAllEnabled());
    }

    @PostMapping
    public R<Instrument> create(@RequestBody Instrument instrument) {
        return R.ok(instrumentService.create(instrument));
    }

    @PutMapping
    public R<Instrument> update(@RequestBody Instrument instrument) {
        return R.ok(instrumentService.update(instrument));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        instrumentService.delete(id);
        return R.ok();
    }

    /**
     * 查询仪器项目映射
     */
    @GetMapping("/{id}/items")
    public R<List<InstrumentItemMap>> listItemMaps(@PathVariable Long id) {
        return R.ok(instrumentService.listItemMaps(id));
    }

    /**
     * 批量设置仪器项目映射
     */
    @PostMapping("/{id}/items")
    public R<Void> setItemMaps(@PathVariable Long id, @RequestBody List<InstrumentItemMap> maps) {
        instrumentService.setItemMaps(id, maps);
        return R.ok();
    }

    /**
     * 查询仪器关联的操作人员
     */
    @GetMapping("/{id}/staff")
    public R<List<InstrumentStaffMap>> listStaff(@PathVariable Long id) {
        return R.ok(instrumentService.listStaffMaps(id));
    }

    /**
     * 设置仪器关联的操作人员（覆盖式）
     */
    @PostMapping("/{id}/staff")
    public R<Void> setStaff(@PathVariable Long id, @RequestBody List<InstrumentStaffMap> staffMaps) {
        instrumentService.setStaffMaps(id, staffMaps);
        return R.ok();
    }

    /**
     * 查询仪器关联的标本类型
     */
    @GetMapping("/{id}/specimens")
    public R<List<InstrumentSpecimenMap>> listSpecimens(@PathVariable Long id) {
        return R.ok(instrumentService.listSpecimenMaps(id));
    }

    /**
     * 设置仪器关联的标本类型（覆盖式）
     */
    @PostMapping("/{id}/specimens")
    public R<Void> setSpecimens(@PathVariable Long id, @RequestBody List<InstrumentSpecimenMap> specimenMaps) {
        instrumentService.setSpecimenMaps(id, specimenMaps);
        return R.ok();
    }
}
