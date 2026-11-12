package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.vo.PurchaseSuggestionDTO;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 试剂字典服务
 */
public interface ReagentService extends IService<Reagent> {

    Page<Reagent> queryPage(long current, long size, String keyword, Long instrumentId, Long testItemId);

    /**
     * 扩展分页查询（缺陷 G / C-12 / R-06 要求）：
     * 支持按名称 / 编码独立过滤、按状态过滤、按分类与危险品标志过滤。
     *
     * @param current       页码
     * @param size          页大小
     * @param keyword       关键字（名称 / 编码模糊），可为 null
     * @param reagentCode   编码精确，可为 null
     * @param reagentName   名称精确，可为 null
     * @param status        状态，可为 null
     * @param categoryId    分类，可为 null
     * @param hazardFlag    危险品标志（0 / 1），可为 null
     * @param instrumentId  仪器，可为 null
     * @param testItemId    检验项目，可为 null
     * @return 分页结果
     */
    Page<Reagent> queryPage(long current, long size, String keyword, String reagentCode,
                            String reagentName, Integer status, Long categoryId,
                            Integer hazardFlag, Long instrumentId, Long testItemId);

    /**
     * 智能检索增强分页（T-19 / R-14）：在 {@link #queryPage(long, long, String, String, String,
     * Integer, Long, Integer, Long, Long)} 基础上，叠加库存维度组合条件
     * （库存量区间 / 效期区间 / 仓库 / 存放位置）。
     *
     * <p>库存维度条件经由 {@code lis_reagent_stock} 反查试剂 ID 后以 {@code IN} 收敛，
     * 与试剂主表条件任意组合；任一维度命中为空则返回空页。</p>
     *
     * @param current      页码
     * @param size         页大小
     * @param keyword      关键字（名称 / 编码模糊），可为 null
     * @param reagentCode  编码精确，可为 null
     * @param reagentName  名称精确，可为 null
     * @param status       状态，可为 null
     * @param categoryId   分类，可为 null
     * @param hazardFlag   危险品标志（0 / 1），可为 null
     * @param instrumentId 仪器，可为 null
     * @param testItemId   检验项目，可为 null
     * @param minQuantity  库存量下限（聚合），可为 null
     * @param maxQuantity  库存量上限（聚合），可为 null
     * @param expiryStart  效期起点，可为 null
     * @param expiryEnd    效期终点，可为 null
     * @param warehouseId  仓库 ID，可为 null
     * @param location     存放位置（模糊），可为 null
     * @return 分页结果
     */
    Page<Reagent> queryPage(long current, long size, String keyword, String reagentCode,
                            String reagentName, Integer status, Long categoryId,
                            Integer hazardFlag, Long instrumentId, Long testItemId,
                            BigDecimal minQuantity, BigDecimal maxQuantity,
                            LocalDate expiryStart, LocalDate expiryEnd,
                            Long warehouseId, String location);

    Reagent getByCode(String reagentCode);

    /**
     * 计算试剂采购建议。
     *
     * <p>当前库存为 {@code lis_reagent_stock} 中未过期且 {@code status=1} 的批次数量汇总；
     * 建议采购数量 = 最佳库存（未配置时取最高库存） - 当前库存，小于 0 取 0；
     * 建议采购日期 = 今天 + 采购周期天数。</p>
     *
     * @param reagentId 试剂 ID
     * @return 采购建议，恒不为 null
     */
    PurchaseSuggestionDTO getPurchaseSuggestion(Long reagentId);

    /**
     * 删除试剂字典。被库存 / 申请明细 / 订单明细 / 请领明细 / 入库明细 / 消耗 / 期初
     * 任一引用时拒绝删除（避免孤儿数据），建议改为停用。
     *
     * @param id 试剂 ID
     */
    void delete(Long id);
}
