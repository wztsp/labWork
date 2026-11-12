package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.entity.ReagentScrapDisposal;
import com.lis.reagent.service.ReagentScrapDisposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 报废单环保处置信息 Controller（T-15 高危审批 + 环保处置）。
 *
 * <p>处置信息与报废单 1:1，按 {@code reqId} 幂等 upsert；
 * 危险品报废单（reqType=3 且 hazardFlag=1）执行前强制校验四要素已登记。</p>
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/scrap-disposal")
@RequiredArgsConstructor
public class ReagentScrapDisposalController {

    private final ReagentScrapDisposalService scrapDisposalService;

    /** 按报废单查询处置信息（无则返回 null） */
    @GetMapping("/by-req/{reqId}")
    public R<ReagentScrapDisposal> getByReq(@PathVariable Long reqId) {
        return R.ok(scrapDisposalService.getByReqId(reqId));
    }

    /** 保存 / 更新处置信息（按 reqId 幂等 upsert） */
    @PostMapping
    public R<ReagentScrapDisposal> save(@RequestBody ReagentScrapDisposal dto) {
        return R.ok(scrapDisposalService.save(dto));
    }

    /** 分页查询处置台账 */
    @GetMapping("/page")
    public R<PageResult<ReagentScrapDisposal>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size) {
        Page<ReagentScrapDisposal> page = scrapDisposalService.page(current, size);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 删除处置登记 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        scrapDisposalService.deleteById(id);
        return R.ok();
    }

    /** 导出危险品处置台账（制表符分隔文本，Content-Type application/vnd.ms-excel） */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        List<ReagentScrapDisposal> records = scrapDisposalService.page(1, 100000).getRecords();
        StringBuilder sb = new StringBuilder(
                "reqId\tdisposalMethod\tdisposalOrg\tdisposalCert\tdisposalDate\thazardFlag\thazardClass"
                        + "\tsafetyApproverName\tremark\tcreateTime\n");
        for (ReagentScrapDisposal d : records) {
            sb.append(nvl(d.getReqId())).append('\t')
                    .append(nvl(d.getDisposalMethod())).append('\t')
                    .append(nvl(d.getDisposalOrg())).append('\t')
                    .append(nvl(d.getDisposalCert())).append('\t')
                    .append(nvl(d.getDisposalDate())).append('\t')
                    .append(nvl(d.getHazardFlag())).append('\t')
                    .append(nvl(d.getHazardClass())).append('\t')
                    .append(nvl(d.getSafetyApproverName())).append('\t')
                    .append(nvl(d.getRemark())).append('\t')
                    .append(nvl(d.getCreateTime())).append('\n');
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.ms-excel")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=scrap_disposal_ledger.xls")
                .body(body);
    }

    private static String nvl(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}