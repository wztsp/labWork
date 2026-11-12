package com.lis.instrument.handler;

import com.lis.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仪器处理器工厂：根据品牌名获取对应 InstrumentHandler Bean。
 * Bean 命名约定：品牌 + "Handler"，如 MindrayHandler / SysmexHandler / RocheHandler / AbbottHandler。
 * 若品牌未实现专用处理器，降级为 MindrayHandler（绝大多数国产/主流生化仪均兼容 ASTM 协议）。
 */
@Component
public class InstrumentHandlerFactory {

    private final ApplicationContext applicationContext;
    /** 所有 InstrumentHandler Bean，key = beanName */
    private final Map<String, InstrumentHandler> allHandlers;

    /** 品牌 -> BeanName 映射（避免每次反射式查找） */
    private final Map<String, String> beanNameCache = new ConcurrentHashMap<>();

    public InstrumentHandlerFactory(ApplicationContext applicationContext,
                                    Map<String, InstrumentHandler> allHandlers) {
        this.applicationContext = applicationContext;
        this.allHandlers = allHandlers;
    }

    public InstrumentHandler getHandler(String brand) {
        if (allHandlers.isEmpty()) {
            throw new BusinessException("未注册任何 InstrumentHandler Bean，请检查仪器模块 Spring 配置");
        }
        if (brand == null || brand.isEmpty()) {
            return getFallbackHandler();
        }
        String normalized = brand.trim().toUpperCase();
        String beanName = beanNameCache.computeIfAbsent(normalized, k -> {
            // 首字母大写其余小写，再拼 Handler
            String formatted = Character.toUpperCase(k.charAt(0)) + k.substring(1).toLowerCase();
            String candidate = formatted + "Handler";
            if (applicationContext.containsBean(candidate)) {
                return candidate;
            }
            // 全小写匹配
            if (applicationContext.containsBean(k.toLowerCase() + "Handler")) {
                return k.toLowerCase() + "Handler";
            }
            // 全大写匹配
            if (applicationContext.containsBean(k + "Handler")) {
                return k + "Handler";
            }
            return null;
        });
        if (beanName != null && allHandlers.containsKey(beanName)) {
            return allHandlers.get(beanName);
        }
        return getFallbackHandler();
    }

    /**
     * 回退处理器：优先 MindrayHandler（绝大多数国产/主流生化仪均兼容 ASTM 协议），
     * 若容器中不存在则取第一个已注册 Handler。
     */
    private InstrumentHandler getFallbackHandler() {
        if (allHandlers.containsKey("MindrayHandler")) {
            return allHandlers.get("MindrayHandler");
        }
        return allHandlers.values().iterator().next();
    }
}
