package com.lis.sample.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.api.dto.OrderDTO;
import com.lis.api.dto.OrderQueryDTO;
import com.lis.api.vo.OrderVO;
import com.lis.common.annotation.OperLog;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.sample.entity.LabOrder;
import com.lis.sample.service.LabOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 检验申请 Controller
 */
@RestController
@RequestMapping("/api/sample/orders")
@RequiredArgsConstructor
public class OrderController {

    private final LabOrderService labOrderService;

    /**
     * 接收HIS检验申请: 一个申请含多个项目时拆分为多个独立申请
     */
    @PostMapping("/receive")
    @OperLog(module = "检验申请", operation = "接收检验申请", recordParams = true)
    public R<List<LabOrder>> receive(@RequestBody OrderDTO dto) {
        return R.ok(labOrderService.receiveOrder(dto));
    }

    /**
     * 分页查询申请 (支持就诊号/患者姓名/申请单号查询)
     */
    @GetMapping("/page")
    public R<PageResult<OrderVO>> page(OrderQueryDTO query) {
        Page<OrderVO> page = labOrderService.queryPage(query);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 查询申请
     */
    @GetMapping("/{id}")
    public R<LabOrder> get(@PathVariable Long id) {
        return R.ok(labOrderService.getOrder(id));
    }

    /**
     * 取消申请
     */
    @PutMapping("/{id}/cancel")
    @OperLog(module = "检验申请", operation = "取消申请")
    public R<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        labOrderService.cancelOrder(id, reason);
        return R.ok();
    }

    /**
     * 更新申请的采集方式
     * 请求体: { "collectionMethodId": 1001, "collectionMethodName": "门诊采集" }
     * collectionMethodId 为 null 表示清空采集方式
     */
    @PutMapping("/{id}/collection-method")
    public R<Void> updateCollectionMethod(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        Long methodId = body.get("collectionMethodId") == null ? null
                : Long.valueOf(body.get("collectionMethodId").toString());
        String methodName = body.get("collectionMethodName") == null ? null
                : body.get("collectionMethodName").toString();
        labOrderService.updateCollectionMethod(id, methodId, methodName);
        return R.ok();
    }

    /**
     * 临时: 清理含 ??? 乱码的申请/就诊/患者/申请项目数据 (仅开发环境使用)
     * 返回清理的记录数
     */
    @DeleteMapping("/cleanup-garbage")
    public R<String> cleanupGarbage() {
        int n = labOrderService.cleanupGarbage();
        return R.ok("清理了 " + n + " 条含乱码(???)的申请, 及关联的就诊/患者/项目记录");
    }
}
