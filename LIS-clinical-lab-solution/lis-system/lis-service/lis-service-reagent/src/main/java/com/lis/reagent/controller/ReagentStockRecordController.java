package com.lis.reagent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentStockRecord;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentStockRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 试剂出入库记录 Controller —— <b>只读</b>。
 *
 * <p>{@code lis_reagent_stock_record} 是库存变动的审计流水，只能由业务动作
 * （入库 / 出库 / 盘点调账 / 报损 / 退库）在同一事务内追加写入，
 * 任何人不得手工新增、修改或删除，否则「流水 → 库存」的对账链条断裂，
 * 账目失去可信度。</p>
 *
 * <p>原先的 {@code POST /}、{@code PUT /{id}}、{@code DELETE /{id}} 三个裸端点
 * 已按主理人 Q11 裁决<b>删除</b>。</p>
 */
@RestController
@RequestMapping("/api/reagent/records")
@RequiredArgsConstructor
public class ReagentStockRecordController {

    private final ReagentStockRecordMapper recordMapper;
    private final ReagentMapper reagentMapper;

    /**
     * 分页查询出入库流水。
     *
     * @param current    页码，从 1 开始
     * @param size       每页条数
     * @param reagentId  试剂 ID，可为 null 表示不过滤
     * @param recordType 流水类型，取值见 {@link com.lis.reagent.enums.RecordType}，可为 null 表示不过滤
     * @return 分页结果，按创建时间倒序
     */
    @GetMapping
    public R<PageResult<ReagentStockRecord>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) Long reagentId,
                                                  @RequestParam(required = false) Integer recordType,
                                                  @RequestParam(required = false) String reagentName,
                                                  @RequestParam(required = false)
                                                  @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                                                  LocalDateTime beginTime,
                                                  @RequestParam(required = false)
                                                  @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                                                  LocalDateTime endTime) {
        LambdaQueryWrapper<ReagentStockRecord> wrapper = new LambdaQueryWrapper<>();
        if (reagentId != null) {
            wrapper.eq(ReagentStockRecord::getReagentId, reagentId);
        }
        if (recordType != null) {
            wrapper.eq(ReagentStockRecord::getRecordType, recordType);
        }
        // 试剂名称模糊过滤：委派给 lis_reagent 子查询（流水不自带名称列）
        if (reagentName != null && !reagentName.trim().isEmpty()) {
            String name = reagentName.trim();
            wrapper.apply("reagent_id IN (SELECT id FROM lis_reagent WHERE reagent_name LIKE CONCAT('%', {0}, '%'))", name);
        }
        if (beginTime != null) {
            wrapper.ge(ReagentStockRecord::getCreateTime, beginTime);
        }
        if (endTime != null) {
            wrapper.lt(ReagentStockRecord::getCreateTime, endTime);
        }
        wrapper.orderByDesc(ReagentStockRecord::getCreateTime);
        Page<ReagentStockRecord> page = recordMapper.selectPage(new Page<>(current, size), wrapper);
        fillReagentNames(page.getRecords());
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 批量带出试剂名称/编码（Service 层批量查 lis_reagent，null 安全：试剂被删时字段为 null）。
     */
    private void fillReagentNames(List<ReagentStockRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> reagentIds = records.stream()
                .map(ReagentStockRecord::getReagentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (reagentIds.isEmpty()) {
            return;
        }
        Map<Long, Reagent> reagentMap = reagentMapper.selectBatchIds(reagentIds).stream()
                .collect(Collectors.toMap(Reagent::getId, Function.identity(), (a, b) -> a));
        for (ReagentStockRecord record : records) {
            Reagent reagent = reagentMap.get(record.getReagentId());
            if (reagent != null) {
                record.setReagentName(reagent.getReagentName());
                record.setReagentCode(reagent.getReagentCode());
            }
        }
    }
}
