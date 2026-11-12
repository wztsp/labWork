package com.lis.instrument.controller;

import com.lis.common.core.result.R;
import com.lis.instrument.handler.InstrumentHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据接收 Controller (用于手动测试)
 */
@RestController
@RequestMapping("/api/instrument/data")
@RequiredArgsConstructor
public class DataReceiveController {

    private final List<InstrumentHandler> handlers;

    /**
     * 手动接收仪器数据 (测试用)
     * @param instrumentId 仪器ID
     * @param brand 品牌 (MINDRAY/SYSMEX/ROCHE/ABBOTT)
     * @param rawData 原始报文
     */
    @PostMapping("/receive")
    public R<Boolean> receive(@RequestParam Long instrumentId,
                              @RequestParam String brand,
                              @RequestBody String rawData) {
        InstrumentHandler handler = findHandler(brand);
        if (handler == null) {
            return R.fail("未找到品牌 [" + brand + "] 对应的处理器");
        }
        handler.handleData(rawData, instrumentId);
        return R.ok(true);
    }

    /**
     * 列出所有可用品牌
     */
    @GetMapping("/brands")
    public R<List<String>> brands() {
        return R.ok(handlers.stream().map(InstrumentHandler::getBrand).toList());
    }

    private InstrumentHandler findHandler(String brand) {
        for (InstrumentHandler h : handlers) {
            if (h.getBrand().equalsIgnoreCase(brand)) {
                return h;
            }
        }
        return null;
    }
}
