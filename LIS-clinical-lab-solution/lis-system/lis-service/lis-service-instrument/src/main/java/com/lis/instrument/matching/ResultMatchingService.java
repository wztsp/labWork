package com.lis.instrument.matching;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.api.dto.QCRecordDTO;
import com.lis.api.dto.ResultDTO;
import com.lis.api.service.ResultPersistencePort;
import com.lis.instrument.entity.Sample;
import com.lis.instrument.mapper.SampleMatchMapper;
import com.lis.instrument.service.UnmatchedResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 结果匹配服务
 * <p>
 * 根据条码/样本号查询 lis_sample 表匹配标本，保存结果 (通过 MQ 通知报告服务落库)。
 * 未匹配的结果不丢弃, 单独通过 "result.unmatched" 路由键通知, 供人工干预。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultMatchingService {

    private final RabbitTemplate rabbitTemplate;
    private final SampleMatchMapper sampleMapper;
    private final UnmatchedResultService unmatchedResultService;
    /** 进程内直连落库端口 (报告模块实现, 独立部署时可能为空) */
    private final ObjectProvider<ResultPersistencePort> resultPersistencePortProvider;
    /** 质控记录服务 (monolith 模式下可用，ObjectProvider<Object> + 反射避免直接依赖 qc 模块) */
    private final ObjectProvider<Object> qcRecordServiceProvider;
    /** 质控批号查询 / testCode 反向解析 */
    private final JdbcTemplate jdbcTemplate;

    /** 质控样本号命中词 (样本号去除分隔符后前缀命中, 大小写不敏感) */
    private static final List<String> QC_PREFIXES = Arrays.asList(
            "QC", "QCK", "QCL", "QCT", "LJ", "L1", "L2", "L3",
            "N1", "N2", "N3", "Q1", "Q2", "Q3", "LEV", "LEVEL", "CON", "CTRL");
    /** 质控样本号匹配正则 (数字可选后缀, 如 QC1, QC-01, QC001, L-J-1) */
    private static final Pattern QC_SAMPLE_NO_PATTERN =
            Pattern.compile("^(?i)(QC|QCK|QCL|QCT|LJ|L1|L2|L3|N1|N2|N3|Q1|Q2|Q3|LEV|LEVEL|CON|CTRL)[\\-_0-9A-Z]*$");

    @Value("${mq.exchange:lis.exchange}")
    private String exchange;
    @Value("${mq.result.routing-key:instrument.result.received}")
    private String resultRoutingKey;
    @Value("${mq.result.unmatched-routing-key:result.unmatched}")
    private String unmatchedRoutingKey;
    @Value("${mq.qc.data-routing-key:qc.data}")
    private String qcRoutingKey;

    // 反射缓存 submitRecord(QCRecordDTO) 方法
    private volatile Method cachedSubmitRecordMethod;

    /**
     * 匹配并保存结果
     * @param instrumentId  仪器ID
     * @param protocolType  协议类型
     * @param parsedResults 解析后的结果列表 (含 sampleNo/testCode/value/unit/refRange/abnormalFlag)
     */
    public void matchAndSave(Long instrumentId, String protocolType,
                             List<Map<String, String>> parsedResults) {
        if (parsedResults == null || parsedResults.isEmpty()) {
            log.warn("仪器 [{}] 无可保存的结果", instrumentId);
            return;
        }
        List<ResultDTO> matched = new ArrayList<>();
        List<ResultDTO> unmatched = new ArrayList<>();
        List<ResultDTO> qcBatch = new ArrayList<>();

        for (Map<String, String> r : parsedResults) {
            String sampleNo = r.get("sampleNo");
            if (sampleNo == null || sampleNo.isEmpty()) {
                sampleNo = r.get("seq");
            }
            // ===== 质控自动分流：样本号前缀为 QC/L1/N1/LEVEL 等质控标识时，走质控入库流程 =====
            if (sampleNo != null && isQcSampleNo(sampleNo)) {
                qcBatch.add(buildResultDTO(instrumentId, sampleNo, r));
                continue;
            }

            // 查询 lis_sample 表: WHERE sample_barcode = ? OR sample_no = ?
            Long sampleId = resolveSampleId(sampleNo);

            String abnormalFlag = r.get("abnormalFlag");
            boolean critical = isCritical(abnormalFlag);

            ResultDTO dto = buildResultDTO(instrumentId, sampleNo, r);

            if (sampleId != null) {
                dto.setSampleId(sampleId);
                matched.add(dto);
            } else {
                // 未找到标本: 不丢弃, 加入未匹配列表
                unmatched.add(dto);
                log.warn("仪器 [{}] 样本 [{}] 未匹配到标本, 保存为未匹配结果", instrumentId, sampleNo);
            }
        }

        // 质控结果分流：自动匹配质控品批号 → 直连落库 + 发 MQ
        if (!qcBatch.isEmpty()) {
            dispatchQcBatch(instrumentId, qcBatch);
        }

        // 已匹配结果 -> 报告服务落库（双写机制：优先进程内直连，MQ作为通知/分布式补偿）
        if (!matched.isEmpty()) {
            ResultPersistencePort persistencePort = resultPersistencePortProvider.getIfAvailable();
            boolean localSaved = false;

            // 1) 进程内直连优先（monolith 单进程部署下最可靠，不依赖 RabbitMQ）
            if (persistencePort != null) {
                try {
                    List<Long> ids = persistencePort.persistResults(matched);
                    log.info("仪器 [{}] 进程内直连落库完成, 保存 {} 条, resultIds={}",
                            instrumentId, matched.size(), ids);
                    localSaved = true;
                } catch (Exception ex) {
                    log.error("仪器 [{}] 进程内直连落库失败, 尝试 MQ 补偿", instrumentId, ex);
                }
            }

            // 2) 始终发送 MQ（用于外部订阅通知 / 分布式部署场景 / 重复幂等消费）
            try {
                rabbitTemplate.convertAndSend(exchange, resultRoutingKey, matched);
                log.info("仪器 [{}] 匹配结果 {} 条, 已发送 MQ 通知 (routingKey={}, localSaved={})",
                        instrumentId, matched.size(), resultRoutingKey, localSaved);
            } catch (Exception e) {
                if (!localSaved) {
                    log.error("仪器 [{}] MQ 发送失败且进程内直连也未成功, 结果可能丢失", instrumentId, e);
                } else {
                    log.warn("仪器 [{}] MQ 通知发送失败 (但进程内已落库, 不影响结果)", instrumentId, e);
                }
            }

            // 3) 两者都不可用时告警
            if (!localSaved && persistencePort == null) {
                log.warn("仪器 [{}] 未找到 ResultPersistencePort 且 MQ 未确认, 请检查服务配置", instrumentId);
            }
        }

        // 未匹配结果 -> 落库为「无主标本」供技师手工认领(本系统内), 并保留 MQ 事件通知
        if (!unmatched.isEmpty()) {
            try {
                unmatchedResultService.saveUnmatched(instrumentId, unmatched);
                log.warn("仪器 [{}] 未匹配结果 {} 条, 已落库为无主标本", instrumentId, unmatched.size());
            } catch (Exception e) {
                log.error("仪器 [{}] 无主标本落库失败", instrumentId, e);
            }
            try {
                rabbitTemplate.convertAndSend(exchange, unmatchedRoutingKey, unmatched);
                log.warn("仪器 [{}] 未匹配结果 {} 条, 已发送未匹配 MQ (routingKey={})",
                        instrumentId, unmatched.size(), unmatchedRoutingKey);
            } catch (Exception e) {
                log.error("仪器 [{}] 未匹配结果发送 MQ 失败", instrumentId, e);
            }
        }
    }

    // ====================================================================
    // 质控分流：仪器样本号为 QC/质控 标识时，自动识别并生成质控记录
    // ====================================================================

    /** 按 QC 前缀正则判断样本号是否为质控 (public 供其他包调用) */
    public static boolean isQcSampleNo(String sampleNo) {
        if (sampleNo == null || sampleNo.isEmpty()) return false;
        // 清理多余空格
        String s = sampleNo.trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty()) return false;
        // 1) 前缀匹配（先去掉 '-' '_' 再判断）
        String stripped = s.replace("-", "").replace("_", "");
        for (String prefix : QC_PREFIXES) {
            if (stripped.startsWith(prefix)) return true;
        }
        // 2) 正则兜底（如 QC-001、L-J-2 等分隔符场景）
        return QC_SAMPLE_NO_PATTERN.matcher(s).matches();
    }

    /**
     * 质控批量分发：按 instrumentId + testCode 反向查质控品批号，
     * 命中则直接反射调用 QCRecordService.submitRecord 落质控；未命中降级为无主标本（remark 标记"质控未匹配批号"）。
     */
    private void dispatchQcBatch(Long instrumentId, List<ResultDTO> qcBatch) {
        Object qcService = qcRecordServiceProvider.getIfAvailable();
        if (qcService == null) {
            qcService = findQcRecordServiceByName();
        }
        Method submitMethod = null;
        if (qcService != null) {
            submitMethod = lookupSubmitMethodSafe(qcService);
        }
        int qcOk = 0, qcFail = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ResultDTO r : qcBatch) {
            Long qcLotId = resolveQcLotId(instrumentId, r.getTestItemCode(), r.getSampleNo());
            if (qcLotId == null) {
                // 找不到对应的质控品批号 → 降级为无主标本 (remark 标记疑似质控)
                qcFail++;
                log.warn("仪器 [{}] 质控样本 [{}] 指标 [{}] 未匹配到质控品批号, 降级为无主标本",
                        instrumentId, r.getSampleNo(), r.getTestItemCode());
                saveAsQcUnmatched(instrumentId, r);
                continue;
            }
            BigDecimal value = parseBigDecimalSafe(r.getResultValue());
            if (value == null) {
                qcFail++;
                log.warn("仪器 [{}] 质控样本 [{}] 指标 [{}] 结果值 [{}] 非数值, 降级为无主标本",
                        instrumentId, r.getSampleNo(), r.getTestItemCode(), r.getResultValue());
                saveAsQcUnmatched(instrumentId, r);
                continue;
            }
            try {
                String level = "(MQ)";
                if (qcService != null && submitMethod != null) {
                    QCRecordDTO recordDTO = QCRecordDTO.builder()
                            .qcLotId(qcLotId)
                            .instrumentId(instrumentId)
                            .measureValue(value)
                            .measureTime(r.getTestTime() == null ? now : r.getTestTime())
                            .build();
                    Object qcResult = submitMethod.invoke(qcService, recordDTO);
                    level = resolveQcLevel(qcResult);
                } else {
                    // monolith 不满足时 → 发送 qc.data MQ 通知 (qc 模块监听器消费)
                    Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("lotId", qcLotId);
                    payload.put("instrumentId", instrumentId);
                    payload.put("value", value);
                    payload.put("testTime", r.getTestTime() == null ? now.toString() : r.getTestTime().toString());
                    rabbitTemplate.convertAndSend(exchange, qcRoutingKey, payload);
                }
                qcOk++;
                log.info("仪器 [{}] 质控样本 [{}] 指标 [{}] = {}, qcLotId={}, 判定={}",
                        instrumentId, r.getSampleNo(), r.getTestItemCode(),
                        value, qcLotId, level);
            } catch (Exception e) {
                qcFail++;
                log.warn("仪器 [{}] 质控样本 [{}] 指标 [{}] 落库失败, 降级为无主标本",
                        instrumentId, r.getSampleNo(), r.getTestItemCode(), e);
                saveAsQcUnmatched(instrumentId, r);
            }
        }
        log.info("仪器 [{}] 质控分流: 共 {} 条, 成功 {} 条, 未匹配批号 {} 条 (已降级无主)",
                instrumentId, qcBatch.size(), qcOk, qcFail);
    }

    /** 质控分流失败的结果仍然按无主标本保存，但备注标记以便用户识别 */
    private void saveAsQcUnmatched(Long instrumentId, ResultDTO r) {
        r.setRemark(UnmatchedResultService.class.getName().contains(".")
                ? "疑似质控标本(未匹配批号)" : "QC");
        unmatchedResultService.saveUnmatched(instrumentId, List.of(r));
    }

    /** 根据 instrumentId + testCode + 质控样本号(可选后缀区分水平) 查询 qcLotId */
    private Long resolveQcLotId(Long instrumentId, String testCode, String sampleNo) {
        if (instrumentId == null || testCode == null) return null;
        // 1) 正向：通过 lis_test_item item_code 找 testItemId，再按仪器 + testItemId 找 lot
        try {
            Long testItemId = resolveTestItemIdByCode(testCode);
            if (testItemId != null) {
                // 根据样本号后缀(1/2/3) 选择不同水平的质控品 (如 QC1=水平1, QC2=水平2)
                Integer levelHint = parseLevelHint(sampleNo);
                List<Long> lots = jdbcTemplate.queryForList(
                        "SELECT id FROM lis_qc_lot WHERE instrument_id = ? AND test_item_id = ? AND status = 1 AND deleted = 0 " +
                                "ORDER BY create_time DESC",
                        Long.class, instrumentId, testItemId);
                if (lots != null && !lots.isEmpty()) {
                    if (levelHint == null || lots.size() == 1) {
                        return lots.get(0);
                    }
                    // 样本号带水平：例如 QC1 → 取第1个, QC2 → 取第2个 (按 level 序号，或按 lot 名称匹配 LEV1/LEV2)
                    if (levelHint >= 1 && levelHint <= lots.size()) {
                        return lots.get(levelHint - 1);
                    }
                    return lots.get(0);
                }
            }
        } catch (Exception e) {
            log.warn("按 item_code 查询质控批号失败, instrumentId={}, testCode={}", instrumentId, testCode);
        }
        // 2) 反向：通过 lis_instrument_channel 按仪器 + channel_code/test_code 找 testItemId
        try {
            Long testItemId = resolveTestItemIdByChannel(instrumentId, testCode);
            if (testItemId != null) {
                List<Long> lots = jdbcTemplate.queryForList(
                        "SELECT id FROM lis_qc_lot WHERE instrument_id = ? AND test_item_id = ? AND status = 1 AND deleted = 0 " +
                                "ORDER BY create_time DESC LIMIT 1",
                        Long.class, instrumentId, testItemId);
                if (lots != null && !lots.isEmpty()) return lots.get(0);
            }
        } catch (Exception e) {
            log.warn("按仪器通道查询质控批号失败, instrumentId={}, testCode={}", instrumentId, testCode);
        }
        return null;
    }

    private Long resolveTestItemIdByCode(String itemCode) {
        if (itemCode == null) return null;
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM lis_test_item WHERE item_code = ? AND is_panel = 0 AND status = 1 LIMIT 1",
                    Long.class, itemCode.trim());
            return (ids == null || ids.isEmpty()) ? null : ids.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveTestItemIdByChannel(Long instrumentId, String testCode) {
        if (instrumentId == null || testCode == null) return null;
        try {
            String tc = testCode.trim();
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT test_item_id FROM lis_instrument_channel " +
                            "WHERE instrument_id = ? AND (channel_code = ? OR test_code = ?) AND deleted = 0 LIMIT 1",
                    Long.class, instrumentId, tc, tc);
            return (ids == null || ids.isEmpty()) ? null : ids.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从样本号提取质控水平: QC1 → 1, QC2 → 2, L-J-3 → 3, LEV2 → 2 */
    private static Integer parseLevelHint(String sampleNo) {
        if (sampleNo == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean digitStarted = false;
        for (int i = sampleNo.length() - 1; i >= 0; i--) {
            char c = sampleNo.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.insert(0, c);
                digitStarted = true;
            } else if (digitStarted) {
                break;
            }
        }
        if (sb.length() == 0) return null;
        try {
            int level = Integer.parseInt(sb.toString());
            return level >= 1 && level <= 9 ? level : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseBigDecimalSafe(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        try { return new BigDecimal(v); }
        catch (NumberFormatException ignore) { return null; }
    }

    private static ResultDTO buildResultDTO(Long instrumentId, String sampleNo, Map<String, String> r) {
        String abnormalFlag = r.get("abnormalFlag");
        boolean critical = isCriticalStatic(abnormalFlag);
        return ResultDTO.builder()
                .sampleNo(sampleNo)
                .testItemCode(r.get("testCode"))
                .resultValue(r.get("value"))
                .unit(r.get("unit"))
                .refRange(r.get("refRange"))
                .abnormalFlag(abnormalFlag)
                .isCritical(critical ? 1 : 0)
                .instrumentId(instrumentId)
                .testTime(LocalDateTime.now())
                .build();
    }

    /**
     * 根据条码/样本号查询 lis_sample 表解析 sampleId
     * 查询条件: sample_barcode = ? OR sample_no = ?
     */
    private Long resolveSampleId(String sampleNo) {
        if (sampleNo == null || sampleNo.isBlank()) {
            return null;
        }
        Sample sample = sampleMapper.selectOne(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSampleBarcode, sampleNo)
                .or()
                .eq(Sample::getSampleNo, sampleNo)
                .last("LIMIT 1"));
        return sample == null ? null : sample.getId();
    }

    /**
     * 危急值判定: 包含 *** 或 > 或 < 标记视为危急值 (静态版，供静态 buildResultDTO 使用)
     */
    private static boolean isCriticalStatic(String abnormalFlag) {
        if (abnormalFlag == null || abnormalFlag.isEmpty()) {
            return false;
        }
        String flag = abnormalFlag.trim().toUpperCase();
        return flag.contains("***") || flag.contains(">") || flag.contains("<");
    }

    /**
     * 危急值判定: 实例方法 (供 matchAndSave 非静态上下文调用，保持原语义)
     */
    private boolean isCritical(String abnormalFlag) {
        return isCriticalStatic(abnormalFlag);
    }

    // ===== 反射辅助：QCRecordService.submitRecord(QCRecordDTO) =====

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

    private Method lookupSubmitMethodSafe(Object qcService) {
        if (cachedSubmitRecordMethod != null) return cachedSubmitRecordMethod;
        synchronized (this) {
            if (cachedSubmitRecordMethod != null) return cachedSubmitRecordMethod;
            Class<?> clz = qcService.getClass();
            for (Method m : clz.getMethods()) {
                if ("submitRecord".equals(m.getName()) && m.getParameterCount() == 1) {
                    Class<?> pt = m.getParameterTypes()[0];
                    if (pt.getName().equals("com.lis.api.dto.QCRecordDTO")
                            || pt.getSimpleName().equals("QCRecordDTO")) {
                        m.setAccessible(true);
                        cachedSubmitRecordMethod = m;
                        return m;
                    }
                }
            }
            for (Method m : clz.getMethods()) {
                if ("submitRecord".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    cachedSubmitRecordMethod = m;
                    return m;
                }
            }
            return null;
        }
    }

    private String resolveQcLevel(Object qcResult) {
        if (qcResult == null) return "(未知)";
        try {
            Method m = findGetter(qcResult.getClass(), "getLevel");
            if (m != null) {
                Object v = m.invoke(qcResult);
                if (v != null) return v.toString();
            }
            m = findGetter(qcResult.getClass(), "name");
            if (m != null && qcResult.getClass().isEnum()) {
                Object v = m.invoke(qcResult);
                if (v != null) return v.toString();
            }
        } catch (Exception ignored) {
        }
        return qcResult.toString();
    }

    private static Method findGetter(Class<?> clz, String name) {
        for (Method m : clz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}
