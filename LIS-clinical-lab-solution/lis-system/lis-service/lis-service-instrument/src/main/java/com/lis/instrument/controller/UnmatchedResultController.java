package com.lis.instrument.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.api.dto.UnmatchedBatchToQcDTO;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.instrument.entity.InstrumentUnmatchedResult;
import com.lis.instrument.service.UnmatchedResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 仪器「无主标本」结果管理 Controller（技师工作站手工认领/调整）。
 *
 * <p>仪器上传的结果在条码/样本号匹配不到业务标本时，先落库为「无主标本」；
 * 技师可在工作站查看、手工调整字段，并按业务条码认领匹配回正式结果。</p>
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/instrument/unmatched")
@RequiredArgsConstructor
public class UnmatchedResultController {

    private final UnmatchedResultService unmatchedResultService;

    /** 分页查询无主标本结果（兼容前端参数名 pageNum/pageSize 与后端 current/size） */
    @GetMapping("/page")
    public R<PageResult<InstrumentUnmatchedResult>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long pageNum,
            @RequestParam(required = false) Long pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long instrumentId) {
        long actualCurrent = (pageNum != null && pageNum > 0) ? pageNum : current;
        long actualSize = (pageSize != null && pageSize > 0) ? pageSize : size;
        Page<InstrumentUnmatchedResult> page = unmatchedResultService.page(actualCurrent, actualSize, status, instrumentId);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 待处理（无主）数量，用于界面角标 */
    @GetMapping("/pending-count")
    public R<Long> pendingCount() {
        return R.ok(unmatchedResultService.page(1, 1, 0, null).getTotal());
    }

    /** 手工调整无主标本结果字段 */
    @PutMapping("/{id}")
    public R<InstrumentUnmatchedResult> update(@PathVariable Long id,
                                               @RequestBody InstrumentUnmatchedResult dto) {
        return R.ok(unmatchedResultService.updateResult(id, dto));
    }

    /** 按条码/样本号匹配到业务标本并落库为正式结果 */
    @PostMapping("/{id}/match")
    public R<Long> match(@PathVariable Long id,
                         @RequestBody Map<String, String> body) {
        String sampleBarcode = body == null ? null : body.get("sampleBarcode");
        return R.ok(unmatchedResultService.matchToSample(id, sampleBarcode));
    }

    /** 批量匹配一组无主标本结果到同一业务标本（标本卡片一键认领） */
    @PostMapping("/batch-match")
    public R<Integer> batchMatch(@RequestBody Map<String, Object> body) {
        Object rawIds = body == null ? null : body.get("ids");
        String sampleBarcode = body == null ? null : (String) body.get("sampleBarcode");
        List<Long> ids = parseIds(rawIds);
        return R.ok(unmatchedResultService.matchBatchToSample(ids, sampleBarcode));
    }

    /** 作废无主标本结果 */
    @PostMapping("/{id}/discard")
    public R<Void> discard(@PathVariable Long id) {
        unmatchedResultService.discard(id);
        return R.ok();
    }

    /** 批量作废一组无主标本结果（标本卡片一键作废） */
    @PostMapping("/batch-discard")
    public R<Void> batchDiscard(@RequestBody Map<String, Object> body) {
        Object rawIds = body == null ? null : body.get("ids");
        List<Long> ids = parseIds(rawIds);
        unmatchedResultService.discardBatch(ids);
        return R.ok();
    }

    /**
     * 批量将无主标本结果转为质控记录。
     * <p>
     * 前端场景：无主标本弹窗 / 标本卡片 → 「转质控」按钮 → 选择质控品批号 → 调用本接口
     * 将 resultValue 作为质控测量值生成 lis_qc_record 并执行 Westgard 规则判定，
     * 无主记录状态更新为 3=已转质控。
     *
     * @return {successCount, failCount, generatedQcCount, failDetails:[{id,reason}]}
     */
    @PostMapping("/batch-to-qc")
    public R<Map<String, Object>> batchToQc(@RequestBody UnmatchedBatchToQcDTO dto) {
        return R.ok(unmatchedResultService.batchConvertToQc(dto));
    }

    // ===== 私有辅助 =====
    @SuppressWarnings("unchecked")
    private List<Long> parseIds(Object rawIds) {
        List<Long> ids = new java.util.ArrayList<>();
        if (rawIds == null) return ids;
        if (rawIds instanceof List) {
            for (Object o : (List<Object>) rawIds) {
                if (o == null) continue;
                try { ids.add(Long.parseLong(String.valueOf(o).trim())); } catch (Exception ignore) {}
            }
        } else if (rawIds instanceof String) {
            String s = (String) rawIds;
            for (String p : s.split("[,，;；\\s]+")) {
                if (p == null || p.isEmpty()) continue;
                try { ids.add(Long.parseLong(p.trim())); } catch (Exception ignore) {}
            }
        } else if (rawIds instanceof Number) {
            ids.add(((Number) rawIds).longValue());
        }
        return ids;
    }
}