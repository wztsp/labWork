package com.lis.report.controller;

import com.lis.api.vo.MicroReportVO;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.report.entity.MicroAlert;
import com.lis.report.service.MicroReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 微生物报告 Controller
 */
@RestController
@RequestMapping("/api/report/micro")
@RequiredArgsConstructor
public class MicroReportController {

    private final MicroReportService microReportService;

    /** 创建微生物报告 */
    @PostMapping
    public R<MicroReportVO> create(@RequestBody MicroReportVO vo) {
        return R.ok(microReportService.createReport(vo));
    }

    /** 仪器导入: 一次性创建报告+细菌鉴定+药敏结果 */
    @PostMapping("/import")
    public R<MicroReportVO> importReport(@RequestBody MicroReportVO vo) {
        return R.ok(microReportService.importReport(vo));
    }

    /** 通信程序回传结果到指定报告 (upsert 细菌/药敏, 按编码/名称匹配去重) */
    @PostMapping("/{id:\\d+}/instrument-results")
    public R<MicroReportVO> pushInstrumentResults(@PathVariable Long id,
                                                   @RequestBody MicroReportVO vo) {
        return R.ok(microReportService.pushInstrumentResults(id, vo));
    }

    /** 通信程序回传结果 (按培养条码/采集条码匹配目标报告, 匹配成功再写入) */
    @PostMapping("/instrument-results/by-barcode")
    public R<MicroReportVO> pushInstrumentResultsByBarcode(@RequestParam(required = false) String plateBarcode,
                                                           @RequestParam(required = false) String sampleBarcode,
                                                           @RequestBody MicroReportVO vo) {
        return R.ok(microReportService.pushInstrumentResultsByBarcode(plateBarcode, sampleBarcode, vo));
    }

    /** 查询微生物报告详情 */
    @GetMapping("/{id:\\d+}")
    public R<MicroReportVO> getById(@PathVariable Long id) {
        return R.ok(microReportService.getById(id));
    }

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<MicroReportVO>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) Integer reportStatus,
            @RequestParam(required = false) String beginDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String reportNo,
            @RequestParam(required = false) String sampleBarcode,
            @RequestParam(required = false) Long patientId) {
        return R.ok(microReportService.queryPage(current, size, patientName, reportStatus, beginDate, endDate,
                keyword, reportNo, sampleBarcode, patientId));
    }

    /**
     * 通过培养基条码核收
     * - 查找培养皿条码对应的标本
     * - 没有微生物报告则自动创建一条(初步报告状态)
     * - 返回对应报告 VO
     */
    @PostMapping("/receive-by-plate")
    public R<MicroReportVO> receiveByPlate(@RequestParam String plateBarcode) {
        return R.ok(microReportService.receiveByPlateBarcode(plateBarcode));
    }

    /** 撤销核收（取消已核收的初步报告）
     * - 仅允许撤销「初步报告」状态
     * - 回滚关联培养基条码的核收状态为未核收
     * - 删除该报告及子表数据，标本可重新核收
     */
    @DeleteMapping("/{id:\\d+}/cancel-receive")
    public R<Void> cancelReceive(@PathVariable Long id) {
        microReportService.cancelReceive(id);
        return R.ok();
    }

    /** 取消最终报告：将「最终报告」回退为「鉴定报告」，恢复可编辑、可重新发布 */
    @PostMapping("/{id:\\d+}/revoke-final")
    public R<MicroReportVO> revokeFinal(@PathVariable Long id) {
        return R.ok(microReportService.revokeFinalReport(id));
    }

    /** 添加细菌鉴定 */
    @PostMapping("/{id:\\d+}/organisms")
    public R<MicroReportVO.OrganismVO> addOrganism(@PathVariable Long id,
                                                   @RequestBody MicroReportVO.OrganismVO organismVO) {
        return R.ok(microReportService.addOrganism(id, organismVO));
    }

    /** 手动触发指定报告的耐药规则评估 */
    @PostMapping("/{id:\\d+}/evaluate-rules")
    public R<List<MicroAlert>> evaluateRules(@PathVariable Long id) {
        return R.ok(microReportService.evaluateExpertRules(id));
    }

    /** 计算指定报告的专家/耐药规则命中项(只读, 用于报告详情标注) */
    @GetMapping("/{id:\\d+}/rule-hits")
    public R<List<MicroAlert>> ruleHits(@PathVariable Long id) {
        return R.ok(microReportService.computeRuleHits(id));
    }

    /** 修改细菌鉴定 */
    @PutMapping("/{id:\\d+}/organisms/{organismId:\\d+}")
    public R<MicroReportVO.OrganismVO> updateOrganism(@PathVariable Long id,
                                                      @PathVariable Long organismId,
                                                      @RequestBody MicroReportVO.OrganismVO organismVO) {
        return R.ok(microReportService.updateOrganism(id, organismId, organismVO));
    }

    /** 删除细菌鉴定 (同时删除关联药敏结果) */
    @DeleteMapping("/{id:\\d+}/organisms/{organismId:\\d+}")
    public R<Void> deleteOrganism(@PathVariable Long id, @PathVariable Long organismId) {
        microReportService.deleteOrganism(id, organismId);
        return R.ok();
    }

    /** 添加药敏结果 */
    @PostMapping("/{id:\\d+}/sensitivities")
    public R<MicroReportVO.SensitivityVO> addSensitivity(@PathVariable Long id,
                                                         @RequestBody MicroReportVO.SensitivityVO sensitivityVO) {
        return R.ok(microReportService.addSensitivity(id, sensitivityVO));
    }

    /** 修改药敏结果 */
    @PutMapping("/{id:\\d+}/sensitivities/{sensitivityId:\\d+}")
    public R<MicroReportVO.SensitivityVO> updateSensitivity(@PathVariable Long id,
                                                             @PathVariable Long sensitivityId,
                                                             @RequestBody MicroReportVO.SensitivityVO sensitivityVO) {
        return R.ok(microReportService.updateSensitivity(id, sensitivityId, sensitivityVO));
    }

    /** 删除药敏结果 */
    @DeleteMapping("/{id:\\d+}/sensitivities/{sensitivityId:\\d+}")
    public R<Void> deleteSensitivity(@PathVariable Long id, @PathVariable Long sensitivityId) {
        microReportService.deleteSensitivity(id, sensitivityId);
        return R.ok();
    }

    /**
     * 发布报告 (多级: 初步 -> 鉴定 -> 最终)
     * 兼容 body 与 query 两种传参方式
     */
    @PutMapping("/{id:\\d+}/publish")
    public R<MicroReportVO> publish(@PathVariable Long id,
                                    @RequestParam(required = false) Integer targetStatus,
                                    @RequestParam(required = false) Long auditorId,
                                    @RequestBody(required = false) MicroReportVO body) {
        int target = targetStatus != null ? targetStatus : (body != null && body.getReportStatus() != null ? body.getReportStatus() : 3);
        long auditor = auditorId != null ? auditorId : (body != null && body.getAuditorId() != null ? body.getAuditorId() : 1L);
        return R.ok(microReportService.publishReport(id, target, auditor));
    }

    /** 保存革兰氏染色结果 */
    @PutMapping("/{id:\\d+}/gram-stain")
    public R<MicroReportVO> saveGramStain(@PathVariable Long id, @RequestBody MicroReportVO body) {
        return R.ok(microReportService.saveGramStain(id, body));
    }

    /** 保存阴性报告 */
    @PutMapping("/{id:\\d+}/negative")
    public R<MicroReportVO> saveNegativeReport(@PathVariable Long id, @RequestBody MicroReportVO body) {
        return R.ok(microReportService.saveNegativeReport(id, body));
    }

    /** 保存血培养信息 */
    @PutMapping("/{id:\\d+}/blood-culture")
    public R<MicroReportVO> saveBloodCulture(@PathVariable Long id, @RequestBody MicroReportVO body) {
        return R.ok(microReportService.saveBloodCulture(id, body));
    }

    /** 添加转种记录 */
    @PostMapping("/{id:\\d+}/subcultures")
    public R<MicroReportVO.SubcultureVO> addSubculture(@PathVariable Long id,
                                                        @RequestBody MicroReportVO.SubcultureVO body) {
        return R.ok(microReportService.addSubculture(id, body));
    }

    /** 删除转种记录 */
    @DeleteMapping("/{id:\\d+}/subcultures/{subId:\\d+}")
    public R<Void> deleteSubculture(@PathVariable Long id, @PathVariable Long subId) {
        microReportService.deleteSubculture(id, subId);
        return R.ok();
    }
}
