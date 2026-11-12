package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 科室主数据【只读投影】实体，对应表 {@code sys_department}。
 *
 * <p>设计说明（对应设计文档 C-5）：本项目采用单库单体部署，
 * lis-service-system 与 lis-service-reagent 共享同一个 PostgreSQL 数据源，
 * 因此跨模块读取科室名称无需引入 Feign / OpenFeign 远程调用，
 * 直接以只读投影实体访问即可，既省掉一次网络往返，也避免了模块间强耦合。</p>
 *
 * <p><b>严格约束</b>：</p>
 * <ul>
 *   <li>本实体<b>只读</b>。禁止在 reagent 模块内对 {@code sys_department} 执行
 *       任何 insert / update / delete；科室主数据的写入权归 system 模块独有。</li>
 *   <li>只声明 reagent 模块实际需要的列，不做全字段镜像，
 *       避免 system 模块加列时被动同步。</li>
 *   <li>命名带 {@code Projection} 后缀，与 system 模块的正式实体区分，
 *       防止后续开发者误当作可写实体使用。</li>
 * </ul>
 *
 * @author LIS Team
 */
@TableName("sys_department")
public class SysDepartmentProjection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 科室主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 科室编码 */
    private String deptCode;

    /** 科室名称 */
    private String deptName;

    /** 上级科室 ID，0 表示顶级 */
    private Long parentId;

    /** 状态：0停用 1启用 */
    private Integer status;

    public SysDepartmentProjection() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
