package com.lis.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.mapper.QCLotMapper;
import com.lis.qc.mapper.QCRecordMapper;
import com.lis.qc.service.QCLotService;
import com.lis.qc.support.QcQueryGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 质控品批号服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QCLotServiceImpl implements QCLotService {

    private final QCLotMapper qcLotMapper;
    private final QCRecordMapper qcRecordMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QCLot lot) {
        lot.setStatus(1);
        lot.setCreateTime(LocalDateTime.now());
        qcLotMapper.insert(lot);
        log.info("新增质控品批号: id={}, lotNo={}", lot.getId(), lot.getLotNo());
        return lot.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(QCLot lot) {
        if (lot.getId() == null) {
            throw new BusinessException("质控品批号 ID 不能为空");
        }
        qcLotMapper.updateById(lot);
        return true;
    }

    @Override
    public boolean delete(Long id) {
        return qcLotMapper.deleteById(id) > 0;
    }

    @Override
    public QCLot getById(Long id) {
        return qcLotMapper.selectById(id);
    }

    @Override
    public List<QCLot> listAll() {
        List<QCLot> list = qcLotMapper.selectList(new LambdaQueryWrapper<QCLot>()
                .orderByDesc(QCLot::getCreateTime));
        enrichNames(list);
        return list;
    }

    @Override
    public List<QCLot> listEnabled() {
        List<QCLot> list = qcLotMapper.selectList(new LambdaQueryWrapper<QCLot>()
                .eq(QCLot::getStatus, 1)
                .orderByDesc(QCLot::getCreateTime));
        enrichNames(list);
        return list;
    }

    /**
     * 批量填充检验指标名称和仪器名称 (非数据库字段)
     */
    private void enrichNames(List<QCLot> lots) {
        if (lots == null || lots.isEmpty()) return;
        Set<Long> itemIds = new HashSet<>();
        Set<Long> instrIds = new HashSet<>();
        for (QCLot lot : lots) {
            if (lot.getTestItemId() != null) itemIds.add(lot.getTestItemId());
            if (lot.getInstrumentId() != null) instrIds.add(lot.getInstrumentId());
        }
        Map<Long, String> itemNameMap = batchQueryNames(
                "SELECT id, item_name FROM lis_test_item WHERE id IN ", itemIds);
        Map<Long, String> instrNameMap = batchQueryNames(
                "SELECT id, instrument_name FROM lis_instrument WHERE id IN ", instrIds);
        for (QCLot lot : lots) {
            lot.setTestItemName(lot.getTestItemId() != null ? itemNameMap.get(lot.getTestItemId()) : null);
            lot.setInstrumentName(lot.getInstrumentId() != null ? instrNameMap.get(lot.getInstrumentId()) : null);
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

    // ==================== 重估靶值 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reestimate(Long lotId, Integer minSamples, String operator) {
        if (lotId == null) throw new BusinessException("批号ID不能为空");
        int min = (minSamples == null || minSamples < 2) ? 20 : minSamples;
        QCLot lot = qcLotMapper.selectById(lotId);
        if (lot == null) throw new BusinessException("质控品批号不存在: " + lotId);

        // 取最近 120 条在控(ACCEPT)记录按时间升序
        List<QCRecord> records = qcRecordMapper.selectList(new LambdaQueryWrapper<QCRecord>()
                .eq(QCRecord::getQcLotId, lotId)
                .eq(QCRecord::getQcResult, "ACCEPT")
                .orderByAsc(QCRecord::getMeasureTime)
                .last("LIMIT 120"));
        if (records == null || records.size() < min) {
            throw new BusinessException("在控质控记录不足, 共 " + (records == null ? 0 : records.size())
                    + " 条, 至少需要 " + min + " 条才能重估靶值和SD");
        }
        // 过滤有效测量值
        List<BigDecimal> vals = new ArrayList<>();
        for (QCRecord r : records) {
            if (r.getMeasureValue() != null) vals.add(r.getMeasureValue());
        }
        if (vals.size() < min) {
            throw new BusinessException("有效测量值不足, 共 " + vals.size() + " 条, 至少需要 " + min + " 条");
        }
        int n = vals.size();
        // 均值
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : vals) sum = sum.add(v);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        // 样本标准差 (Bessel 修正 n-1)
        BigDecimal sqSum = BigDecimal.ZERO;
        for (BigDecimal v : vals) {
            BigDecimal d = v.subtract(mean);
            sqSum = sqSum.add(d.multiply(d));
        }
        BigDecimal variance = sqSum.divide(BigDecimal.valueOf(n - 1L), 10, RoundingMode.HALF_UP);
        BigDecimal sd = bigSqrt(variance, 6);
        BigDecimal cv = (mean.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO
                : sd.divide(mean, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        BigDecimal oldTarget = lot.getTargetValue();
        BigDecimal oldSd = lot.getSd();

        // 写回批号表
        lot.setTargetValue(mean);
        lot.setSd(sd);
        lot.setCv(cv);
        qcLotMapper.updateById(lot);

        // 记录操作日志(写 remark 字段或打印到应用日志; 这里两者都做)
        log.warn("[重估靶值] lotId={} 操作人={} 样本数={} 旧靶值={} 旧SD={} 新靶值={} 新SD={} 新CV={}%",
                lotId, operator == null ? "unknown" : operator,
                n, oldTarget, oldSd, mean, sd, cv);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("lotId", lotId);
        resp.put("lotNo", lot.getLotNo());
        resp.put("oldTarget", oldTarget);
        resp.put("oldSd", oldSd);
        resp.put("newTarget", mean);
        resp.put("newSd", sd);
        resp.put("newCv", cv);
        resp.put("sampleCount", n);
        resp.put("operator", operator);
        resp.put("estimatedAt", LocalDateTime.now());
        return resp;
    }

    /** BigDecimal 开平方 (牛顿迭代, 保留指定 scale) */
    private static BigDecimal bigSqrt(BigDecimal value, int scale) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        BigDecimal x0 = BigDecimal.valueOf(Math.sqrt(value.doubleValue()));
        BigDecimal eps = BigDecimal.ONE.movePointLeft(scale + 2);
        for (int i = 0; i < 50; i++) {
            BigDecimal x1 = x0.add(value.divide(x0, scale + 4, RoundingMode.HALF_UP))
                    .divide(BigDecimal.valueOf(2), scale + 4, RoundingMode.HALF_UP);
            if (x1.subtract(x0).abs().compareTo(eps) < 0) {
                return x1.setScale(scale, RoundingMode.HALF_UP);
            }
            x0 = x1;
        }
        return x0.setScale(scale, RoundingMode.HALF_UP);
    }
}
