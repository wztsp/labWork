package com.lis.micro.interpret;

import com.lis.micro.entity.AntibioticBreakpoint;
import com.lis.micro.interpret.dto.InterpretRequest;
import com.lis.micro.interpret.dto.InterpretResult;

import java.util.List;
import java.util.Map;

/**
 * 药敏判读器。
 *
 * <p><b>纯函数语义</b>：给定原始值与候选折点集合即可得出判读结果，
 * 不依赖 Spring 容器、不访问数据库、<b>绝不抛出业务异常</b>。
 * 任何无法判读的情形一律降级为 {@link SirCategory#NI} 并给出 basis 说明。</p>
 */
public interface SusceptibilityInterpreter {

    /**
     * 单条判读。
     *
     * @param req        判读入参, 可为 null (返回降级结果)
     * @param candidates 候选折点集合, 可为 null / 空 (返回降级结果)
     * @return 判读结果, 永不为 null
     */
    InterpretResult interpret(InterpretRequest req, List<AntibioticBreakpoint> candidates);

    /**
     * 批量判读。
     *
     * @param reqs         判读入参列表, 可为 null / 空
     * @param candidateMap 折点集合, key 由 {@link #candidateKey(Long, Long)} 生成
     * @return 与 reqs 等长且顺序一致的结果列表, 永不为 null
     */
    List<InterpretResult> interpretBatch(List<InterpretRequest> reqs,
                                         Map<String, List<AntibioticBreakpoint>> candidateMap);

    /**
     * 折点缓存 key 生成规则, Service 层与判读器必须使用同一规则。
     *
     * @param bacteriaId   细菌ID, 允许 null
     * @param antibioticId 抗生素ID, 允许 null
     * @return 形如 {@code "12:34"} 的 key
     */
    static String candidateKey(Long bacteriaId, Long antibioticId) {
        return bacteriaId + ":" + antibioticId;
    }
}
