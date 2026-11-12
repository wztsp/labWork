package com.lis.micro.service;

import com.lis.micro.interpret.dto.InterpretRequest;
import com.lis.micro.interpret.dto.InterpretResult;

import java.util.List;

/**
 * 药敏判读接入服务: 负责取折点 + 委托纯算法判读。
 */
public interface SusceptibilityInterpretService {

    /**
     * 单条判读。
     *
     * @param req 判读入参
     * @return 判读结果, 永不为 null; 无法判读时降级为 NI
     */
    InterpretResult interpret(InterpretRequest req);

    /**
     * 批量判读: 按 (bacteriaId, antibioticId) 去重预取折点, 避免 N+1 查询。
     *
     * @param reqs 判读入参列表
     * @return 与入参等长且顺序一致的结果列表, 永不为 null
     */
    List<InterpretResult> interpretBatch(List<InterpretRequest> reqs);
}
