package com.lis.reagent.support;

import com.lis.common.core.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 盘点差异计算（零 Spring 依赖，可脱离容器直接单测）。
 *
 * <p>差异 = 实盘数 - 账面数。正数为盘盈，负数为盘亏，0 为相符。
 * 所有比较使用 {@link BigDecimal#compareTo}，禁用 {@code equals}（标度陷阱）。</p>
 *
 * @author LIS Team
 */
public final class StockCheckCalculator {

    private StockCheckCalculator() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 计算盘点差异。
     *
     * @param actual 实盘数量，不能为 null 且不能为负数
     * @param book   账面数量，null 视为 0
     * @return 差异值 = actual - book，正数盘盈 / 负数盘亏 / 0 相符，恒不为 null
     * @throws BusinessException 实盘数量为 null 或为负数时抛出
     */
    public static BigDecimal diff(BigDecimal actual, BigDecimal book) {
        if (actual == null) {
            throw new BusinessException("实盘数量不能为空");
        }
        if (actual.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("实盘数量不能为负数");
        }
        return actual.subtract(book == null ? BigDecimal.ZERO : book);
    }

    /**
     * 是否需要调账。差异为 0（或 null）时无需调账，避免写出一堆 quantity=0 的无效流水。
     *
     * @param diff 差异值，可为 null
     * @return true 表示需要调账
     */
    public static boolean needAdjust(BigDecimal diff) {
        return diff != null && diff.compareTo(BigDecimal.ZERO) != 0;
    }

    /**
     * 是否为盘盈（差异 &gt; 0）。
     *
     * @param diff 差异值，可为 null
     * @return true 表示盘盈
     */
    public static boolean isSurplus(BigDecimal diff) {
        return diff != null && diff.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 是否为盘亏（差异 &lt; 0）。
     *
     * @param diff 差异值，可为 null
     * @return true 表示盘亏
     */
    public static boolean isShortage(BigDecimal diff) {
        return diff != null && diff.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * 实盘为 0 时批次应置为「已耗尽」（R-07 验收 7）。
     *
     * @param actual 实盘数量，可为 null
     * @return true 表示应置 status=2 已耗尽
     */
    public static boolean isExhausted(BigDecimal actual) {
        return actual != null && actual.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 差异金额 = 差异数量 × 单价，用于盘点损益汇总。
     *
     * @param diff      差异数量，null 视为 0
     * @param unitPrice 单价，null 视为 0
     * @return 差异金额，恒不为 null
     */
    public static BigDecimal diffAmount(BigDecimal diff, BigDecimal unitPrice) {
        BigDecimal d = diff == null ? BigDecimal.ZERO : diff;
        BigDecimal p = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        return d.multiply(p);
    }
}
