package com.lis.reagent.controller;

import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.R;
import com.lis.reagent.dto.InboundScanDTO;
import com.lis.reagent.entity.ReagentInbound;
import com.lis.reagent.entity.ReagentInboundItem;
import com.lis.reagent.service.ReagentInboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫码入库 Controller：条码解析、规则生成、单码/别名扫码建单。
 */
@Slf4j
@RestController
@RequestMapping("/api/reagent/inbound/scan")
@RequiredArgsConstructor
public class ReagentInboundScanController {

    /** 规则扫码入库方式 */
    private static final String MODE_RULE_SCAN = "RULE_SCAN";
    /** 单码扫码入库方式 */
    private static final String MODE_SINGLE_SCAN = "SINGLE_SCAN";
    /** 条码别名扫码入库方式 */
    private static final String MODE_ALIAS_SCAN = "ALIAS_SCAN";

    private final ReagentInboundService inboundService;

    /**
     * 扫码解析：先按试剂条码别名，再按库存批次条码匹配。
     */
    @GetMapping("/resolve")
    public R<Map<String, Object>> resolve(@RequestParam String code) {
        return R.ok(inboundService.resolveScanCode(code));
    }

    /**
     * 按“前缀 + 4 位序号”批量生成条码并创建待验收入库单。
     */
    @PostMapping("/rule")
    public R<Map<String, Object>> rule(@RequestBody InboundScanDTO dto) {
        if (dto.getPrefix() == null || dto.getPrefix().trim().isEmpty()) {
            throw new BusinessException("条码前缀不能为空");
        }
        if (dto.getCount() == null || dto.getCount() <= 0) {
            throw new BusinessException("生成条码数量必须大于 0");
        }
        if (dto.getReagentId() == null) {
            throw new BusinessException("请选择试剂");
        }
        int startSeq = dto.getStartSeq() == null ? 1 : dto.getStartSeq();
        BigDecimal quantity = requireQuantity(dto);
        List<ReagentInboundItem> items = new ArrayList<>();
        List<String> barcodes = new ArrayList<>();
        for (int i = 0; i < dto.getCount(); i++) {
            String barcode = dto.getPrefix().trim() + String.format("%04d", startSeq + i);
            barcodes.add(barcode);
            items.add(buildItem(dto, dto.getReagentId(), dto.getBatchNo(), barcode, quantity));
        }
        ReagentInbound inbound = inboundService.createScanInbound(MODE_RULE_SCAN, items, dto.getRemark());
        return R.ok(buildResult(inbound, barcodes, MODE_RULE_SCAN));
    }

    /**
     * 单码扫码：每个条码一条明细。
     */
    @PostMapping("/single")
    public R<Map<String, Object>> single(@RequestBody InboundScanDTO dto) {
        if (dto.getBarcodes() == null || dto.getBarcodes().isEmpty()) {
            throw new BusinessException("条码不能为空");
        }
        if (dto.getReagentId() == null) {
            throw new BusinessException("请选择试剂");
        }
        BigDecimal quantity = requireQuantity(dto);
        List<ReagentInboundItem> items = new ArrayList<>();
        List<String> barcodes = new ArrayList<>();
        for (String barcode : dto.getBarcodes()) {
            if (barcode == null || barcode.trim().isEmpty()) {
                continue;
            }
            barcodes.add(barcode.trim());
            items.add(buildItem(dto, dto.getReagentId(), dto.getBatchNo(), barcode.trim(), quantity));
        }
        if (items.isEmpty()) {
            throw new BusinessException("条码不能为空");
        }
        ReagentInbound inbound = inboundService.createScanInbound(MODE_SINGLE_SCAN, items, dto.getRemark());
        return R.ok(buildResult(inbound, barcodes, MODE_SINGLE_SCAN));
    }

    /**
     * 条码别名扫码：先逐码解析试剂/批次，再生成明细。
     */
    @PostMapping("/alias")
    public R<Map<String, Object>> alias(@RequestBody InboundScanDTO dto) {
        if (dto.getCodes() == null || dto.getCodes().isEmpty()) {
            throw new BusinessException("条码不能为空");
        }
        BigDecimal quantity = dto.getQuantity() == null ? BigDecimal.ONE : dto.getQuantity();
        List<ReagentInboundItem> items = new ArrayList<>();
        List<String> barcodes = new ArrayList<>();
        for (String code : dto.getCodes()) {
            if (code == null || code.trim().isEmpty()) {
                continue;
            }
            String barcode = code.trim();
            // 复用统一解析逻辑，未匹配时抛出“条码未匹配到试剂”
            Map<String, Object> resolved = inboundService.resolveScanCode(barcode);
            Long reagentId = (Long) resolved.get("reagentId");
            String lotNo = dto.getBatchNo() != null && !dto.getBatchNo().trim().isEmpty()
                    ? dto.getBatchNo() : (String) resolved.get("batchNo");
            barcodes.add(barcode);
            items.add(buildItem(dto, reagentId, lotNo, barcode, quantity));
        }
        if (items.isEmpty()) {
            throw new BusinessException("条码不能为空");
        }
        ReagentInbound inbound = inboundService.createScanInbound(MODE_ALIAS_SCAN, items, dto.getRemark());
        return R.ok(buildResult(inbound, barcodes, MODE_ALIAS_SCAN));
    }

    /** 构造入库明细（每个条码一条）。 */
    private ReagentInboundItem buildItem(InboundScanDTO dto, Long reagentId, String lotNo,
                                         String barcode, BigDecimal quantity) {
        ReagentInboundItem item = new ReagentInboundItem();
        item.setReagentId(reagentId);
        item.setLotNo(lotNo);
        item.setBarcode(barcode);
        item.setQuantity(quantity);
        item.setUnit(dto.getUnit());
        item.setPurchasePrice(dto.getPurchasePrice());
        item.setExpiryDate(dto.getExpiryDate());
        item.setWarehouseId(dto.getWarehouseId());
        item.setRemark(dto.getRemark());
        return item;
    }

    /** 数量校验：扫码入库每条码必须有正数量。 */
    private BigDecimal requireQuantity(InboundScanDTO dto) {
        if (dto.getQuantity() == null || dto.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("入库数量必须大于 0");
        }
        return dto.getQuantity();
    }

    /** 统一返回入库单 ID、单号及条码列表。 */
    private Map<String, Object> buildResult(ReagentInbound inbound, List<String> barcodes, String inboundMode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inboundId", inbound.getId());
        result.put("inboundNo", inbound.getInboundNo());
        result.put("inboundMode", inboundMode);
        result.put("barcodes", barcodes);
        return result;
    }
}
