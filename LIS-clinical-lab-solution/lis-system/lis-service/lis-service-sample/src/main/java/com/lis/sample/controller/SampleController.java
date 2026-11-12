package com.lis.sample.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.api.dto.SampleDTO;
import com.lis.api.dto.SampleQueryDTO;
import com.lis.api.dto.SampleReceiveDTO;
import com.lis.api.dto.SampleCollectDTO;
import com.lis.api.dto.SampleBatchDTO;
import com.lis.api.dto.SampleRejectDTO;
import com.lis.api.dto.BatchBarcodeDTO;
import com.lis.api.vo.SampleVO;
import com.lis.api.vo.TubeGroupVO;
import com.lis.common.annotation.OperLog;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.sample.service.SampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * 标本 Controller
 */
@RestController
@RequestMapping("/api/sample")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public R<PageResult<SampleVO>> page(SampleQueryDTO query) {
        Page<SampleVO> page = sampleService.queryPage(query);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 按ID查询
     */
    @GetMapping("/{id}")
    public R<SampleVO> getById(@PathVariable Long id) {
        return R.ok(sampleService.getById(id));
    }

    /**
     * 按条码查询
     */
    @GetMapping("/barcode/{barcode}")
    public R<SampleVO> getByBarcode(@PathVariable String barcode) {
        return R.ok(sampleService.getByBarcode(barcode));
    }

    /**
     * 创建标本
     */
    @PostMapping
    public R<SampleVO> create(@RequestBody SampleDTO dto) {
        return R.ok(sampleService.createSample(dto));
    }

    /**
     * 标本核收 (需选择仪器, 系统校验仪器能力并按仪器序号生成标本号)
     */
    @PutMapping("/receive")
    @OperLog(module = "标本管理", operation = "标本核收", recordParams = true)
    public R<SampleVO> receive(@RequestBody SampleReceiveDTO dto) {
        return R.ok(sampleService.receiveSample(dto));
    }

    /**
     * 核收前预检: 校验仪器是否能检测该条码对应的所有检验项目
     * 返回不可检测的项目名称列表 (空列表表示校验通过)
     */
    @GetMapping("/receive/precheck")
    public R<List<String>> precheckReceive(@RequestParam String barcode,
                                           @RequestParam Long instrumentId) {
        return R.ok(sampleService.precheckReceive(barcode, instrumentId));
    }

    /**
     * 按就诊/门诊/住院标识查询可核收标本(状态=已送检2/已签收3), 供技师工作站按标识核收时多标本选择
     * @param type    标识类型: visitNo就诊号 / outpatient门诊号 / inpatient住院号
     * @param keyword 标识值(就诊号)
     */
    @GetMapping("/receive/lookup")
    public R<List<java.util.Map<String, Object>>> lookupReceivable(@RequestParam String type,
                                                                   @RequestParam String keyword) {
        return R.ok(sampleService.lookupReceivable(type, keyword));
    }

    /**
     * 标本采集: 扫码采集, 记录采集时间和采集人
     * 采集人自动取当前登录用户(通过网关注入 X-User-Id 查 lis_staff 获取员工信息)
     */
    @PutMapping("/collect")
    @OperLog(module = "标本管理", operation = "标本采集", recordParams = true)
    public R<SampleVO> collect(@RequestBody SampleCollectDTO dto,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(sampleService.collectSample(dto, userId));
    }

    /**
     * 标本取消采集: 批量取消采集, 状态回退到已申请, 清除采集时间/采集人
     * 仅允许已采集但未送检、未签收、未核收、未报告的标本取消采集
     */
    @PutMapping("/cancel-collect")
    @OperLog(module = "标本管理", operation = "标本取消采集")
    public R<List<SampleVO>> cancelCollect(@RequestBody SampleBatchDTO dto,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(sampleService.cancelCollectSamples(dto, userId));
    }

    /**
     * 标本送检: 批量送检, 校验已采集才能送检
     * 送检人自动取当前登录用户
     */
    @PutMapping("/deliver")
    @OperLog(module = "标本管理", operation = "标本送检")
    public R<List<SampleVO>> deliver(@RequestBody SampleBatchDTO dto,
                                     @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(sampleService.deliverSamples(dto, userId));
    }

    /**
     * 标本取消送检: 批量取消送检, 清除送检时间/送检人
     * 仅允许已送检但未签收、未核收、未报告的标本取消送检
     */
    @PutMapping("/cancel-deliver")
    public R<List<SampleVO>> cancelDeliver(@RequestBody SampleBatchDTO dto,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(sampleService.cancelDeliverSamples(dto, userId));
    }

    /**
     * 标本签收: 批量签收, 校验已送检才能签收
     * 签收人自动取当前登录用户
     */
    @PutMapping("/signoff")
    @OperLog(module = "标本管理", operation = "标本签收")
    public R<List<SampleVO>> signOff(@RequestBody SampleBatchDTO dto,
                                     @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(sampleService.signOffSamples(dto, userId));
    }

    /**
     * 标本取消签收: 批量取消签收, 状态回退到已送检, 清除签收时间/签收人
     * 仅允许已签收但未核收、未报告的标本取消签收
     */
    @PutMapping("/cancel-signoff")
    public R<List<SampleVO>> cancelSignOff(@RequestBody SampleBatchDTO dto,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(sampleService.cancelSignOffSamples(dto, userId));
    }

    /**
     * 更新状态
     */
    @PutMapping("/{id}/status")
    public R<SampleVO> updateStatus(@PathVariable Long id, @RequestParam int status) {
        return R.ok(sampleService.updateStatus(id, status));
    }

    /**
     * 标本追踪
     */
    @GetMapping("/{id}/track")
    public R<SampleVO> track(@PathVariable Long id) {
        return R.ok(sampleService.trackSample(id));
    }

    /**
     * 标本退费
     */
    @PutMapping("/{id}/refund")
    @OperLog(module = "标本管理", operation = "标本退费")
    public R<SampleVO> refund(@PathVariable Long id, @RequestParam String reason) {
        return R.ok(sampleService.refundSample(id, reason));
    }

    /**
     * 标本作废
     */
    @PutMapping("/{id}/void")
    @OperLog(module = "标本管理", operation = "标本作废")
    public R<SampleVO> voidSample(@PathVariable Long id, @RequestParam String reason) {
        return R.ok(sampleService.voidSample(id, reason));
    }

    /**
     * 标本拒收: 采集/签收环节均可拒收, 需选择不合格类型
     * 拒收人自动取当前登录用户
     */
    @PutMapping("/reject")
    @OperLog(module = "标本管理", operation = "标本拒收")
    public R<SampleVO> reject(@RequestBody SampleRejectDTO dto,
                              @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(sampleService.rejectSample(dto, userId));
    }

    /**
     * 计算试管分组: 按就诊维度查询所有申请的检验项目, 按相同试管+相同标本类型跨申请合管
     * 前端查询就诊信息后调用此接口, 获取该就诊下的合管结果
     */
    @GetMapping("/tube-groups")
    public R<List<TubeGroupVO>> tubeGroups(@RequestParam Long visitId) {
        return R.ok(sampleService.calculateTubeGroups(visitId));
    }

    /**
     * 批量生成条码: 按试管分组批量创建标本, 每组一个条码(一根管)
     * 支持前端手工拆分后传入调整后的分组
     * 已生成条码的申请项目不可重复生成
     */
    @PostMapping("/batch")
    public R<List<SampleVO>> batchCreate(@RequestBody BatchBarcodeDTO dto) {
        return R.ok(sampleService.batchCreateBarcodes(dto));
    }

    /**
     * 取消条码: 删除标本及中间表关联, 允许重新生成条码
     * 仅允许未采集的标本取消条码, 已采集/送检/签收/核收的标本不可取消
     */
    @DeleteMapping("/barcode/{barcode}")
    public R<Void> cancelBarcode(@PathVariable String barcode) {
        sampleService.cancelBarcode(barcode);
        return R.ok();
    }
}
