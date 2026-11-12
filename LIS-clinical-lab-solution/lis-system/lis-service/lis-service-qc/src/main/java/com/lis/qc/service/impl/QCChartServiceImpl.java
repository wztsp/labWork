package com.lis.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.exception.BusinessException;
import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.mapper.QCLotMapper;
import com.lis.qc.mapper.QCRecordMapper;
import com.lis.qc.service.QCChartService;
import com.lis.qc.support.QcQueryGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 质控图数据服务实现
 */
@Service
@RequiredArgsConstructor
public class QCChartServiceImpl implements QCChartService {

    private final QCLotMapper qcLotMapper;
    private final QCRecordMapper qcRecordMapper;

    @Override
    public Map<String, Object> getLeveyJenningsData(Long lotId, int limit, LocalDate beginDate, LocalDate endDate) {
        QCLot lot = qcLotMapper.selectById(lotId);
        if (lot == null) {
            throw new BusinessException("质控品批号不存在: " + lotId);
        }
        // 通过 MP 分页插件生成预编译 LIMIT ?, 并对 limit 做上限钳制 (B-3: 消除 SQL 拼接)
        List<QCRecord> records = loadRecordsAsc(lotId, limit, beginDate, endDate);

        // 计算实际均值和SD (基于当前返回的所有测量数据)
        BigDecimal actualMean = null;
        BigDecimal actualSd = null;
        if (!records.isEmpty()) {
            double[] values = records.stream()
                    .map(QCRecord::getMeasureValue)
                    .filter(java.util.Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .toArray();
            if (values.length > 0) {
                double mean = java.util.stream.DoubleStream.of(values).average().orElse(0);
                // 样本标准差 (n-1分母), 数据量>=2时才计算SD
                double variance = 0;
                if (values.length >= 2) {
                    variance = java.util.stream.DoubleStream.of(values)
                            .map(v -> Math.pow(v - mean, 2))
                            .sum() / (values.length - 1);
                }
                double std = Math.sqrt(variance);
                actualMean = BigDecimal.valueOf(mean).setScale(4, RoundingMode.HALF_UP);
                actualSd = BigDecimal.valueOf(std).setScale(4, RoundingMode.HALF_UP);
            }
        }

        // 使用设置的靶值和SD计算Z分数: Z = (测量值 - 靶值) / 设置SD
        final BigDecimal targetForCalc = lot.getTargetValue();
        final BigDecimal lotSdForCalc = lot.getSd();
        List<Map<String, Object>> points = records.stream()
                .map(r -> {
                    Map<String, Object> p = new HashMap<>();
                    p.put("measureValue", r.getMeasureValue());
                    // Z分数用设置的靶值和设置SD计算
                    BigDecimal z = null;
                    if (r.getMeasureValue() != null && targetForCalc != null && lotSdForCalc != null
                            && lotSdForCalc.compareTo(BigDecimal.ZERO) != 0) {
                        z = r.getMeasureValue().subtract(targetForCalc)
                                .divide(lotSdForCalc, 4, RoundingMode.HALF_UP);
                    }
                    p.put("zScore", z);
                    p.put("measureTime", r.getMeasureTime());
                    p.put("qcResult", r.getQcResult());
                    p.put("ruleViolation", r.getRuleViolation());
                    return p;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        // 靶值/SD (批号预设)
        BigDecimal targetValue = lot.getTargetValue();
        BigDecimal lotSd = lot.getSd();
        result.put("targetValue", targetValue);
        result.put("sd", lotSd);
        result.put("cv", lot.getCv());
        result.put("level", lot.getLevel());
        result.put("lotNo", lot.getLotNo());
        // 实际均值和实际SD (用于对比显示)
        result.put("actualMean", actualMean);
        result.put("actualSd", actualSd);
        // 上下控制限 (基于设置的靶值和SD)
        if (targetValue != null && lotSd != null) {
            result.put("mean", targetValue);
            result.put("upper3sd", targetValue.add(lotSd.multiply(BigDecimal.valueOf(3))));
            result.put("upper2sd", targetValue.add(lotSd.multiply(BigDecimal.valueOf(2))));
            result.put("upper1sd", targetValue.add(lotSd));
            result.put("lower1sd", targetValue.subtract(lotSd));
            result.put("lower2sd", targetValue.subtract(lotSd.multiply(BigDecimal.valueOf(2))));
            result.put("lower3sd", targetValue.subtract(lotSd.multiply(BigDecimal.valueOf(3))));
        }
        result.put("points", points);
        return result;
    }

    @Override
    public Map<String, Object> getZScoreData(Long lotId, int limit, LocalDate beginDate, LocalDate endDate) {
        QCLot lot = qcLotMapper.selectById(lotId);
        if (lot == null) {
            throw new BusinessException("质控品批号不存在: " + lotId);
        }
        // 通过 MP 分页插件生成预编译 LIMIT ?, 并对 limit 做上限钳制 (B-3: 消除 SQL 拼接)
        List<QCRecord> records = loadRecordsAsc(lotId, limit, beginDate, endDate);

        // 计算实际均值和SD (基于当前返回的所有测量数据, 仅用于显示对比)
        BigDecimal actualMean = null;
        BigDecimal actualSd = null;
        if (!records.isEmpty()) {
            double[] values = records.stream()
                    .map(QCRecord::getMeasureValue)
                    .filter(java.util.Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .toArray();
            if (values.length > 0) {
                double mean = java.util.stream.DoubleStream.of(values).average().orElse(0);
                double variance = 0;
                if (values.length >= 2) {
                    variance = java.util.stream.DoubleStream.of(values)
                            .map(v -> Math.pow(v - mean, 2))
                            .sum() / (values.length - 1);
                }
                double std = Math.sqrt(variance);
                actualMean = BigDecimal.valueOf(mean).setScale(4, RoundingMode.HALF_UP);
                actualSd = BigDecimal.valueOf(std).setScale(4, RoundingMode.HALF_UP);
            }
        }

        // 使用设置的靶值和SD计算Z分数 (与Levey-Jennings图/失控判定保持一致)
        final BigDecimal targetForCalc = lot.getTargetValue();
        final BigDecimal lotSdForCalc = lot.getSd();
        List<Map<String, Object>> points = records.stream()
                .map(r -> {
                    Map<String, Object> p = new HashMap<>();
                    BigDecimal z = null;
                    if (r.getMeasureValue() != null && targetForCalc != null && lotSdForCalc != null
                            && lotSdForCalc.compareTo(BigDecimal.ZERO) != 0) {
                        z = r.getMeasureValue().subtract(targetForCalc)
                                .divide(lotSdForCalc, 4, RoundingMode.HALF_UP);
                    }
                    p.put("zScore", z);
                    p.put("measureValue", r.getMeasureValue());
                    p.put("measureTime", r.getMeasureTime());
                    p.put("qcResult", r.getQcResult());
                    p.put("ruleViolation", r.getRuleViolation());
                    return p;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("lotId", lotId);
        result.put("lotNo", lot.getLotNo());
        result.put("targetValue", targetForCalc);
        result.put("sd", lotSdForCalc);
        result.put("actualMean", actualMean);
        result.put("actualSd", actualSd);
        result.put("points", points);
        return result;
    }

    /**
     * 按批号取最近 limit 条质控记录, 并反转为时间升序 (便于前端按时间绘制)。
     * <p>
     * B-3 修复: 原实现为 {@code .last("LIMIT " + limit)} 字符串拼接, 现改为 MyBatis-Plus 分页插件,
     * 由 MP 生成预编译 {@code LIMIT ?}; 同时 limit 经 {@link QcQueryGuard#clampLimit(Integer)}
     * 钳制到 [1, 1000], 杜绝全表扫描。{@code new Page<>(1, n, false)} 的 false 表示不执行 count 查询。
     *
     * @param lotId     质控品批号ID
     * @param limit     期望返回的最大条数 (会被钳制)
     * @param beginDate 起始日期 (含), 可为 null
     * @param endDate   截止日期 (含), 可为 null
     * @return 时间升序的质控记录列表, 永不为 null
     */
    private List<QCRecord> loadRecordsAsc(Long lotId, int limit, LocalDate beginDate, LocalDate endDate) {
        int safeLimit = QcQueryGuard.clampLimit(limit);
        LambdaQueryWrapper<QCRecord> wrapper = applyDateRange(
                new LambdaQueryWrapper<QCRecord>()
                        .eq(QCRecord::getQcLotId, lotId)
                        .orderByDesc(QCRecord::getMeasureTime),
                beginDate, endDate);
        Page<QCRecord> page = qcRecordMapper.selectPage(new Page<>(1, safeLimit, false), wrapper);
        List<QCRecord> records = new ArrayList<>();
        if (page != null && page.getRecords() != null) {
            records.addAll(page.getRecords());
        }
        // 反转为升序，便于前端按时间绘制
        Collections.reverse(records);
        return records;
    }

    /**
     * 应用日期范围过滤: beginDate 含当天 00:00:00, endDate 含当天 23:59:59
     */
    private LambdaQueryWrapper<QCRecord> applyDateRange(LambdaQueryWrapper<QCRecord> wrapper,
                                                        LocalDate beginDate, LocalDate endDate) {
        if (beginDate != null) {
            wrapper.ge(QCRecord::getMeasureTime, beginDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(QCRecord::getMeasureTime, endDate.plusDays(1).atStartOfDay());
        }
        return wrapper;
    }
}
