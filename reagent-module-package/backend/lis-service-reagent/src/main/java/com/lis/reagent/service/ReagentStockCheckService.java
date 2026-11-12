package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.dto.ItemSubmitDTO;
import com.lis.reagent.dto.StockCheckCreateDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.ReagentStockCheck;

import java.util.List;

/**
 * 库存盘点服务（R-07 + 缺陷 P 修复）。
 *
 * <p><b>闭环</b>：创建盘点单（按仓库子树生成账面明细）→ 录入实盘 → 提交（差异自动
 * 生成盘盈 / 盘亏调账流水 + 回写库存，单事务 + 幂等）。</p>
 *
 * <p><b>事务与幂等</b>：{@link #submit} 用悲观行锁锁盘点单 + 状态守卫，
 * 「已完成」的单据重复提交一律拒绝（缺陷 P）；差异为 0 的明细不写流水
 * （R-07 验收 4），全部失败整体回滚。</p>
 *
 * @author LIS Team
 */
public interface ReagentStockCheckService extends IService<ReagentStockCheck> {

    /**
     * 创建盘点单（含按仓库子树生成的账面明细）。
     *
     * @param dto 创建参数（仓库 / 是否含子树）
     * @return 创建后的盘点单
     */
    ReagentStockCheck create(StockCheckCreateDTO dto);

    /**
     * 提交实盘并调账。
     *
     * @param checkId 盘点单 ID
     * @param items   实盘明细（itemId + actualQty）
     * @param ctx     业务上下文
     */
    void submit(Long checkId, List<ItemSubmitDTO> items, StockOpContext ctx);

    /**
     * 作废盘点单（仅「进行中」可作废）。
     *
     * @param checkId      盘点单 ID
     * @param operatorName 操作人姓名
     * @param reason       作废原因
     */
    void voidCheck(Long checkId, String operatorName, String reason);
}
