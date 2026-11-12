package com.lis.instrument.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.api.dto.QCRecordDTO;
import com.lis.api.dto.ResultDTO;
import com.lis.api.dto.UnmatchedBatchToQcDTO;
import com.lis.api.service.ResultPersistencePort;
import com.lis.common.core.exception.BusinessException;
import com.lis.instrument.entity.Instrument;
import com.lis.instrument.entity.InstrumentUnmatchedResult;
import com.lis.instrument.entity.Sample;
import com.lis.instrument.mapper.InstrumentCommMapper;
import com.lis.instrument.mapper.SampleMatchMapper;
import com.lis.instrument.mapper.UnmatchedResultMapper;
import com.lis.instrument.service.UnmatchedResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 仪器「无主标本」结果服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnmatchedResultServiceImpl implements UnmatchedResultService {

    private final UnmatchedResultMapper unmatchedResultMapper;
    private final SampleMatchMapper sampleMapper;
    private final InstrumentCommMapper instrumentMapper;
    private final ObjectProvider<ResultPersistencePort> resultPersistencePortProvider;
    /**
     * 质控记录服务 (monolith 模式下可用，通过 ObjectProvider 按类名反射查找
     * 避免在 instrument 模块 pom 直接引入 lis-service-qc —— 保持部署灵活性)。
     *
     * 可用实现：com.lis.qc.service.QCRecordService.submitRecord(QCRecordDTO)
     */
    private final ObjectProvider<Object> qcRecordServiceProvider;
    /** 反向解析 testCode → testItemId / 查询质控品批号 */
    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Value("${mq.exchange:lis.exchange}")
    private String exchange;
    @Value("${mq.qc.data-routing-key:qc.data}")
    private String qcRoutingKey;

    // 通过反射缓存 submitRecord 方法（若 QCRecordService bean 存在）
    private volatile Method submitRecordMethodCache;

    @Override
    public void saveUnmatched(Long instrumentId, List<ResultDTO> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        String instrumentName = resolveInstrumentName(instrumentId);
        LocalDateTime now = LocalDateTime.now();
        List<InstrumentUnmatchedResult> rows = new ArrayList<>();
        for (ResultDTO r : results) {
            InstrumentUnmatchedResult row = new InstrumentUnmatchedResult();
            row.setInstrumentId(instrumentId);
            row.setInstrumentName(instrumentName);
            row.setSampleNo(r.getSampleNo());
            row.setTestCode(r.getTestItemCode());
            row.setTestName(r.getTestItemName());
            row.setResultValue(r.getResultValue());
            row.setUnit(r.getUnit());
            row.setRefRange(r.getRefRange());
            row.setAbnormalFlag(r.getAbnormalFlag());
            row.setIsCritical(r.getIsCritical());
            row.setStatus(STATUS_PENDING);
            row.setTestTime(r.getTestTime() == null ? now : r.getTestTime());
            row.setCreateTime(now);
            row.setUpdateTime(now);
            rows.add(row);
        }
        for (InstrumentUnmatchedResult row : rows) {
            unmatchedResultMapper.insert(row);
        }
        log.info("无主标本落库 {} 条, instrumentId={}", rows.size(), instrumentId);
    }

    @Override
    public Page<InstrumentUnmatchedResult> page(long current, long size,
                                                Integer status, Long instrumentId) {
        LambdaQueryWrapper<InstrumentUnmatchedResult> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(InstrumentUnmatchedResult::getStatus, status);
        }
        if (instrumentId != null) {
            w.eq(InstrumentUnmatchedResult::getInstrumentId, instrumentId);
        }
        w.orderByDesc(InstrumentUnmatchedResult::getCreateTime);
        Page<InstrumentUnmatchedResult> page = unmatchedResultMapper.selectPage(
                new Page<>(current, size), w);
        // 回填仪器名称（兜底，兼容历史空快照）
        backfillInstrumentNames(page.getRecords());
        return page;
    }

    @Override
    public InstrumentUnmatchedResult updateResult(Long id, InstrumentUnmatchedResult dto) {
        InstrumentUnmatchedResult row = getRequired(id);
        if (dto.getSampleNo() != null) {
            row.setSampleNo(dto.getSampleNo());
        }
        if (dto.getTestCode() != null) {
            row.setTestCode(dto.getTestCode());
        }
        if (dto.getResultValue() != null) {
            row.setResultValue(dto.getResultValue());
        }
        if (dto.getUnit() != null) {
            row.setUnit(dto.getUnit());
        }
        if (dto.getRefRange() != null) {
            row.setRefRange(dto.getRefRange());
        }
        if (dto.getAbnormalFlag() != null) {
            row.setAbnormalFlag(dto.getAbnormalFlag());
        }
        if (dto.getRemark() != null) {
            row.setRemark(dto.getRemark());
        }
        row.setUpdateTime(LocalDateTime.now());
        unmatchedResultMapper.updateById(row);
        return row;
    }

    @Override
    public Long matchToSample(Long id, String sampleBarcode) {
        InstrumentUnmatchedResult row = getRequired(id);
        if (row.getStatus() != null && row.getStatus() == STATUS_MATCHED) {
            throw new BusinessException("该无主标本结果已匹配，不可重复匹配");
        }
        if (sampleBarcode == null || sampleBarcode.isBlank()) {
            throw new BusinessException("请提供要匹配的标本条码/样本号");
        }
        Sample sample = sampleMapper.selectOne(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSampleBarcode, sampleBarcode.trim())
                .or()
                .eq(Sample::getSampleNo, sampleBarcode.trim())
                .last("LIMIT 1"));
        if (sample == null) {
            throw new BusinessException("未找到与 [" + sampleBarcode + "] 对应的业务标本，请核对条码");
        }

        // 将当前无主标本结果拼装为正式结果并落库（复用报告模块 ResultPersistencePort）
        ResultDTO dto = ResultDTO.builder()
                .sampleId(sample.getId())
                .sampleNo(sampleBarcode.trim())
                .testItemCode(row.getTestCode())
                .testItemName(row.getTestName())
                .resultValue(row.getResultValue())
                .unit(row.getUnit())
                .refRange(row.getRefRange())
                .abnormalFlag(row.getAbnormalFlag())
                .isCritical(row.getIsCritical() == null ? 0 : row.getIsCritical())
                .instrumentId(row.getInstrumentId())
                .testTime(row.getTestTime() == null ? LocalDateTime.now() : row.getTestTime())
                .build();

        ResultPersistencePort persistencePort = resultPersistencePortProvider.getIfAvailable();
        if (persistencePort == null) {
            throw new BusinessException("结果落库端口不可用，无法完成匹配");
        }
        List<Long> ids = persistencePort.persistResults(List.of(dto));
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("匹配结果落库失败，可能因指标代码未配置");
        }

        row.setStatus(STATUS_MATCHED);
        row.setMatchedSampleId(sample.getId());
        row.setRemark(mergeRemark(row.getRemark(), "已手动匹配到标本 " + sampleBarcode.trim()));
        row.setUpdateTime(LocalDateTime.now());
        unmatchedResultMapper.updateById(row);
        log.info("无主标本结果 [{}] 匹配到标本 [{}], sampleId={}, resultIds={}",
                id, sampleBarcode, sample.getId(), ids);
        return sample.getId();
    }

    @Override
    public void discard(Long id) {
        InstrumentUnmatchedResult row = getRequired(id);
        if (row.getStatus() != null && row.getStatus() == STATUS_DISCARDED) {
            return;
        }
        row.setStatus(STATUS_DISCARDED);
        row.setUpdateTime(LocalDateTime.now());
        unmatchedResultMapper.updateById(row);
    }

    @Override
    public int matchBatchToSample(List<Long> ids, String sampleBarcode) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        if (sampleBarcode == null || sampleBarcode.isBlank()) {
            throw new BusinessException("请提供要匹配的标本条码/样本号");
        }
        // 先校验业务标本是否存在（只需查一次，避免每条记录重复查库）
        Sample sample = sampleMapper.selectOne(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSampleBarcode, sampleBarcode.trim())
                .or()
                .eq(Sample::getSampleNo, sampleBarcode.trim())
                .last("LIMIT 1"));
        if (sample == null) {
            throw new BusinessException("未找到与 [" + sampleBarcode + "] 对应的业务标本，请核对条码");
        }

        ResultPersistencePort persistencePort = resultPersistencePortProvider.getIfAvailable();
        if (persistencePort == null) {
            throw new BusinessException("结果落库端口不可用，无法完成匹配");
        }

        String trimmedBarcode = sampleBarcode.trim();
        int okCount = 0;
        LocalDateTime now = LocalDateTime.now();
        List<ResultDTO> batchForPersist = new ArrayList<>();
        List<InstrumentUnmatchedResult> batchForUpdate = new ArrayList<>();

        for (Long id : ids) {
            InstrumentUnmatchedResult row = unmatchedResultMapper.selectById(id);
            if (row == null) continue;
            if (row.getStatus() != null && row.getStatus() == STATUS_MATCHED) {
                continue; // 已匹配的跳过，不抛异常以免打断批量
            }
            ResultDTO dto = ResultDTO.builder()
                    .sampleId(sample.getId())
                    .sampleNo(trimmedBarcode)
                    .testItemCode(row.getTestCode())
                    .testItemName(row.getTestName())
                    .resultValue(row.getResultValue())
                    .unit(row.getUnit())
                    .refRange(row.getRefRange())
                    .abnormalFlag(row.getAbnormalFlag())
                    .isCritical(row.getIsCritical() == null ? 0 : row.getIsCritical())
                    .instrumentId(row.getInstrumentId())
                    .testTime(row.getTestTime() == null ? now : row.getTestTime())
                    .build();
            batchForPersist.add(dto);

            row.setStatus(STATUS_MATCHED);
            row.setMatchedSampleId(sample.getId());
            row.setRemark(mergeRemark(row.getRemark(), "已手动匹配到标本 " + trimmedBarcode));
            row.setUpdateTime(now);
            batchForUpdate.add(row);
        }
        // 一次性落库正式结果
        if (!batchForPersist.isEmpty()) {
            List<Long> persisted = persistencePort.persistResults(batchForPersist);
            if (persisted == null || persisted.isEmpty()) {
                throw new BusinessException("匹配结果落库失败，可能因指标代码未配置");
            }
            okCount = persisted.size();
        }
        // 批量更新无主标本状态为已匹配
        for (InstrumentUnmatchedResult row : batchForUpdate) {
            unmatchedResultMapper.updateById(row);
        }
        log.info("批量无主标本认领: ids={}, sampleBarcode={}, 成功落库{}条, sampleId={}",
                ids, trimmedBarcode, okCount, sample.getId());
        return okCount;
    }

    @Override
    public void discardBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            if (id == null) continue;
            InstrumentUnmatchedResult row = unmatchedResultMapper.selectById(id);
            if (row == null) continue;
            if (row.getStatus() != null && row.getStatus() == STATUS_DISCARDED) continue;
            row.setStatus(STATUS_DISCARDED);
            row.setUpdateTime(now);
            unmatchedResultMapper.updateById(row);
        }
    }

    // ====================================================================
    // 无主标本转质控
    // ====================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchConvertToQc(UnmatchedBatchToQcDTO dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BusinessException("请选择要转质控的无主标本记录");
        }
        if (dto.getQcLotId() == null) {
            throw new BusinessException("请选择质控品批号");
        }
        // 校验质控品批号存在 (用 Map 代替 QCLot 实体，避免 qc 模块依赖)
        Map<String, Object> lot = queryQcLot(dto.getQcLotId());
        if (lot == null) {
            throw new BusinessException("质控品批号不存在: " + dto.getQcLotId());
        }
        Long lotTestItemId = toLong(lot.get("test_item_id"));
        Long lotInstrumentId = toLong(lot.get("instrument_id"));
        BigDecimal lotTargetValue = toBigDecimal(lot.get("target_value"));
        BigDecimal lotSd = toBigDecimal(lot.get("sd"));
        Long lotId = toLong(lot.get("id"));
        if (lotTestItemId == null) {
            throw new BusinessException("该质控品批号未绑定检验指标 (lis_qc_lot.test_item_id 为空)");
        }
        if (lotInstrumentId == null) {
            throw new BusinessException("该质控品批号未绑定仪器 (lis_qc_lot.instrument_id 为空)");
        }
        if (lotTargetValue == null || lotSd == null) {
            throw new BusinessException("该质控品批号未设置靶值/SD, 无法进行质控计算");
        }

        // 质控服务: 通过反射查找 QCRecordService.submitRecord(QCRecordDTO)
        Object qcService = qcRecordServiceProvider.getIfAvailable();
        if (qcService == null) {
            // 兜底：按 bean 名称再尝试从 Spring 上下文查找
            qcService = findQcRecordServiceByName();
        }
        if (qcService == null) {
            throw new BusinessException("质控记录服务暂不可用, 请稍后重试");
        }
        Method submitMethod = ensureSubmitRecordMethod(qcService);

        // 反向解析: 质控批号绑定的 testItemId 在 lis_test_item.item_code (用于判断无主 testCode 是否匹配)
        String expectedItemCode = queryItemCode(lotTestItemId);

        int successCount = 0;
        int failCount = 0;
        int generatedQcCount = 0;
        List<Map<String, Object>> failDetails = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Long id : dto.getIds()) {
            if (id == null) continue;
            InstrumentUnmatchedResult row = unmatchedResultMapper.selectById(id);
            if (row == null) {
                failCount++;
                failDetails.add(failDetail(id, "记录不存在"));
                continue;
            }
            if (row.getStatus() != null && row.getStatus() != STATUS_PENDING) {
                failCount++;
                failDetails.add(failDetail(id, "记录已处理 (状态=" + row.getStatus() + ")"));
                continue;
            }
            // 1) 仪器一致性校验: 质控批号绑定的 instrumentId 必须和无主记录一致
            if (!lotInstrumentId.equals(row.getInstrumentId())) {
                failCount++;
                failDetails.add(failDetail(id, "质控品批号所属仪器与无主记录不一致 (lotInstrumentId="
                        + lotInstrumentId + ", rowInstrumentId=" + row.getInstrumentId() + ")"));
                continue;
            }
            // 2) 指标代码一致性校验: 无主 testCode 必须能映射到批号的 testItemId
            boolean itemMatched = false;
            if (row.getTestCode() != null && expectedItemCode != null
                    && row.getTestCode().trim().equalsIgnoreCase(expectedItemCode.trim())) {
                itemMatched = true;
            }
            // 兜底: 再按 testItemId 级仪器通道 (lis_instrument_channel) 反向查一次
            if (!itemMatched && row.getTestCode() != null) {
                Long resolvedItemId = resolveTestItemIdByChannel(row.getInstrumentId(), row.getTestCode());
                if (resolvedItemId != null && resolvedItemId.equals(lotTestItemId)) {
                    itemMatched = true;
                }
            }
            if (!itemMatched) {
                failCount++;
                failDetails.add(failDetail(id, "检验指标不匹配: 无主testCode=" + row.getTestCode()
                        + ", 质控批号指标(代码)=" + expectedItemCode));
                continue;
            }
            // 3) 结果值必须是数值
            BigDecimal measure = parseBigDecimalSafe(row.getResultValue());
            if (measure == null) {
                failCount++;
                failDetails.add(failDetail(id, "结果值非数值: " + row.getResultValue()));
                continue;
            }

            // 4) 组装质控记录请求并落库（反射调用 submitRecord）
            try {
                QCRecordDTO recordDTO = QCRecordDTO.builder()
                        .qcLotId(lotId)
                        .instrumentId(lotInstrumentId)
                        .measureValue(measure)
                        .measureTime(row.getTestTime() != null ? row.getTestTime() : now)
                        .operatorId(dto.getOperatorId())
                        .build();
                Object qcResult = submitMethod.invoke(qcService, recordDTO);
                String level = resolveQcLevel(qcResult);
                generatedQcCount++;
                successCount++;

                // 5) 回写无主记录状态为 3=已转质控, 并溯源 qcLotId / qcRecordId
                Map<String, Object> latest = findLatestQcRecord(lotId, now.minusMinutes(5));
                if (latest != null) {
                    Long qcRecordId = toLong(latest.get("id"));
                    if (qcRecordId != null) {
                        row.setMatchedQcRecordId(qcRecordId);
                    }
                }
                row.setMatchedQcLotId(lotId);
                row.setStatus(STATUS_TO_QC);
                row.setRemark(mergeRemark(row.getRemark(),
                        "已转质控 (qcLotId=" + lotId + ", 判定=" + level + ")"));
                row.setUpdateTime(now);
                unmatchedResultMapper.updateById(row);
            } catch (Exception ex) {
                log.warn("无主标本转质控失败, id={}", id, ex);
                failCount++;
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                failDetails.add(failDetail(id, "质控落库异常: " + cause.getMessage()));
            }
        }

        log.info("无主标本批量转质控: ids={}, qcLotId={}, success={}, fail={}, 生成质控记录{}条",
                dto.getIds(), dto.getQcLotId(), successCount, failCount, generatedQcCount);

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("generatedQcCount", generatedQcCount);
        result.put("failDetails", failDetails);
        return result;
    }

    // ===== 私有辅助 =====

    private InstrumentUnmatchedResult getRequired(Long id) {
        if (id == null) {
            throw new BusinessException("无主标本 ID 不能为空");
        }
        InstrumentUnmatchedResult row = unmatchedResultMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("无主标本结果不存在: " + id);
        }
        return row;
    }

    private String resolveInstrumentName(Long instrumentId) {
        if (instrumentId == null) {
            return null;
        }
        Instrument ins = instrumentMapper.selectById(instrumentId);
        return ins == null ? null : ins.getInstrumentName();
    }

    private void backfillInstrumentNames(List<InstrumentUnmatchedResult> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> need = new HashSet<>();
        for (InstrumentUnmatchedResult r : records) {
            if ((r.getInstrumentName() == null || r.getInstrumentName().isEmpty())
                    && r.getInstrumentId() != null) {
                need.add(r.getInstrumentId());
            }
        }
        if (need.isEmpty()) {
            return;
        }
        Map<Long, Instrument> map = instrumentMapper.selectBatchIds(need).stream()
                .collect(Collectors.toMap(Instrument::getId, Function.identity(), (a, b) -> a));
        for (InstrumentUnmatchedResult r : records) {
            if ((r.getInstrumentName() == null || r.getInstrumentName().isEmpty())
                    && r.getInstrumentId() != null) {
                Instrument ins = map.get(r.getInstrumentId());
                if (ins != null) {
                    r.setInstrumentName(ins.getInstrumentName());
                }
            }
        }
    }

    private static String mergeRemark(String before, String add) {
        if (before == null || before.isBlank()) {
            return add;
        }
        return before + "；" + add;
    }

    // ===== 转质控辅助方法 =====

    private static Map<String, Object> failDetail(Long id, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("reason", reason);
        return m;
    }

    private static BigDecimal parseBigDecimalSafe(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            // 尝试移除可能的单位/尾修饰字符(保留数字和小数点/-)
            StringBuilder sb = new StringBuilder();
            for (char c : v.toCharArray()) {
                if ((c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+') sb.append(c);
            }
            String cleaned = sb.toString();
            if (cleaned.isEmpty()) return null;
            try {
                return new BigDecimal(cleaned);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    /** 查询质控品批号 (lis_qc_lot) — 返回 Map，避免依赖 QCLot 实体 */
    private Map<String, Object> queryQcLot(Long lotId) {
        if (lotId == null) return null;
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT id, lot_no, test_item_id, instrument_id, target_value, sd " +
                            "FROM lis_qc_lot WHERE id = ? LIMIT 1",
                    lotId);
            if (list == null || list.isEmpty()) return null;
            return list.get(0);
        } catch (Exception e) {
            log.warn("查询质控品批号失败, lotId={}", lotId, e);
            return null;
        }
    }

    /** 通过 testItemId 查询 lis_test_item.item_code */
    private String queryItemCode(Long testItemId) {
        if (testItemId == null) return null;
        try {
            List<String> codes = jdbcTemplate.queryForList(
                    "SELECT item_code FROM lis_test_item WHERE id = ? AND status = 1 LIMIT 1",
                    String.class, testItemId);
            return (codes == null || codes.isEmpty()) ? null : codes.get(0);
        } catch (Exception e) {
            log.warn("查询检验指标代码失败, testItemId={}", testItemId, e);
            return null;
        }
    }

    /** 通过仪器通道表 lis_instrument_channel 反向解析: instrument_id + channel_code/test_code → test_item_id */
    private Long resolveTestItemIdByChannel(Long instrumentId, String testCode) {
        if (instrumentId == null || testCode == null) return null;
        try {
            String tc = testCode.trim();
            List<Long> list = jdbcTemplate.queryForList(
                    "SELECT test_item_id FROM lis_instrument_channel " +
                            "WHERE instrument_id = ? AND (channel_code = ? OR test_code = ?) " +
                            "AND deleted = 0 LIMIT 1",
                    Long.class, instrumentId, tc, tc);
            return (list == null || list.isEmpty()) ? null : list.get(0);
        } catch (Exception e) {
            log.warn("通过仪器通道解析指标失败, instrumentId={}, testCode={}", instrumentId, testCode);
            return null;
        }
    }

    /** 找到指定批号最近时间的质控记录 (用于溯源 matched_qc_record_id) — 返回 Map，避免依赖 QCRecord 实体 */
    private Map<String, Object> findLatestQcRecord(Long qcLotId, LocalDateTime since) {
        if (qcLotId == null) return null;
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT id, qc_lot_id, test_item_id, instrument_id, measure_value, measure_time, qc_result " +
                            "FROM lis_qc_record WHERE qc_lot_id = ? AND measure_time >= ? " +
                            "AND deleted = 0 ORDER BY id DESC LIMIT 1",
                    qcLotId, since == null ? LocalDateTime.now().minusMinutes(30) : since);
            if (list == null || list.isEmpty()) return null;
            return list.get(0);
        } catch (Exception e) {
            log.warn("查询最近质控记录失败, qcLotId={}", qcLotId, e);
            return null;
        }
    }

    // ===== 反射辅助：定位 QCRecordService.submitRecord(QCRecordDTO) =====

    /** 尝试按类型名在 ObjectProvider stream 中查找 QCRecordService */
    private Object findQcRecordServiceByName() {
        try {
            return qcRecordServiceProvider.stream()
                    .filter(b -> b.getClass().getName().startsWith("com.lis.qc")
                            || b.getClass().getSimpleName().contains("QCRecordService"))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 反射查找 submitRecord(QCRecordDTO) 方法并缓存 */
    private Method ensureSubmitRecordMethod(Object qcService) {
        if (submitRecordMethodCache != null) return submitRecordMethodCache;
        synchronized (this) {
            if (submitRecordMethodCache != null) return submitRecordMethodCache;
            Class<?> clz = qcService.getClass();
            // 先精确匹配参数类型
            for (Method m : clz.getMethods()) {
                if ("submitRecord".equals(m.getName()) && m.getParameterCount() == 1) {
                    Class<?> pt = m.getParameterTypes()[0];
                    if (pt.getName().equals("com.lis.api.dto.QCRecordDTO")
                            || pt.getSimpleName().equals("QCRecordDTO")) {
                        m.setAccessible(true);
                        submitRecordMethodCache = m;
                        return m;
                    }
                }
            }
            // 兜底：任意单参数 submitRecord
            for (Method m : clz.getMethods()) {
                if ("submitRecord".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    submitRecordMethodCache = m;
                    return m;
                }
            }
            throw new BusinessException("未找到质控服务 submitRecord 方法: " + clz.getName());
        }
    }

    /** 从 QCResult 返回值提取 level 字符串 (支持枚举 getLevel / getCode / toString / name) */
    private String resolveQcLevel(Object qcResult) {
        if (qcResult == null) return "(未知)";
        try {
            Method m = findMethod(qcResult.getClass(), "getLevel");
            if (m != null) {
                Object v = m.invoke(qcResult);
                if (v != null) return v.toString();
            }
            m = findMethod(qcResult.getClass(), "name");
            if (m != null && qcResult.getClass().isEnum()) {
                Object v = m.invoke(qcResult);
                if (v != null) return v.toString();
            }
        } catch (Exception ignored) {
        }
        return qcResult.toString();
    }

    private static Method findMethod(Class<?> clz, String name) {
        for (Method m : clz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return new BigDecimal(o.toString());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return null; }
    }
}
