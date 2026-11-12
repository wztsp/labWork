package com.lis.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.qc.entity.QcAlert;
import com.lis.qc.mapper.QcAlertMapper;
import com.lis.qc.service.QcAlertService;
import com.lis.qc.support.QcQueryGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 质控偏差趋势预警服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QcAlertServiceImpl implements QcAlertService {

    private final QcAlertMapper qcAlertMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAlert(Map<String, Object> payload) {
        QcAlert alert = new QcAlert();
        alert.setAlertType(getStr(payload, "alertType"));
        alert.setLotId(getLong(payload, "lotId"));
        alert.setTestItemId(getLong(payload, "testItemId"));
        alert.setInstrumentId(getLong(payload, "instrumentId"));
        alert.setLastMeasureValue(getBigDecimal(payload, "lastMeasureValue"));
        alert.setLastZScore(getBigDecimal(payload, "lastZScore"));
        alert.setMessage(getStr(payload, "message"));
        alert.setStatus("PENDING");
        LocalDateTime now = LocalDateTime.now();
        alert.setAlertTime(now);
        alert.setCreateTime(now);
        qcAlertMapper.insert(alert);
        log.warn("质控趋势预警已入库: id={}, message={}", alert.getId(), alert.getMessage());
    }

    @Override
    public PageResult<QcAlert> queryPage(int current, int size, String status) {
        LambdaQueryWrapper<QcAlert> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(QcAlert::getStatus, status);
        }
        wrapper.orderByDesc(QcAlert::getAlertTime);
        Page<QcAlert> page = qcAlertMapper.selectPage(new Page<>(current, size), wrapper);
        enrichNames(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public boolean markRead(Long id) {
        QcAlert alert = qcAlertMapper.selectById(id);
        if (alert == null) {
            return false;
        }
        alert.setStatus("READ");
        qcAlertMapper.updateById(alert);
        return true;
    }

    /** 批量填充检验指标名称、仪器名称、批号 */
    private void enrichNames(List<QcAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) return;
        Set<Long> lotIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        Set<Long> instrIds = new HashSet<>();
        for (QcAlert a : alerts) {
            if (a.getLotId() != null) lotIds.add(a.getLotId());
            if (a.getTestItemId() != null) itemIds.add(a.getTestItemId());
            if (a.getInstrumentId() != null) instrIds.add(a.getInstrumentId());
        }
        Map<Long, String> lotNameMap = batchQueryNames("SELECT id, lot_no FROM lis_qc_lot WHERE id IN ", lotIds);
        Map<Long, String> itemNameMap = batchQueryNames("SELECT id, item_name FROM lis_test_item WHERE id IN ", itemIds);
        Map<Long, String> instrNameMap = batchQueryNames("SELECT id, instrument_name FROM lis_instrument WHERE id IN ", instrIds);
        for (QcAlert a : alerts) {
            a.setLotNo(a.getLotId() != null ? lotNameMap.get(a.getLotId()) : null);
            a.setTestItemName(a.getTestItemId() != null ? itemNameMap.get(a.getTestItemId()) : null);
            a.setInstrumentName(a.getInstrumentId() != null ? instrNameMap.get(a.getInstrumentId()) : null);
        }
    }

    /**
     * 批量查询ID→名称映射。
     * <p>
     * B-3 修复: IN 子句改为 {@code (?,?,?)} 参数绑定, id 不再拼接进 SQL 字面量。
     *
     * @param sqlPrefix 以 {@code " ... WHERE id IN "} 结尾的 SQL 前缀 (代码内常量)
     * @param ids       待查询 ID 集合, 为空则不发 SQL
     * @return id → name 映射, 永不为 null
     */
    private Map<Long, String> batchQueryNames(String sqlPrefix, Set<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        if (QcQueryGuard.isEmpty(ids)) return result;
        List<Object> args = new ArrayList<>(ids);
        String sql = sqlPrefix + QcQueryGuard.inClause(args.size());
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getLong("id"), rs.getString(2));
        }, args.toArray());
        return result;
    }

    private String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
