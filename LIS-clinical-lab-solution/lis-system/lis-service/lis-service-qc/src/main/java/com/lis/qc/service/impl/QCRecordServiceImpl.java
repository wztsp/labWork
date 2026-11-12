package com.lis.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.api.dto.QCRecordDTO;
import com.lis.api.dto.SampleToQcDTO;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.PageResult;
import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.mapper.QCLotMapper;
import com.lis.qc.mapper.QCRecordMapper;
import com.lis.qc.service.OccService;
import com.lis.qc.service.QCRecordService;
import com.lis.qc.service.rule.QCResult;
import com.lis.qc.service.rule.WestgardRuleEngine;
import com.lis.qc.support.QcQueryGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 质控记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QCRecordServiceImpl implements QCRecordService {

    /** 用于多规则评估的历史记录数 (足够覆盖 10x 规则) */
    private static final int RECENT_LIMIT = 20;

    private final QCRecordMapper qcRecordMapper;
    private final QCLotMapper qcLotMapper;
    private final WestgardRuleEngine westgardRuleEngine;
    private final JdbcTemplate jdbcTemplate;
    private final OccService occService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QCResult submitRecord(QCRecordDTO dto) {
        QCLot lot = qcLotMapper.selectById(dto.getQcLotId());
        if (lot == null) {
            throw new BusinessException("质控品批号不存在: " + dto.getQcLotId());
        }
        // 1. 计算并保存记录
        QCRecord record = new QCRecord();
        record.setQcLotId(dto.getQcLotId());
        // 从批号表获取 testItemId 和 instrumentId，避免前端未传或传错
        record.setTestItemId(lot.getTestItemId());
        record.setInstrumentId(lot.getInstrumentId());
        record.setMeasureValue(dto.getMeasureValue());
        // 测量时间默认当前时间
        record.setMeasureTime(dto.getMeasureTime() != null ? dto.getMeasureTime() : LocalDateTime.now());
        record.setOperatorId(dto.getOperatorId());
        record.setCreateTime(LocalDateTime.now());

        BigDecimal zScore = calcZScore(dto.getMeasureValue(), lot);
        // 强制保留 6 位小数: 防止持久化时被列 scale 或驱动 round(曾发生 z=2.995 被 DB 舍入为 3 导致展示/持久化值与实际计算不一致)
        // 注: Westgard 规则评估不使用 record.getZScore(), 而是 Rule12s/Rule13s 等按 measureValue + targetValue + sd 实时重算 z,
        // 即使持久化字段异常也不会误判规则; 此处强制 setScale 保证前端/报表看到的 z 与规则计算值一致。
        if (zScore != null) zScore = zScore.setScale(6, RoundingMode.HALF_UP);
        record.setZScore(zScore);

        // 2. 先保存，让 Westgard 规则评估可读到当前记录
        qcRecordMapper.insert(record);

        // 3. 取最近若干条历史 (含当前点)
        List<QCRecord> recent = listRecentByLotId(dto.getQcLotId(), RECENT_LIMIT);

        // 4. 调用引擎判定
        QCResult result = westgardRuleEngine.evaluate(recent, lot);
        applyResult(record, result);
        qcRecordMapper.updateById(record);
        // 失控判定自动产生失控报告 (与失控数据关联)
        autoCreateOccIfReject(lot, record, result,
                dto.getOperatorId() == null ? null : String.valueOf(dto.getOperatorId()));

        log.info("质控录入完成: lotId={}, value={}, z={}, result={}, rule={}",
                dto.getQcLotId(), dto.getMeasureValue(), zScore,
                record.getQcResult(),
                record.getRuleViolation() == null ? "NONE" : record.getRuleViolation());
        return result;
    }

    /**
     * 将判定结果写入质控记录。
     * REJECT -> qcResult=REJECT 记录规则; WARNING -> qcResult=ACCEPT 但记录警告规则; ACCEPT -> 清空规则。
     */
    private void applyResult(QCRecord record, QCResult result) {
        String level = result.getLevel();
        if (QCResult.LEVEL_REJECT.equals(level)) {
            record.setRuleViolation(result.getRuleName());
            record.setQcResult("REJECT");
        } else if (QCResult.LEVEL_WARNING.equals(level)) {
            // 警告: 仍为在控, 但记录触发的警告规则
            record.setRuleViolation(result.getRuleName());
            record.setQcResult("ACCEPT");
        } else {
            record.setRuleViolation(null);
            record.setQcResult("ACCEPT");
        }
    }

    /**
     * 失控(REJECT)后自动产生失控报告, 与失控数据通过 lot_id 关联。
     * 失控报告来源为失控计算/判定, 而非失控报告界面手工建档。异常不阻断主流程。
     */
    private void autoCreateOccIfReject(QCLot lot, QCRecord record, QCResult result, String operator) {
        if (lot == null || record == null || result == null) return;
        if (!QCResult.LEVEL_REJECT.equals(result.getLevel())) return;
        try {
            occService.createFromReject(lot, record, result, operator == null ? "op" : operator);
            log.warn("失控判定自动产生失控报告: lotId={}, rule={}, value={}",
                    lot.getId(), result.getRuleName(), record.getMeasureValue());
        } catch (Exception e) {
            log.error("自动产生失控报告失败: lotId={}, msg={}", lot.getId(), e.getMessage(), e);
        }
    }

    @Override
    public PageResult<QCRecord> queryPage(int current, int size, Long lotId, Long testItemId,
                                           Long instrumentId, String measureDate, String beginDate, String endDate) {
        LambdaQueryWrapper<QCRecord> wrapper = new LambdaQueryWrapper<>();
        if (lotId != null) {
            wrapper.eq(QCRecord::getQcLotId, lotId);
        }
        if (testItemId != null) {
            wrapper.eq(QCRecord::getTestItemId, testItemId);
        }
        if (instrumentId != null) {
            wrapper.eq(QCRecord::getInstrumentId, instrumentId);
        }
        if (beginDate != null && !beginDate.isEmpty()) {
            LocalDate bd = LocalDate.parse(beginDate);
            wrapper.ge(QCRecord::getMeasureTime, bd.atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            LocalDate ed = LocalDate.parse(endDate);
            wrapper.le(QCRecord::getMeasureTime, ed.plusDays(1).atStartOfDay());
        }
        if ((beginDate == null || beginDate.isEmpty()) && (endDate == null || endDate.isEmpty())
                && measureDate != null && !measureDate.isEmpty()) {
            LocalDate date = LocalDate.parse(measureDate);
            wrapper.ge(QCRecord::getMeasureTime, date.atStartOfDay());
            wrapper.le(QCRecord::getMeasureTime, date.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(QCRecord::getMeasureTime);
        Page<QCRecord> page = qcRecordMapper.selectPage(new Page<>(current, size), wrapper);
        // 批量填充检验指标名称、仪器名称、批号
        enrichRecordNames(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 批量填充检验指标名称、仪器名称、批号 (非数据库字段)
     * 优先使用批号表(lis_qc_lot)中的 testItemId 和 instrumentId，避免记录表中数据不一致
     */
    private void enrichRecordNames(List<QCRecord> records) {
        if (records == null || records.isEmpty()) return;
        // 收集需要查询的ID
        java.util.Set<Long> lotIds = new java.util.HashSet<>();
        for (QCRecord r : records) {
            if (r.getQcLotId() != null) lotIds.add(r.getQcLotId());
        }

        // 查询批号信息: id -> (lotNo, testItemId, instrumentId)
        Map<Long, Object[]> lotInfoMap = new HashMap<>();
        if (!lotIds.isEmpty()) {
            // B-3 修复: IN 子句改为 ?,?,? 参数绑定, 不再把 id 拼进 SQL 字面量
            List<Object> args = new ArrayList<>(lotIds);
            String sql = "SELECT id, lot_no, test_item_id, instrument_id FROM lis_qc_lot WHERE id IN "
                    + QcQueryGuard.inClause(args.size());
            jdbcTemplate.query(sql, rs -> {
                lotInfoMap.put(rs.getLong("id"), new Object[]{
                        rs.getString("lot_no"),
                        rs.getLong("test_item_id"),
                        rs.getLong("instrument_id")
                });
            }, args.toArray());
        }

        // 收集批号对应的 testItemId 和 instrumentId (优先使用批号表中的值)
        java.util.Set<Long> itemIds = new java.util.HashSet<>();
        java.util.Set<Long> instrIds = new java.util.HashSet<>();
        for (QCRecord r : records) {
            Object[] lotInfo = r.getQcLotId() != null ? lotInfoMap.get(r.getQcLotId()) : null;
            Long itemId = lotInfo != null ? (Long) lotInfo[1] : r.getTestItemId();
            Long instrId = lotInfo != null ? (Long) lotInfo[2] : r.getInstrumentId();
            if (itemId != null) itemIds.add(itemId);
            if (instrId != null) instrIds.add(instrId);
        }

        // 批量查询检验指标名称
        Map<Long, String> itemNameMap = batchQueryNames(
                "SELECT id, item_name FROM lis_test_item WHERE id IN ", itemIds);
        // 批量查询仪器名称
        Map<Long, String> instrNameMap = batchQueryNames(
                "SELECT id, instrument_name FROM lis_instrument WHERE id IN ", instrIds);

        // 填充: 优先使用批号表中的 testItemId/instrumentId
        for (QCRecord r : records) {
            Object[] lotInfo = r.getQcLotId() != null ? lotInfoMap.get(r.getQcLotId()) : null;
            if (lotInfo != null) {
                // 使用批号表中的信息
                r.setLotNo((String) lotInfo[0]);
                Long itemId = (Long) lotInfo[1];
                Long instrId = (Long) lotInfo[2];
                r.setTestItemName(itemId != null ? itemNameMap.get(itemId) : null);
                r.setInstrumentName(instrId != null ? instrNameMap.get(instrId) : null);
                // 同步修正记录表中的 testItemId 和 instrumentId
                r.setTestItemId(itemId);
                r.setInstrumentId(instrId);
            } else {
                // 回退到记录表中的值
                r.setTestItemName(r.getTestItemId() != null ? itemNameMap.get(r.getTestItemId()) : null);
                r.setInstrumentName(r.getInstrumentId() != null ? instrNameMap.get(r.getInstrumentId()) : null);
            }
        }
    }

    /**
     * 批量查询ID→名称映射。
     * <p>
     * B-3 修复: IN 子句由 {@link QcQueryGuard#inClause(int)} 生成 {@code (?,?,?)} 占位符,
     * id 一律走 JDBC 参数绑定, 不再拼接进 SQL 字面量。
     *
     * @param sqlPrefix 以 {@code " ... WHERE id IN "} 结尾的 SQL 前缀 (必须为代码内常量, 不得含外部输入)
     * @param ids       待查询的 ID 集合, 为空时直接返回空 Map (不发 SQL)
     * @return id → name 映射, 永不为 null
     */
    private Map<Long, String> batchQueryNames(String sqlPrefix, java.util.Set<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        if (QcQueryGuard.isEmpty(ids)) return result;
        List<Object> args = new ArrayList<>(ids);
        String sql = sqlPrefix + QcQueryGuard.inClause(args.size());
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getLong("id"), rs.getString(2));
        }, args.toArray());
        return result;
    }

    @Override
    public List<QCRecord> listByLotId(Long lotId) {
        return qcRecordMapper.selectList(new LambdaQueryWrapper<QCRecord>()
                .eq(QCRecord::getQcLotId, lotId)
                .orderByAsc(QCRecord::getMeasureTime));
    }

    @Override
    public List<QCRecord> listRecentByLotId(Long lotId, int limit) {
        // 先取最近 limit 条 (desc)，再反转为升序便于规则评估
        // B-3 修复: 原 .last("LIMIT " + limit) 字符串拼接 -> MP 分页插件预编译 LIMIT ?, 且 limit 受钳制
        int safeLimit = QcQueryGuard.clampLimit(limit, RECENT_LIMIT, QcQueryGuard.MAX_LIMIT);
        Page<QCRecord> page = qcRecordMapper.selectPage(
                new Page<>(1, safeLimit, false),
                new LambdaQueryWrapper<QCRecord>()
                        .eq(QCRecord::getQcLotId, lotId)
                        .orderByDesc(QCRecord::getMeasureTime));
        List<QCRecord> desc = new ArrayList<>();
        if (page != null && page.getRecords() != null) {
            desc.addAll(page.getRecords());
        }
        Collections.reverse(desc);
        return desc;
    }

    /**
     * 计算 Z 分数 = (实测值 - 靶值) / SD, 结果保留 6 位小数.
     *
     * 重要: 本方法计算的 z 值仅用于持久化 (lis_qc_record.z_score 展示字段).
     * Westgard 多规则评估始终使用各规则实现 (Rule12s 等) 的 calcZScore 实时重算,
     * 不会读取 record.getZScore() — 这样即使持久化列被 DBA 修改精度或手工修改,
     * 也不会影响规则判定 (确保 1-3s 在 z>=3 才触发, 不会因 DB round 把 2.995 误判成 3.0).
     */
    private BigDecimal calcZScore(BigDecimal measure, QCLot lot) {
        if (measure == null || lot.getTargetValue() == null || lot.getSd() == null
                || lot.getSd().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return measure.subtract(lot.getTargetValue())
                .divide(lot.getSd(), 6, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QCResult updateRecord(Long id, QCRecordDTO dto) {
        QCRecord record = qcRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("质控记录不存在: " + id);
        }
        QCLot lot = qcLotMapper.selectById(record.getQcLotId());
        if (lot == null) {
            throw new BusinessException("质控品批号不存在: " + record.getQcLotId());
        }
        // 更新字段
        if (dto.getMeasureValue() != null) {
            record.setMeasureValue(dto.getMeasureValue());
        }
        if (dto.getMeasureTime() != null) {
            record.setMeasureTime(dto.getMeasureTime());
        }
        // 重新计算Z分数
        BigDecimal zScore = calcZScore(record.getMeasureValue(), lot);
        // 同 submitRecord: 强制 6 位小数避免持久化 round-up
        if (zScore != null) zScore = zScore.setScale(6, RoundingMode.HALF_UP);
        record.setZScore(zScore);

        // 先保存
        qcRecordMapper.updateById(record);

        // 重新判定: 取最近若干条历史 (含当前点)
        List<QCRecord> recent = listRecentByLotId(record.getQcLotId(), RECENT_LIMIT);

        // 调用引擎判定
        QCResult result = westgardRuleEngine.evaluate(recent, lot);
        applyResult(record, result);
        qcRecordMapper.updateById(record);
        // 失控判定自动产生失控报告 (与失控数据关联)
        autoCreateOccIfReject(lot, record, result, "update");

        log.info("质控记录更新完成: id={}, value={}, z={}, result={}, rule={}",
                id, record.getMeasureValue(), zScore,
                record.getQcResult(),
                record.getRuleViolation() == null ? "NONE" : record.getRuleViolation());
        return result;
    }

    @Override
    public Map<String, Integer> batchRecalculate(Long lotId, String beginDate, String endDate) {
        QCLot lot = qcLotMapper.selectById(lotId);
        if (lot == null) {
            throw new BusinessException("质控品批号不存在: " + lotId);
        }
        // 1. 查该 lot 全部记录按 measureTime 升序 (含时间范围外的上下文, 作为规则历史窗口)
        LambdaQueryWrapper<QCRecord> wrapper = new LambdaQueryWrapper<QCRecord>()
                .eq(QCRecord::getQcLotId, lotId)
                .orderByAsc(QCRecord::getMeasureTime);
        List<QCRecord> all = qcRecordMapper.selectList(wrapper);
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", 0);
        stats.put("rejectCount", 0);
        stats.put("updatedCount", 0);
        if (all.isEmpty()) {
            return stats;
        }
        // 2. 解析时间范围 (beginDate 当天 00:00:00, endDate 次日 00:00:00, 即闭区间到当天末)
        LocalDateTime begin = null;
        LocalDateTime end = null;
        if (beginDate != null && !beginDate.isEmpty()) {
            begin = LocalDate.parse(beginDate).atStartOfDay();
        }
        if (endDate != null && !endDate.isEmpty()) {
            end = LocalDate.parse(endDate).plusDays(1).atStartOfDay();
        }

        // 3. 使用批号设置的靶值和SD (与 submitRecord 保持一致)
        BigDecimal targetValue = lot.getTargetValue();
        BigDecimal lotSd = lot.getSd();
        if (targetValue == null || lotSd == null) {
            throw new BusinessException("批号未设置靶值或SD, 无法计算失控");
        }
        log.info("批量重算使用批号设置: targetValue={}, sd={}", targetValue, lotSd);

        // 4. 逐条重算时间范围内的记录, 每条取其前 RECENT_LIMIT-1 条+自身作为规则评估窗口
        int rejectCount = 0;
        int updatedCount = 0;
        int totalCount = 0;
        int errorCount = 0;
        for (int i = 0; i < all.size(); i++) {
            QCRecord rec = all.get(i);
            LocalDateTime mt = rec.getMeasureTime();
            if (begin != null && mt.isBefore(begin)) continue;
            if (end != null && !mt.isBefore(end)) continue;
            totalCount++;
            try {
                // 历史窗口: all[max(0, i-19) .. i], 最后一个为当前点
                int from = Math.max(0, i - RECENT_LIMIT + 1);
                List<QCRecord> recent = new ArrayList<>(all.subList(from, i + 1));
                // 使用批号设置的靶值和SD重算 z_score (6 位小数)
                BigDecimal zScore = calcZScore(rec.getMeasureValue(), targetValue, lotSd);
                if (zScore != null) zScore = zScore.setScale(6, RoundingMode.HALF_UP);
                rec.setZScore(zScore);
                // 调用引擎判定 (使用批号设置的靶值和SD)
                QCResult result = westgardRuleEngine.evaluate(recent, lot);
                log.info("质控记录评估: id={}, value={}, target={}, sd={}, zScore={}, level={}, rule={}",
                        rec.getId(), rec.getMeasureValue(), targetValue, lotSd, zScore,
                        result.getLevel(), result.getRuleName());
                String oldResult = rec.getQcResult();
                String oldRule = rec.getRuleViolation();
                applyResult(rec, result);
                if ("REJECT".equals(rec.getQcResult())) {
                    rejectCount++;
                    // 失控计算产生的失控数据自动产生(或复用)失控报告 (独立事务, 失败不影响质控记录更新)
                    try {
                        autoCreateOccIfReject(lot, rec, result, "batch");
                    } catch (Exception oe) {
                        log.error("自动产生失控报告失败(已隔离, 不影响质控重算): recordId={}, msg={}",
                                rec.getId(), oe.getMessage());
                        errorCount++;
                    }
                }
                if (!java.util.Objects.equals(oldResult, rec.getQcResult())
                        || !java.util.Objects.equals(oldRule, rec.getRuleViolation())) {
                    updatedCount++;
                }
                qcRecordMapper.updateById(rec);
            } catch (Exception e) {
                errorCount++;
                log.error("单条质控记录重算失败(已跳过继续): recordId={}, msg={}", rec.getId(), e.getMessage(), e);
            }
        }
        stats.put("total", totalCount);
        stats.put("rejectCount", rejectCount);
        stats.put("updatedCount", updatedCount);
        stats.put("errorCount", errorCount);
        log.info("批量重算失控完成: lotId={}, 范围={}~{}, 靶值={}, SD={}, total={}, reject={}, updated={}, errors={}",
                lotId, beginDate, endDate, targetValue, lotSd, totalCount, rejectCount, updatedCount, errorCount);
        return stats;
    }

    /** 使用实际均值和SD计算Z分数 */
    private BigDecimal calcZScore(BigDecimal measure, BigDecimal mean, BigDecimal sd) {
        if (measure == null || mean == null || sd == null || sd.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return measure.subtract(mean).divide(sd, 6, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<QCRecord> createFromSample(SampleToQcDTO dto) {
        Long sampleId = dto.getSampleId();
        if (sampleId == null) {
            throw new BusinessException("标本ID不能为空");
        }
        List<SampleToQcDTO.Mapping> mappings = dto.getMappings();
        if (mappings == null || mappings.isEmpty()) {
            throw new BusinessException("请至少选择一个检验指标与质控批号的映射");
        }

        List<QCRecord> created = new ArrayList<>();
        for (SampleToQcDTO.Mapping m : mappings) {
            if (m.getQcLotId() == null) continue;
            QCLot lot = qcLotMapper.selectById(m.getQcLotId());
            if (lot == null) {
                throw new BusinessException("质控品批号不存在: " + m.getQcLotId());
            }
            // 读取该标本对应指标的实测值
            String resultValue = queryResultValue(sampleId, m.getTestItemId());
            if (resultValue == null || resultValue.trim().isEmpty()) {
                log.warn("标本[{}]指标[{}]无实测值, 跳过转换", sampleId, m.getTestItemId());
                continue;
            }
            BigDecimal measure;
            try {
                measure = new BigDecimal(resultValue.trim());
            } catch (NumberFormatException e) {
                log.warn("标本[{}]指标[{}]实测值[{}]非数值, 跳过转换", sampleId, m.getTestItemId(), resultValue);
                continue;
            }

            QCRecord record = new QCRecord();
            record.setQcLotId(lot.getId());
            record.setTestItemId(lot.getTestItemId());
            record.setInstrumentId(lot.getInstrumentId());
            record.setSampleId(sampleId);
            record.setMeasureValue(measure);
            record.setMeasureTime(LocalDateTime.now());
            record.setOperatorId(dto.getOperatorId());
            record.setCreateTime(LocalDateTime.now());

            BigDecimal zScore = calcZScore(measure, lot);
            if (zScore != null) zScore = zScore.setScale(6, RoundingMode.HALF_UP);
            record.setZScore(zScore);

            qcRecordMapper.insert(record);

            List<QCRecord> recent = listRecentByLotId(lot.getId(), RECENT_LIMIT);
            QCResult result = westgardRuleEngine.evaluate(recent, lot);
            applyResult(record, result);
            qcRecordMapper.updateById(record);
            // 失控判定自动产生失控报告 (与失控数据关联)
            autoCreateOccIfReject(lot, record, result,
                    dto.getOperatorId() == null ? null : String.valueOf(dto.getOperatorId()));
            created.add(record);
        }

        // 将标本标记为质控类型
        jdbcTemplate.update("UPDATE lis_sample SET sample_type = 'QC' WHERE id = ?", sampleId);
        log.info("手工标本转质控完成: sampleId={}, 生成质控记录 {} 条", sampleId, created.size());
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBySampleId(Long sampleId) {
        if (sampleId == null) {
            throw new BusinessException("标本ID不能为空");
        }
        int deleted = qcRecordMapper.delete(
                new LambdaQueryWrapper<QCRecord>().eq(QCRecord::getSampleId, sampleId));
        // 清除标本的质控标记
        jdbcTemplate.update("UPDATE lis_sample SET sample_type = NULL WHERE id = ?", sampleId);
        log.info("取消质控完成: sampleId={}, 删除质控记录 {} 条", sampleId, deleted);
        return deleted;
    }

    /** 查询来源标本某指标的实测值 */
    private String queryResultValue(Long sampleId, Long testItemId) {
        if (testItemId == null) return null;
        List<String> values = jdbcTemplate.query(
                "SELECT result_value FROM lis_result WHERE sample_id = ? AND test_item_id = ? LIMIT 1",
                rs -> {
                    java.util.List<String> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(rs.getString("result_value"));
                    }
                    return list;
                }, sampleId, testItemId);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
