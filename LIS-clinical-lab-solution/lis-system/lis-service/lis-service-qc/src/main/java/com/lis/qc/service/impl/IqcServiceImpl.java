package com.lis.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.qc.entity.QcAlert;
import com.lis.qc.entity.QcRule;
import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.mapper.QcAlertMapper;
import com.lis.qc.mapper.QcIqcMapper;
import com.lis.qc.mapper.QCLotMapper;
import com.lis.qc.mapper.QcRuleMapper;
import com.lis.qc.mapper.QCRecordMapper;
import com.lis.qc.service.IqcService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 室内质控(IQC)服务实现。
 */
@Service
@RequiredArgsConstructor
public class IqcServiceImpl implements IqcService {

    private final QcIqcMapper iqcMapper;
    private final QCLotMapper lotMapper;
    private final QcRuleMapper ruleMapper;
    private final QCRecordMapper recordMapper;
    private final QcAlertMapper alertMapper;

    @Override
    public List<Map<String, Object>> projectList(Long instrumentId, String group, String status, String keyword) {
        List<Map<String, Object>> rows = iqcMapper.selectProjectList(instrumentId);
        // 每个批号存在待处理预警 → 警告
        Set<Long> warnLotIds = new HashSet<>();
        alertMapper.selectList(new LambdaQueryWrapper<QcAlert>().eq(QcAlert::getStatus, "PENDING"))
                .forEach(a -> { if (a.getLotId() != null) warnLotIds.add(a.getLotId()); });

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String projStatus = resolveStatus(r, warnLotIds);
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("lotId", r.get("lot_id"));
            dto.put("lotNo", r.get("lot_no"));
            dto.put("itemId", r.get("item_id"));
            dto.put("itemCode", r.get("item_code"));
            dto.put("itemName", r.get("item_name"));
            dto.put("group", r.get("group_name"));
            dto.put("method", r.get("method"));
            dto.put("unit", r.get("unit"));
            dto.put("instrumentId", r.get("instrument_id"));
            dto.put("instrumentName", r.get("instrument_name"));
            dto.put("brand", r.get("brand"));
            dto.put("model", r.get("model"));
            dto.put("level", r.get("level"));
            dto.put("target", toNum(r.get("target_value")));
            dto.put("sd", toNum(r.get("sd")));
            dto.put("cv", toNum(r.get("cv")));
            dto.put("lastValue", toNum(r.get("last_value")));
            dto.put("lastZ", toNum(r.get("last_z")));
            dto.put("lastResult", r.get("last_result"));
            dto.put("status", projStatus);
            dto.put("keyword", joinKeyword(r));

            // 仪器过滤
            boolean instMatch = instrumentId == null || instrumentId <= 0
                    || (r.get("instrument_id") != null && ((Number) r.get("instrument_id")).longValue() == instrumentId);
            boolean groupMatch = group == null || group.isBlank() || group.equals(r.get("group_name"));
            boolean statusMatch = status == null || status.isBlank() || status.equals(projStatus);
            boolean kwMatch = keyword == null || keyword.isBlank()
                    || String.valueOf(dto.get("keyword")).toLowerCase().contains(keyword.toLowerCase());
            if (instMatch && groupMatch && statusMatch && kwMatch) result.add(dto);
        }
        return result;
    }

    private String resolveStatus(Map<String, Object> r, Set<Long> warnLotIds) {
        String lastResult = r.get("last_result") == null ? null : String.valueOf(r.get("last_result"));
        if ("REJECT".equals(lastResult)) return "失控";
        Object lotId = r.get("lot_id");
        if (lotId != null && warnLotIds.contains(((Number) lotId).longValue())) return "警告";
        return "在控";
    }

    private String joinKeyword(Map<String, Object> r) {
        return String.valueOf(r.get("item_code")) + " " + String.valueOf(r.get("item_name"))
                + " " + String.valueOf(r.get("method")) + " " + String.valueOf(r.get("instrument_name"));
    }

    @Override
    public Map<String, Object> summary() {
        List<Map<String, Object>> list = projectList(null, null, null, null);
        long out = list.stream().filter(m -> "失控".equals(m.get("status"))).count();
        long warn = list.stream().filter(m -> "警告".equals(m.get("status"))).count();
        long in = list.size() - out - warn;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", list.size());
        map.put("inControl", in);
        map.put("warning", warn);
        map.put("outOfControl", out);
        return map;
    }

    @Override
    public Map<String, Object> projectDetail(Long lotId) {
        QCLot lot = lotMapper.selectById(lotId);
        Map<String, Object> dto = new LinkedHashMap<>();
        if (lot == null) return dto;
        dto.put("lot", lot);
        // 规则
        List<QcRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<QcRule>().eq(QcRule::getEnabled, 1));
        dto.put("rules", rules);
        // 最近 30 个质控点
        List<QCRecord> points = recordMapper.selectList(new LambdaQueryWrapper<QCRecord>()
                .eq(QCRecord::getQcLotId, lotId)
                .orderByDesc(QCRecord::getMeasureTime)
                .last("LIMIT 30"));
        List<Map<String, Object>> pts = new ArrayList<>();
        for (QCRecord p : points) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("measureTime", p.getMeasureTime());
            m.put("measureValue", p.getMeasureValue());
            m.put("zScore", p.getZScore());
            m.put("qcResult", "REJECT".equals(p.getQcResult()) ? 0 : 1);
            m.put("ruleViolation", p.getRuleViolation());
            pts.add(m);
        }
        dto.put("points", pts);
        // 用于 L-J 图
        dto.put("target", lot.getTargetValue());
        dto.put("sd", lot.getSd());
        // 存在待处理预警标记
        boolean warn = alertMapper.selectCount(new LambdaQueryWrapper<QcAlert>()
                .eq(QcAlert::getLotId, lotId).eq(QcAlert::getStatus, "PENDING")) > 0;
        dto.put("warning", warn);
        return dto;
    }

    private Object toNum(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return o;
        try { return new java.math.BigDecimal(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}