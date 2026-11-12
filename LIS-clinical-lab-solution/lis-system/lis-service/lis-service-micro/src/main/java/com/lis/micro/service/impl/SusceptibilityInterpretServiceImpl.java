package com.lis.micro.service.impl;

import com.lis.micro.entity.AntibioticBreakpoint;
import com.lis.micro.interpret.SirCategory;
import com.lis.micro.interpret.SusceptibilityInterpreter;
import com.lis.micro.interpret.dto.InterpretRequest;
import com.lis.micro.interpret.dto.InterpretResult;
import com.lis.micro.interpret.impl.ClsiSusceptibilityInterpreter;
import com.lis.micro.service.AntibioticBreakpointService;
import com.lis.micro.service.SusceptibilityInterpretService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 药敏判读接入服务实现。
 *
 * <p>职责边界: 只负责「取折点 + 委托 + 人工值优先」，
 * 具体判定算法全部下沉到零 Spring 依赖的 {@link ClsiSusceptibilityInterpreter}。</p>
 */
@Slf4j
@Service
public class SusceptibilityInterpretServiceImpl implements SusceptibilityInterpretService {

    private final AntibioticBreakpointService antibioticBreakpointService;
    private final SusceptibilityInterpreter interpreter;

    @Autowired
    public SusceptibilityInterpretServiceImpl(AntibioticBreakpointService antibioticBreakpointService) {
        this(antibioticBreakpointService, new ClsiSusceptibilityInterpreter());
    }

    /** 供单元测试注入自定义判读器 */
    public SusceptibilityInterpretServiceImpl(AntibioticBreakpointService antibioticBreakpointService,
                                              SusceptibilityInterpreter interpreter) {
        this.antibioticBreakpointService = antibioticBreakpointService;
        this.interpreter = interpreter != null ? interpreter : new ClsiSusceptibilityInterpreter();
    }

    @Override
    public InterpretResult interpret(InterpretRequest req) {
        if (req == null) {
            return InterpretResult.degraded(null, "判读入参为空");
        }
        List<AntibioticBreakpoint> candidates = fetchCandidates(req.getBacteriaId(), req.getAntibioticId());
        InterpretResult result = interpreter.interpret(req, candidates);
        return applyManualPriority(req, result);
    }

    @Override
    public List<InterpretResult> interpretBatch(List<InterpretRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 按 (bacteriaId, antibioticId) 去重, 每个组合只查一次折点, 避免 N+1
        Set<String> keys = new LinkedHashSet<>();
        Map<String, List<AntibioticBreakpoint>> candidateMap = new HashMap<>();
        for (InterpretRequest req : reqs) {
            if (req == null) {
                continue;
            }
            String key = SusceptibilityInterpreter.candidateKey(req.getBacteriaId(), req.getAntibioticId());
            if (keys.add(key)) {
                candidateMap.put(key, fetchCandidates(req.getBacteriaId(), req.getAntibioticId()));
            }
        }
        log.debug("批量药敏判读: 请求={}条, 折点查询={}次", reqs.size(), keys.size());

        // 2. 委托纯算法批量判读
        List<InterpretResult> results = interpreter.interpretBatch(reqs, candidateMap);

        // 3. 人工值优先: 逐条回填 (results 与 reqs 等长且顺序一致)
        List<InterpretResult> finalResults = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            InterpretRequest req = i < reqs.size() ? reqs.get(i) : null;
            finalResults.add(applyManualPriority(req, results.get(i)));
        }
        return finalResults;
    }

    // ==================== 私有方法 ====================

    /**
     * 查询候选折点, 任何异常都不向外抛出, 退化为空集合由判读器降级处理。
     */
    private List<AntibioticBreakpoint> fetchCandidates(Long bacteriaId, Long antibioticId) {
        if (bacteriaId == null || antibioticId == null) {
            return Collections.emptyList();
        }
        try {
            List<AntibioticBreakpoint> list =
                    antibioticBreakpointService.listByAntibioticAndBacteria(antibioticId, bacteriaId);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.warn("查询折点失败, 降级为空折点: bacteriaId={}, antibioticId={}, err={}",
                    bacteriaId, antibioticId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 人工判读优先: 入参带 manualResult 时保留人工值, 不被引擎结果覆盖。
     *
     * @param req    原始入参, 可为 null
     * @param result 引擎结果, 可为 null
     * @return 处理后的结果, 永不为 null
     */
    private InterpretResult applyManualPriority(InterpretRequest req, InterpretResult result) {
        InterpretResult r = result != null ? result : InterpretResult.degraded(req, "判读结果为空");
        if (req == null) {
            return r;
        }
        String manual = req.getManualResult();
        if (manual == null || manual.trim().isEmpty()) {
            return r;
        }
        SirCategory manualSir = SirCategory.fromCode(manual);
        r.setSir(manualSir);
        r.setDegraded(false);
        r.setMessage("人工判读优先, 引擎结果不覆盖人工值");
        r.setBasis("人工判读结果: " + manual.trim()
                + (r.getBasis() != null ? " (引擎参考依据: " + r.getBasis() + ")" : ""));
        return r;
    }
}
