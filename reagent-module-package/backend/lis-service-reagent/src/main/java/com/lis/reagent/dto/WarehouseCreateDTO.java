package com.lis.reagent.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 仓库 / 库位创建 / 编辑入参。
 *
 * <p>{@code path} 由 Service 通过 {@code ReagentWarehouseGuard.childPath(parentPath, id)}
 * 生成，前端不传。</p>
 *
 * @author LIS Team
 */
public class WarehouseCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仓库 ID，新建时为 null */
    private Long id;
    /** 仓库编码；为空时由 Service 生成，唯一 */
    private String whCode;
    /** 仓库名称，必填 */
    private String whName;
    /** 上级仓库 ID；null 或 0 表示顶级 */
    private Long parentId;
    /** 仓库类型：1库房 2冰箱 3货架 */
    private Integer whType;
    /** 归属科室 ID */
    private Long deptId;
    /** 归属科室名称 */
    private String deptName;
    /** 温度下限(℃) */
    private BigDecimal tempMin;
    /** 温度上限(℃) */
    private BigDecimal tempMax;
    /** 位置描述 */
    private String locationDesc;
    /** 排序号 */
    private Integer sortOrder;
    /** 状态：0停用 1启用 */
    private Integer status;
    /** 备注 */
    private String remark;

    public WarehouseCreateDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWhCode() {
        return whCode;
    }

    public void setWhCode(String whCode) {
        this.whCode = whCode;
    }

    public String getWhName() {
        return whName;
    }

    public void setWhName(String whName) {
        this.whName = whName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getWhType() {
        return whType;
    }

    public void setWhType(Integer whType) {
        this.whType = whType;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public BigDecimal getTempMin() {
        return tempMin;
    }

    public void setTempMin(BigDecimal tempMin) {
        this.tempMin = tempMin;
    }

    public BigDecimal getTempMax() {
        return tempMax;
    }

    public void setTempMax(BigDecimal tempMax) {
        this.tempMax = tempMax;
    }

    public String getLocationDesc() {
        return locationDesc;
    }

    public void setLocationDesc(String locationDesc) {
        this.locationDesc = locationDesc;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
