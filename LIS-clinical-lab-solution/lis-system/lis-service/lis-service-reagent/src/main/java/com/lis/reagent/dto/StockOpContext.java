package com.lis.reagent.dto;

import java.io.Serializable;

/**
 * 库存写操作审计上下文（不可变 holder）。
 *
 * <p>贯穿所有库存写操作（入库 / 出库 / 调账 / 移库），负责把「谁、在哪个科室、
 * 因为哪张单据」这三类审计信息一次性传到最底层的流水写入点，
 * 避免在 Service 之间层层透传 5~6 个散装参数。</p>
 *
 * <p><b>不可变性</b>：所有字段 {@code final}，只通过构造器或 {@link Builder} 赋值，
 * 不提供 setter。这样同一个 context 对象可以安全地在一次事务内的多个
 * 库存操作间复用（例如调拨的「一单两动作」），不会被中途篡改。</p>
 *
 * <p><b>字段来源说明</b>：设计文档 §6.4（T-04）列出 operatorId/operatorName/deptId/deptName/ip；
 * §2.3 的调用示例中还使用了 sourceNo/relatedType/relatedId/remark。
 * 本类取两者并集，保证两处设计都能落地。</p>
 *
 * @author LIS Team
 */
public final class StockOpContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作人 ID */
    private final Long operatorId;
    /** 操作人姓名快照 */
    private final String operatorName;
    /** 归属科室 ID（成本归集维度） */
    private final Long deptId;
    /** 归属科室名称快照 */
    private final String deptName;
    /** 操作来源 IP，用于审计追溯 */
    private final String ip;
    /** 来源单据号，写入 lis_reagent_stock_record.source_no */
    private final String sourceNo;
    /** 来源单据类型：REQ / CHECK / INBOUND / OPENING / CONSUME */
    private final String relatedType;
    /** 来源单据 ID，写入 lis_reagent_stock_record.related_id */
    private final Long relatedId;
    /** 备注 */
    private final String remark;

    /**
     * 全参构造器。推荐使用 {@link #builder()} 以避免参数顺序错位。
     *
     * @param operatorId   操作人 ID
     * @param operatorName 操作人姓名
     * @param deptId       科室 ID
     * @param deptName     科室名称
     * @param ip           操作 IP
     * @param sourceNo     来源单据号
     * @param relatedType  来源单据类型
     * @param relatedId    来源单据 ID
     * @param remark       备注
     */
    public StockOpContext(Long operatorId, String operatorName, Long deptId, String deptName,
                          String ip, String sourceNo, String relatedType, Long relatedId, String remark) {
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.deptId = deptId;
        this.deptName = deptName;
        this.ip = ip;
        this.sourceNo = sourceNo;
        this.relatedType = relatedType;
        this.relatedId = relatedId;
        this.remark = remark;
    }

    /**
     * 仅带操作人信息的简化构造，用于无来源单据的手工操作。
     *
     * @param operatorId   操作人 ID
     * @param operatorName 操作人姓名
     */
    public StockOpContext(Long operatorId, String operatorName) {
        this(operatorId, operatorName, null, null, null, null, null, null, null);
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public Long getDeptId() {
        return deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public String getIp() {
        return ip;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public String getRelatedType() {
        return relatedType;
    }

    public Long getRelatedId() {
        return relatedId;
    }

    public String getRemark() {
        return remark;
    }

    /**
     * 创建构造器。
     *
     * @return 新的 {@link Builder} 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 基于当前上下文派生一个只替换来源单据信息的新实例。
     *
     * <p>用于「一个操作人在一次事务里操作多张来源单据」的场景，
     * 保持不可变语义的同时避免重复填写操作人信息。</p>
     *
     * @param newSourceNo    新的来源单据号
     * @param newRelatedType 新的来源单据类型
     * @param newRelatedId   新的来源单据 ID
     * @return 新的上下文实例，原实例不受影响
     */
    public StockOpContext withSource(String newSourceNo, String newRelatedType, Long newRelatedId) {
        return new StockOpContext(this.operatorId, this.operatorName, this.deptId, this.deptName,
                this.ip, newSourceNo, newRelatedType, newRelatedId, this.remark);
    }

    @Override
    public String toString() {
        return "StockOpContext{operatorId=" + operatorId
                + ", operatorName='" + operatorName + '\''
                + ", deptId=" + deptId
                + ", relatedType='" + relatedType + '\''
                + ", relatedId=" + relatedId
                + ", sourceNo='" + sourceNo + '\'' + '}';
    }

    /**
     * {@link StockOpContext} 构造器。
     */
    public static final class Builder {

        private Long operatorId;
        private String operatorName;
        private Long deptId;
        private String deptName;
        private String ip;
        private String sourceNo;
        private String relatedType;
        private Long relatedId;
        private String remark;

        private Builder() {
        }

        public Builder operatorId(Long v) {
            this.operatorId = v;
            return this;
        }

        public Builder operatorName(String v) {
            this.operatorName = v;
            return this;
        }

        public Builder deptId(Long v) {
            this.deptId = v;
            return this;
        }

        public Builder deptName(String v) {
            this.deptName = v;
            return this;
        }

        public Builder ip(String v) {
            this.ip = v;
            return this;
        }

        public Builder sourceNo(String v) {
            this.sourceNo = v;
            return this;
        }

        public Builder relatedType(String v) {
            this.relatedType = v;
            return this;
        }

        public Builder relatedId(Long v) {
            this.relatedId = v;
            return this;
        }

        public Builder remark(String v) {
            this.remark = v;
            return this;
        }

        /**
         * 构建不可变实例。
         *
         * @return {@link StockOpContext}
         */
        public StockOpContext build() {
            return new StockOpContext(operatorId, operatorName, deptId, deptName,
                    ip, sourceNo, relatedType, relatedId, remark);
        }
    }
}
