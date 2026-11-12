package com.lis.report.service.impl;

import com.lis.api.dto.ResultDTO;
import com.lis.api.service.ResultPersistencePort;
import com.lis.report.mapper.ResultMapper;
import com.lis.report.service.ResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检验结果持久化端口实现（进程内直连）。
 * <p>
 * 供仪器模块在 RabbitMQ 不可用时直接落库，逻辑与 MQ 消费链路一致:
 * 1. 根据 testItemCode 从 lis_test_item 解析 testItemId / testItemName;
 * 2. 调用 ResultService 批量保存结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultPersistenceServiceImpl implements ResultPersistencePort {

    private final ResultMapper resultMapper;
    private final ResultService resultService;

    @Override
    public List<Long> persistResults(List<ResultDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            log.warn("进程内直连落库: 结果列表为空");
            return new ArrayList<>();
        }
        log.info("进程内直连落库 {} 条仪器结果, 开始解析并落库", dtos.size());

        // 1. 批量解析检验指标代码 -> id/name
        List<String> codes = new ArrayList<>();
        for (ResultDTO dto : dtos) {
            if (dto.getTestItemCode() != null && !dto.getTestItemCode().isBlank()
                    && !codes.contains(dto.getTestItemCode())) {
                codes.add(dto.getTestItemCode());
            }
        }
        Map<String, Long> codeToId = new HashMap<>();
        Map<String, String> codeToName = new HashMap<>();
        if (!codes.isEmpty()) {
            List<Map<String, Object>> rows = resultMapper.selectTestItemsByCodes(codes);
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                // MyBatis 对 List<Map> 结果不应用驼峰转换, PostgreSQL 列名可能是小写(itemcode/itemname),
                // 因此按大小写不敏感方式取值
                String code = getMapValue(row, "itemCode");
                Object idObj = getMapValue(row, "id");
                if (code != null && idObj != null) {
                    codeToId.put(code, toLong(idObj));
                    String itemName = getMapValue(row, "itemName");
                    if (itemName != null) {
                        codeToName.put(code, itemName);
                    }
                }
            }
        }

        // 2. 回填 testItemId / testItemName, 过滤无法匹配指标的记录
        List<ResultDTO> saveDtos = new ArrayList<>();
        for (ResultDTO dto : dtos) {
            String code = dto.getTestItemCode();
            if (code == null) {
                continue;
            }
            if (dto.getTestItemId() == null) {
                dto.setTestItemId(codeToId.get(code));
            }
            if (dto.getTestItemName() == null) {
                dto.setTestItemName(codeToName.get(code));
            }
            // 指标代码在 lis_test_item 中不存在时跳过, 避免非空约束导致整批回滚
            if (dto.getTestItemId() == null) {
                log.warn("检验指标代码 [{}] 未匹配到 lis_test_item, 跳过该结果", code);
                continue;
            }
            saveDtos.add(dto);
        }
        if (saveDtos.isEmpty()) {
            log.warn("进程内直连落库: 所有结果均无法匹配检验指标, 无记录可保存");
            return new ArrayList<>();
        }

        // 3. 批量保存
        List<Long> ids = resultService.batchSaveResults(saveDtos);
        log.info("进程内直连落库完成, 保存 {} 条, resultIds={}", ids.size(), ids);
        return ids;
    }

    /**
     * 从 MyBatis 返回的 Map 中按大小写不敏感方式取值。
     * <p>
     * MyBatis 对 List&lt;Map&gt; 结果不应用 map-underscore-to-camel-case，
     * PostgreSQL 驱动返回的列名 key 通常是全小写(如 itemcode/itemname)，因此需兼容。
     */
    private String getMapValue(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            Object v = row.get(key);
            return v == null ? null : String.valueOf(v);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                Object v = e.getValue();
                return v == null ? null : String.valueOf(v);
            }
        }
        return null;
    }

    /**
     * 将列值转换为 Long, 兼容 Number / String / BigInteger 等类型。
     * PostgreSQL 驱动对雪花ID(bigint) 可能返回 Long/BigInteger，MyBatis Map 场景也可能返回 String。
     */
    private long toLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return Long.parseLong(String.valueOf(v).trim());
    }
}