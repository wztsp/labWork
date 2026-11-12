package com.lis.integration.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HIS 适配器工厂
 * <p>
 * Spring 自动注入所有 {@link HisAdapter} 实现，按 adapterId 索引。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HisAdapterFactory {

    private final Map<String, HisAdapter> adapterMap = new HashMap<>();

    @Autowired
    public HisAdapterFactory(List<HisAdapter> adapters) {
        for (HisAdapter a : adapters) {
            adapterMap.put(a.getAdapterId().toUpperCase(), a);
            log.info("加载 HIS 适配器: {}", a.getAdapterId());
        }
    }

    /**
     * 根据厂商编码获取适配器
     */
    public HisAdapter getAdapter(String hisVendorCode) {
        HisAdapter a = adapterMap.get(hisVendorCode.toUpperCase());
        if (a == null) {
            throw new IllegalArgumentException("未找到 HIS 适配器: " + hisVendorCode);
        }
        return a;
    }

    /**
     * 列出所有可用适配器 ID
     */
    public java.util.Set<String> listAdapterIds() {
        return adapterMap.keySet();
    }
}
