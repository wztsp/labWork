package com.lis.micro.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.micro.entity.AntibioticBreakpoint;
import com.lis.micro.interpret.dto.InterpretRequest;
import com.lis.micro.interpret.dto.InterpretResult;
import com.lis.micro.service.AntibioticBreakpointService;
import com.lis.micro.service.SusceptibilityInterpretService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 抗生素折点 Controller
 */
@RestController
@RequestMapping("/api/micro/breakpoints")
@RequiredArgsConstructor
public class AntibioticBreakpointController {

    private final AntibioticBreakpointService antibioticBreakpointService;
    private final SusceptibilityInterpretService susceptibilityInterpretService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public R<PageResult<AntibioticBreakpoint>> page(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(required = false) Long antibioticId,
                                                     @RequestParam(required = false) Long bacteriaId,
                                                     @RequestParam(required = false) String standard,
                                                     @RequestParam(required = false) String method) {
        Page<AntibioticBreakpoint> page = antibioticBreakpointService
                .queryPage(current, size, antibioticId, bacteriaId, standard, method);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/all")
    public R<List<AntibioticBreakpoint>> listAll() {
        return R.ok(antibioticBreakpointService.listAllEnabled());
    }

    @GetMapping("/by-bacteria/{bacteriaId}")
    public R<List<AntibioticBreakpoint>> listByBacteria(@PathVariable Long bacteriaId) {
        return R.ok(antibioticBreakpointService.listByBacteria(bacteriaId));
    }

    @GetMapping("/by-pair")
    public R<List<AntibioticBreakpoint>> listByPair(@RequestParam Long antibioticId,
                                                     @RequestParam Long bacteriaId) {
        return R.ok(antibioticBreakpointService.listByAntibioticAndBacteria(antibioticId, bacteriaId));
    }

    @GetMapping("/{id}")
    public R<AntibioticBreakpoint> getById(@PathVariable Long id) {
        return R.ok(antibioticBreakpointService.getById(id));
    }

    @PostMapping
    public R<AntibioticBreakpoint> create(@RequestBody AntibioticBreakpoint breakpoint) {
        breakpoint.setStatus(1);
        antibioticBreakpointService.save(breakpoint);
        return R.ok(breakpoint);
    }

    @PutMapping
    public R<AntibioticBreakpoint> update(@RequestBody AntibioticBreakpoint breakpoint) {
        antibioticBreakpointService.updateById(breakpoint);
        return R.ok(breakpoint);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        antibioticBreakpointService.removeById(id);
        return R.ok();
    }

    /**
     * CLSI 药敏判读: 单条与批量共用同一端点。
     *
     * <p>请求体为 JSON 对象时按单条处理, 返回单个 {@link InterpretResult};
     * 请求体为 JSON 数组时按批量处理, 返回 {@code List<InterpretResult>}。
     * 无法判读的条目降级为 {@code sir=NI, degraded=true}, 不会返回错误码。</p>
     */
    @PostMapping("/interpret")
    public R<Object> interpret(@RequestBody(required = false) JsonNode body) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return R.ok(Collections.emptyList());
        }
        if (body.isArray()) {
            List<InterpretRequest> reqs = objectMapper.convertValue(body,
                    new TypeReference<List<InterpretRequest>>() {
                    });
            return R.ok(susceptibilityInterpretService.interpretBatch(reqs));
        }
        InterpretRequest req = objectMapper.convertValue(body, InterpretRequest.class);
        return R.ok(susceptibilityInterpretService.interpret(req));
    }
}
