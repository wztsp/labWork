package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.entity.ReagentStockRecord;
import com.lis.reagent.vo.LowStockAlertDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 试剂库存服务 - 含出入库、效期预警、FEFO。
 *
 * <p><b>写入口纪律</b>（缺陷 O 的修复红线）：库存数量只允许由业务流程产生，
 * 所有写操作必须同步写出 {@code lis_reagent_stock_record} 流水。本接口按此原则提供：
 * 入库 {@link #inStockTyped}、出库 {@link #outStockTyped} / {@link #outStockFefo}、
 * 盘点调账 {@link #adjustTo}。旧的无类型化方法保留并委托新方法，存量调用方零改动。</p>
 *
 * <p><b>仓库维度</b>（缺陷 K）：合批键为 {@code (reagentId, lotNo, warehouseId)}，
 * 仓库 ID 一律经 {@code ReagentWarehouseGuard.normalize} 归一化，null → 0（默认库房），
 * 绝不让 null 进入 {@code LambdaQueryWrapper}。</p>
 *
 * @author LIS Team
 */
public interface ReagentStockService extends IService<ReagentStock> {

    /**
     * 库存分页查询。
     *
     * @param current     页码，从 1 起
     * @param size        页大小
     * @param reagentName 试剂名称模糊匹配，可为 null
     * @param batchNo     批号模糊匹配（等价于 lotNo），可为 null
     * @param barcode     条码精确或前缀匹配，可为 null
     * @param status      批次状态，可为 null
     * @param location    位置模糊，可为 null
     * @return 分页结果，恒不为 null
     */
    Page<ReagentStock> queryPage(long current, long size, String reagentName, String batchNo,
                                 String barcode, Integer status, String location);

    /**
     * 入库（新增批次或合并已有批次）。旧签名保留，内部委托 {@link #inStockTyped}。
     */
    ReagentStock inStock(ReagentStock stock, BigDecimal quantity, Long operatorId, String operatorName,
                         String sourceNo, String remark);

    /**
     * 按试剂 FEFO 出库（近效期优先，按 expiryDate 升序依次扣减）。
     *
     * <p>旧名 {@link #outStockFifo} 名不副实（实为 FEFO），已 {@code @Deprecated}，
     * 逻辑与本方法完全一致。对试剂而言近效期优先才是正确行为，请勿「修正」为真 FIFO。</p>
     */
    List<ReagentStockRecord> outStockFefo(Long reagentId, BigDecimal quantity, Long operatorId,
                                          String operatorName, String sourceNo, String remark);

    /**
     * 按试剂 FEFO 出库（旧名，已废弃）。逻辑与 {@link #outStockFefo} 完全一致。
     *
     * @deprecated 名不副实（实为 FEFO），请改用 {@link #outStockFefo}
     */
    @Deprecated(since = "1.1")
    List<ReagentStockRecord> outStockFifo(Long reagentId, BigDecimal quantity, Long operatorId,
                                          String operatorName, String sourceNo, String remark);

    /**
     * 指定批次出库。旧签名保留，内部委托 {@link #outStockTyped}（recordType=2 出库/领用）。
     */
    ReagentStockRecord outStock(Long stockId, BigDecimal quantity, Long operatorId, String operatorName,
                                String sourceNo, String remark);

    /**
     * 带流水类型的入库，写入业务上下文（科室 / 来源单据）。
     *
     * @param stock      库存实体，warehouseId 为 null 时按默认库房 0 处理
     * @param quantity   入库数量，必须为正数
     * @param recordType 流水类型（{@code com.lis.reagent.enums.RecordType}）
     * @param ctx        业务上下文（操作人 / 科室 / 来源单据）
     * @return 入库后的库存批次
     */
    ReagentStock inStockTyped(ReagentStock stock, BigDecimal quantity, int recordType, StockOpContext ctx);

    /**
     * 指定批次出库，写入业务上下文（科室 / 来源单据）。
     *
     * @param stockId    库存批次 ID，不可为 null
     * @param quantity   出库数量，必须为正数
     * @param recordType 流水类型（{@code com.lis.reagent.enums.RecordType}）
     * @param ctx        业务上下文
     * @return 出库流水记录
     */
    ReagentStockRecord outStockTyped(Long stockId, BigDecimal quantity, int recordType, StockOpContext ctx);

    /**
     * 盘点调账专用：直接把批次数量校准到 {@code actualQty}。
     *
     * <p>差异为 0 时不写流水（避免流水表噪音，R-07 验收 4）；差异 = 实盘 - 账面，
     * 盘盈为正、盘亏为负写入流水 {@code quantity}；实盘为 0 时批次置为
     * {@code status=2 已耗尽}（R-07 验收 7）。</p>
     *
     * @param stockId    库存批次 ID，不可为 null
     * @param actualQty  实盘数量，不可为 null 且不可为负
     * @param recordType 流水类型（盘点调账应为 5）
     * @param ctx        业务上下文
     * @return 调账流水记录；差异为 0 时返回 null
     */
    ReagentStockRecord adjustTo(Long stockId, BigDecimal actualQty, int recordType, StockOpContext ctx);

    /**
     * 低库存预警（缺陷 I 修复）：一条聚合 SQL 返回专用 DTO，替代旧的
     * 「循环查库存 + 拼串伪造实体」N+1 实现。
     *
     * @return 低库存告警列表，无告警时返回空列表，恒不为 null
     */
    List<LowStockAlertDTO> listLowStock();

    /**
     * 查询低于库存下限的试剂（旧实现，保留兼容调用方）。
     *
     * @return 伪造实体的告警列表
     * @deprecated 返回伪造实体，请改用 {@link #listLowStock()}
     */
    @Deprecated(since = "1.1")
    List<ReagentStock> listBelowSafetyStock();

    /**
     * 查询近效期库存（days 天内到期）。
     */
    List<ReagentStock> listNearExpiry(int days);

    /**
     * 锁定过期批次（status=0 过期锁定）。
     */
    int lockExpiredStock();
}
