package com.lis.reagent.support;

import com.lis.common.core.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 仓库相关的纯函数守卫（零 Spring 依赖，可脱离容器直接单测）。
 *
 * <p>三件事：仓库 ID 归一化、物化路径拼装、储存温度越界判定。</p>
 *
 * @author LIS Team
 */
public final class ReagentWarehouseGuard {

    /**
     * 默认库房（待归集）ID。
     *
     * <p>为什么用哨兵值 0 而不是 NULL：PostgreSQL 唯一索引中 {@code NULL <> NULL}，
     * 若 {@code warehouse_id} 允许 NULL，则
     * {@code uk_lis_reagent_stock_lot_wh(reagent_id, lot_no, warehouse_id)}
     * 对所有 NULL 行完全失效 —— 可以插入任意多条重复行且不报错，
     * 这比直接崩溃更危险（因为发现不了）。</p>
     */
    public static final long DEFAULT_WAREHOUSE_ID = 0L;

    /** 物化路径分隔符 */
    public static final String PATH_SEPARATOR = "/";

    private ReagentWarehouseGuard() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 仓库 ID 归一化：null → 0（默认库房）。
     *
     * <p>绝不让 null 进入 {@code LambdaQueryWrapper.eq()} —— MyBatis-Plus 会把
     * {@code eq(col, null)} 翻译成 {@code col = NULL}，该条件在 SQL 中恒为
     * UNKNOWN，导致查询永远返回空集，进而误判为「批次不存在」而重复插入。</p>
     *
     * @param warehouseId 原始仓库 ID，可为 null
     * @return 归一化后的仓库 ID，null 时返回 {@link #DEFAULT_WAREHOUSE_ID}
     */
    public static long normalize(Long warehouseId) {
        return warehouseId == null ? DEFAULT_WAREHOUSE_ID : warehouseId;
    }

    /**
     * 是否为默认库房。
     *
     * @param warehouseId 仓库 ID，可为 null
     * @return true 表示 null 或 0
     */
    public static boolean isDefault(Long warehouseId) {
        return normalize(warehouseId) == DEFAULT_WAREHOUSE_ID;
    }

    /**
     * 拼装子节点物化路径。
     *
     * <p>根节点（parentPath 为 null 或空）返回 {@code "/{id}/"}；
     * 否则返回 {@code parentPath + id + "/"}。首尾均带分隔符，
     * 保证 {@code LIKE path || '%'} 做子树查询时不会把 {@code /12/} 误匹配到 {@code /123/}。</p>
     *
     * @param parentPath 父节点路径，可为 null（表示建根节点）
     * @param id         当前节点 ID，不能为 null
     * @return 当前节点的物化路径，恒以 {@code /} 开头和结尾
     * @throws BusinessException id 为 null 时抛出
     */
    public static String childPath(String parentPath, Long id) {
        if (id == null) {
            throw new BusinessException("仓库ID不能为空，无法生成路径");
        }
        if (parentPath == null || parentPath.trim().isEmpty()) {
            return PATH_SEPARATOR + id + PATH_SEPARATOR;
        }
        String base = parentPath.trim();
        if (!base.startsWith(PATH_SEPARATOR)) {
            base = PATH_SEPARATOR + base;
        }
        if (!base.endsWith(PATH_SEPARATOR)) {
            base = base + PATH_SEPARATOR;
        }
        return base + id + PATH_SEPARATOR;
    }

    /**
     * 温度判定结果。
     */
    public static final class TempVerdict {

        /** 是否通过（未越界或无法判定时为 true） */
        private final boolean ok;
        /** 提示信息，通过时为空串 */
        private final String msg;

        /**
         * @param ok  是否通过
         * @param msg 提示信息，null 视为空串
         */
        public TempVerdict(boolean ok, String msg) {
            this.ok = ok;
            this.msg = msg == null ? "" : msg;
        }

        public boolean isOk() {
            return ok;
        }

        public String getMsg() {
            return msg;
        }

        /** 通过的单例式构造 */
        public static TempVerdict pass() {
            return new TempVerdict(true, "");
        }

        /**
         * 不通过。
         *
         * @param msg 越界说明
         * @return 不通过结果
         */
        public static TempVerdict fail(String msg) {
            return new TempVerdict(false, msg);
        }
    }

    /**
     * 判定试剂储存温度区间与目标仓库温度区间是否兼容。
     *
     * <p>规则：任一侧区间未配置（两端都为 null）时<b>不做判定</b>，返回通过 ——
     * 存量试剂大多没填温度，若一律拦截会导致入库全线阻塞。这是「预警」而非「硬校验」。</p>
     *
     * <p>不兼容的判定条件（只要满足其一即越界）：</p>
     * <ul>
     *   <li>仓库温度上限 &lt; 试剂要求下限 —— 仓库太冷</li>
     *   <li>仓库温度下限 &gt; 试剂要求上限 —— 仓库太热</li>
     * </ul>
     *
     * @param stockTempMin 试剂要求的储存温度下限(℃)，可为 null
     * @param stockTempMax 试剂要求的储存温度上限(℃)，可为 null
     * @param whTempMin    仓库温度下限(℃)，可为 null
     * @param whTempMax    仓库温度上限(℃)，可为 null
     * @return 判定结果，恒不为 null
     */
    public static TempVerdict checkTemperature(BigDecimal stockTempMin, BigDecimal stockTempMax,
                                               BigDecimal whTempMin, BigDecimal whTempMax) {
        boolean stockUnset = stockTempMin == null && stockTempMax == null;
        boolean whUnset = whTempMin == null && whTempMax == null;
        if (stockUnset || whUnset) {
            return TempVerdict.pass();
        }

        if (stockTempMin != null && whTempMax != null && whTempMax.compareTo(stockTempMin) < 0) {
            return TempVerdict.fail("仓库温度上限 " + whTempMax.toPlainString()
                    + "℃ 低于试剂储存要求下限 " + stockTempMin.toPlainString() + "℃");
        }
        if (stockTempMax != null && whTempMin != null && whTempMin.compareTo(stockTempMax) > 0) {
            return TempVerdict.fail("仓库温度下限 " + whTempMin.toPlainString()
                    + "℃ 高于试剂储存要求上限 " + stockTempMax.toPlainString() + "℃");
        }
        return TempVerdict.pass();
    }

    /**
     * 校验温度区间自身合法（下限不得大于上限）。
     *
     * @param tempMin 下限，可为 null
     * @param tempMax 上限，可为 null
     * @throws BusinessException 下限大于上限时抛出
     */
    public static void assertTempRange(BigDecimal tempMin, BigDecimal tempMax) {
        if (tempMin != null && tempMax != null && tempMin.compareTo(tempMax) > 0) {
            throw new BusinessException("温度下限不能大于温度上限");
        }
    }
}
