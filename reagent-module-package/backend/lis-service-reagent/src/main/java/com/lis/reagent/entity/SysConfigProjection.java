package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 系统参数【只读投影】实体，对应表 {@code sys_config}。
 *
 * <p>试剂模块用于读取运行期可调参数，例如：</p>
 * <ul>
 *   <li>{@code reagent.budget.warn.ratio} —— 预算预警比例默认值</li>
 *   <li>{@code reagent.expiry.warn.days} —— 效期预警提前天数</li>
 *   <li>{@code reagent.supplier.license.warn.days} —— 供应商资质到期预警天数</li>
 * </ul>
 *
 * <p>与 {@link SysDepartmentProjection} 同理（设计文档 C-5）：单库单体部署，
 * 直接只读访问，不引入远程调用。<b>禁止</b>在 reagent 模块内写入本表。</p>
 *
 * @author LIS Team
 */
@TableName("sys_config")
public class SysConfigProjection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 参数键 */
    private String configKey;

    /** 参数值 */
    private String configValue;

    /** 参数名称 */
    private String configName;

    public SysConfigProjection() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }
}
