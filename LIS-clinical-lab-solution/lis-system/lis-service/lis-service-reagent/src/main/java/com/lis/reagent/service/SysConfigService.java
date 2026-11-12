package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.entity.SysConfig;

import java.util.List;

/**
 * 系统参数配置服务。
 */
public interface SysConfigService extends IService<SysConfig> {

    /**
     * 查询全部参数。
     *
     * @return 参数列表，按参数键升序，恒不为 null
     */
    List<SysConfig> listAll();

    /**
     * 按参数键取参数值。
     *
     * @param key 参数键
     * @return 参数值；参数不存在时返回 null
     */
    String getValue(String key);

    /**
     * 按参数键取布尔值，供其它模块调用。
     *
     * <p>兼容 {@code 1/0}、{@code true/false}、{@code yes/no}、{@code on/off}；
     * 参数不存在或取值无法识别时返回默认值。</p>
     *
     * @param key          参数键
     * @param defaultValue 默认值
     * @return 布尔取值
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * 更新参数值，参数不存在时抛出 BusinessException("参数不存在")。
     *
     * @param configKey   参数键
     * @param configValue 参数值
     */
    void updateValue(String configKey, String configValue);
}
