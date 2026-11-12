package com.lis.reagent.thirdparty.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.thirdparty.dto.FieldMapBatchDTO;
import com.lis.reagent.thirdparty.entity.ThirdPartyConfig;
import com.lis.reagent.thirdparty.entity.ThirdPartyFieldMap;
import com.lis.reagent.thirdparty.entity.ThirdPartyLog;
import com.lis.reagent.thirdparty.entity.ThirdPartyProductMap;
import com.lis.reagent.thirdparty.service.ThirdPartyConfigService;
import com.lis.reagent.thirdparty.service.ThirdPartyFieldMapService;
import com.lis.reagent.thirdparty.service.ThirdPartyLogService;
import com.lis.reagent.thirdparty.service.ThirdPartyProductMapService;
import com.lis.reagent.thirdparty.service.ThirdPartySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 三方卫材对接管理 Controller。
 *
 * <p>包含对接配置、字段映射、商品映射、调用日志，以及出站同步（订单下发、主数据拉取、库存查询）。</p>
 */
@RestController
@RequestMapping("/api/reagent/thirdparty")
@RequiredArgsConstructor
public class ThirdPartyController {

    private final ThirdPartyConfigService configService;
    private final ThirdPartyFieldMapService fieldMapService;
    private final ThirdPartyLogService logService;
    private final ThirdPartyProductMapService productMapService;
    private final ThirdPartySyncService syncService;

    // ==================== 对接配置 ====================

    @GetMapping("/config/page")
    public R<PageResult<ThirdPartyConfig>> configPage(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "10") long size,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer enabled) {
        Page<ThirdPartyConfig> page = configService.queryPage(current, size, keyword, enabled);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/config/list")
    public R<List<ThirdPartyConfig>> configList() {
        return R.ok(configService.listAll());
    }

    @GetMapping("/config/{id}")
    public R<ThirdPartyConfig> configDetail(@PathVariable Long id) {
        return R.ok(configService.getById(id));
    }

    @PostMapping("/config")
    public R<ThirdPartyConfig> configCreate(@RequestBody ThirdPartyConfig config) {
        config.setId(null);
        return R.ok(configService.save(config));
    }

    @PutMapping("/config")
    public R<ThirdPartyConfig> configUpdate(@RequestBody ThirdPartyConfig config) {
        return R.ok(configService.save(config));
    }

    @DeleteMapping("/config/{id}")
    public R<Void> configDelete(@PathVariable Long id) {
        configService.delete(id);
        return R.ok();
    }

    @PostMapping("/config/{id}/test")
    public R<String> configTest(@PathVariable Long id,
                                @RequestParam(required = false) String operatorName) {
        return R.ok(configService.testConnection(id, operatorName));
    }

    // ==================== 字段映射 ====================

    @GetMapping("/field-map")
    public R<List<ThirdPartyFieldMap>> fieldMapList(@RequestParam String partnerCode,
                                                    @RequestParam(required = false) String bizType,
                                                    @RequestParam(required = false) Integer direction) {
        return R.ok(fieldMapService.list(partnerCode, bizType, direction));
    }

    @PostMapping("/field-map")
    public R<Void> fieldMapSave(@RequestBody FieldMapBatchDTO dto) {
        fieldMapService.replace(dto.getPartnerCode(), dto.getBizType(), dto.getDirection(), dto.getMaps());
        return R.ok();
    }

    @DeleteMapping("/field-map/{id}")
    public R<Void> fieldMapDelete(@PathVariable Long id) {
        fieldMapService.delete(id);
        return R.ok();
    }

    // ==================== 商品映射 ====================

    @GetMapping("/product-map/page")
    public R<PageResult<ThirdPartyProductMap>> productMapPage(@RequestParam(defaultValue = "1") long current,
                                                              @RequestParam(defaultValue = "10") long size,
                                                              @RequestParam(required = false) String partnerCode,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer mapped) {
        Page<ThirdPartyProductMap> page = productMapService.queryPage(current, size, partnerCode, keyword, mapped);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @PostMapping("/product-map")
    public R<ThirdPartyProductMap> productMapSave(@RequestBody ThirdPartyProductMap map) {
        return R.ok(productMapService.save(map));
    }

    @DeleteMapping("/product-map/{id}")
    public R<Void> productMapDelete(@PathVariable Long id) {
        productMapService.delete(id);
        return R.ok();
    }

    @PostMapping("/product-map/auto-match")
    public R<Integer> productMapAutoMatch(@RequestParam String partnerCode) {
        return R.ok(productMapService.autoMatch(partnerCode));
    }

    // ==================== 调用日志 ====================

    @GetMapping("/log/page")
    public R<PageResult<ThirdPartyLog>> logPage(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String partnerCode,
                                                @RequestParam(required = false) String bizType,
                                                @RequestParam(required = false) Integer direction,
                                                @RequestParam(required = false) Integer success,
                                                @RequestParam(required = false) String keyword) {
        Page<ThirdPartyLog> page = logService.queryPage(current, size, partnerCode, bizType, direction, success, keyword);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    // ==================== 出站同步 ====================

    /** 采购订单下发 */
    @PostMapping("/sync/order/{orderId}")
    public R<Map<String, Object>> pushOrder(@PathVariable Long orderId,
                                            @RequestParam String partnerCode,
                                            @RequestParam(required = false) String operatorName) {
        return R.ok(syncService.pushOrder(partnerCode, orderId, operatorName));
    }

    /** 拉取商品主数据 */
    @PostMapping("/sync/products")
    public R<Integer> pullProducts(@RequestParam String partnerCode,
                                   @RequestParam(required = false) String operatorName) {
        return R.ok(syncService.pullProducts(partnerCode, operatorName));
    }

    /** 拉取供应商主数据 */
    @PostMapping("/sync/suppliers")
    public R<Integer> pullSuppliers(@RequestParam String partnerCode,
                                    @RequestParam(required = false) String operatorName) {
        return R.ok(syncService.pullSuppliers(partnerCode, operatorName));
    }

    /** 查询对方库存与效期 */
    @GetMapping("/sync/stock")
    public R<List<Map<String, Object>>> queryStock(@RequestParam String partnerCode,
                                                   @RequestParam String reagentCode,
                                                   @RequestParam(required = false) String operatorName) {
        return R.ok(syncService.queryStock(partnerCode, reagentCode, operatorName));
    }
}
