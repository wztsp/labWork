package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试剂仓库 / 冰箱 / 层架主数据（三级树）。
 *
 * <p>{@code id = 0} 为系统内置的「默认库房（待归集）」哨兵节点，存量库存与未指定仓库的
 * 入库统一归入该节点。用哨兵值而非 NULL，是为了让
 * {@code uk_lis_reagent_stock_lot_wh(reagent_id, lot_no, warehouse_id)} 三列唯一索引
 * 对存量数据依然生效（PostgreSQL 唯一索引中 NULL &lt;&gt; NULL，详见 C-2）。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（C-10）。不声明 {@code deleted} 字段（C-9 复核结论）。</p>
 */
@TableName("lis_reagent_warehouse")
public class ReagentWarehouse {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仓库编码，唯一 */
    private String whCode;
    /** 仓库名称 */
    private String whName;
    /** 父节点，0=根 */
    private Long parentId;
    /** 物化路径 /1/5/23/，子树查询用 likeRight */
    private String path;
    /** 1库房 2冰箱 3层架，见 {@code com.lis.reagent.enums.WhType} */
    private Integer whType;
    /** 归属科室（sys_department.id 快照，不建外键） */
    private Long deptId;
    /** 科室名称冗余快照 */
    private String deptName;
    /** 温度区间下限(℃) */
    private BigDecimal tempMin;
    /** 温度区间上限(℃) */
    private BigDecimal tempMax;
    /** 位置描述 */
    private String locationDesc;
    private Integer sortOrder;
    /** 0停用 1启用 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 子节点列表（非数据库字段，仓库树组装用） */
    @TableField(exist = false)
    private List<ReagentWarehouse> children;

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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public List<ReagentWarehouse> getChildren() {
        return children;
    }

    public void setChildren(List<ReagentWarehouse> children) {
        this.children = children;
    }

}
