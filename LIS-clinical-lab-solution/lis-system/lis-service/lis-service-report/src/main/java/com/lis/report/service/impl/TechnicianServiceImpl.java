package com.lis.report.service.impl;

import com.lis.common.core.exception.BusinessException;
import com.lis.report.dto.BatchAuditDTO;
import com.lis.report.dto.BatchModifyInspectorDTO;
import com.lis.report.dto.BatchModifyResultDTO;
import com.lis.report.dto.BatchOpResult;
import com.lis.report.entity.Result;
import com.lis.report.entity.StationSignin;
import com.lis.report.mapper.ResultMapper;
import com.lis.report.mapper.TechnicianMapper;
import com.lis.report.service.ReportService;
import com.lis.report.service.TechnicianService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 技师工作站服务实现
 * 核心逻辑: 核收后检验结果为空, 结果值通过通讯程序回传
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechnicianServiceImpl implements TechnicianService {

    private final TechnicianMapper technicianMapper;
    private final ResultMapper resultMapper;
    private final ReportService reportService;

    @Override
    public List<Map<String, Object>> getSamples(String keyword, String visitType, String startDate, String endDate, int current, int size, Long instrumentId) {
        if (current <= 0) current = 1;
        if (size <= 0) size = 1000;            // 默认 1000 条/页 (单日全量标本足够)
        if (size > 50000) size = 50000;        // 软上限 5 万条 (超大规模科室也能覆盖), 避免误用导致前端卡顿
        int offset = (current - 1) * size;
        List<Map<String, Object>> rows = technicianMapper.selectReceivedSamples(
                keyword != null ? keyword.trim() : null,
                visitType,
                startDate,
                endDate,
                offset,
                size,
                instrumentId);

        // 为标本卡回填「检验项目」字符串（与右侧详情页「共 N 个检验项目」完全一致）。
        // 策略：
        //   A. 已回传结果：从 lis_result 提取 DISTINCT COALESCE(panel_name, item_name)
        //   B. 预期指标：通过 orderItems → selectTestIndicators 展开全部项目 (即使暂无结果)
        //   将 A ∪ B 合并并去重，得到与右侧面板分组一致的项目名列表
        int enriched = 0;
        for (Map<String, Object> row : rows) {
            Object isUnmatchedRaw = row.get("is_unmatched");
            boolean isUnmatched = Boolean.TRUE.equals(isUnmatchedRaw)
                    || "true".equalsIgnoreCase(String.valueOf(isUnmatchedRaw));
            if (isUnmatched) continue;
            Long sampleId = toLong(row.get("id"));
            Long orderId = toLong(row.get("order_id"));
            if (sampleId == null) continue;
            try {
                java.util.Set<String> names = new java.util.TreeSet<>();
                int existingCount = 0;
                int indicatorCount = 0;

                // 1) 已回传结果对应的 panel/项目名
                List<Map<String, Object>> existing = technicianMapper.selectExistingResults(sampleId);
                if (existing != null && !existing.isEmpty()) {
                    existingCount = existing.size();
                    for (Map<String, Object> r : existing) {
                        Object panelObj = r.get("panel_name");
                        Object itemObj = r.get("item_name");
                        String display = panelObj != null ? String.valueOf(panelObj).trim()
                                        : (itemObj != null ? String.valueOf(itemObj).trim() : null);
                        if (display != null && !display.isEmpty()) names.add(display);
                    }
                }

                // 2) 预期指标（申请项目字典树展开）对应的 panel/项目名（即使无结果也要显示）
                List<Map<String, Object>> orderItems = technicianMapper.selectOrderItemsBySampleId(sampleId);
                if (orderItems == null || orderItems.isEmpty()) {
                    if (orderId != null) orderItems = technicianMapper.selectOrderItemsByOrderId(orderId);
                }
                Integer gender = parseGender(row.get("patient_gender"));
                Integer age = parseAge(row.get("patient_age"));

                if (orderItems != null && !orderItems.isEmpty()) {
                    List<Long> panelIds = orderItems.stream()
                            .map(oi -> toLong(oi.get("test_item_id")))
                            .filter(Objects::nonNull).distinct().collect(Collectors.toList());
                    if (!panelIds.isEmpty()) {
                        List<Map<String, Object>> indicators = technicianMapper.selectTestIndicators(panelIds, gender, age);
                        if (indicators != null && !indicators.isEmpty()) {
                            indicatorCount = indicators.size();
                            for (Map<String, Object> ind : indicators) {
                                Object panelObj = ind.get("panel_name");
                                Object itemObj = ind.get("item_name");
                                String display = panelObj != null ? String.valueOf(panelObj).trim()
                                                : (itemObj != null ? String.valueOf(itemObj).trim() : null);
                                if (display != null && !display.isEmpty()) names.add(display);
                            }
                        }
                    }
                    // 注: 不再额外追加申请项目名本身。
                    // 正常情况下所有申请项目展开后得到的指标都会携带正确的 panel_name 聚合显示；
                    // 若某申请项目无匹配指标（例如性别/年龄过滤后为空），右侧详情页同样不会展示，
                    // 此处不额外补齐以保证卡片与详情页的分组数量一致。
                }

                String joined = names.isEmpty() ? null : String.join("、", names);
                log.info("[CardTestItems] sampleId={} sampleBarcode={} raw={} joined={} " +
                                "existingCount={} indicatorCount={} panelNames={}",
                        sampleId, row.get("sample_barcode"), row.get("test_items"), joined,
                        existingCount, indicatorCount, names.size());
                if (joined != null && !joined.isEmpty()) {
                    row.put("test_items", joined);
                    enriched++;
                }
            } catch (Exception e) {
                log.warn("回填标本检验项目名称失败: sampleId={}, error={}", sampleId, e.getMessage(), e);
            }
        }
        log.info("[CardTestItems] finished: rows={} enriched={}", rows.size(), enriched);

        return rows.stream().map(this::convertSampleRow).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getDetail(Long sampleId) {
        log.info("查询标本详情: sampleId={}", sampleId);
        
        Map<String, Object> sample = technicianMapper.selectSampleDetail(sampleId);
        if (sample == null) {
            throw new BusinessException("标本不存在或已删除: " + sampleId);
        }
        log.info("标本查询成功: sampleId={}, orderId={}", sampleId, sample.get("order_id"));

        // 1. 构建患者信息
        Map<String, Object> patient = buildPatientInfo(sample);
        log.info("患者信息构建完成: sampleId={}", sampleId);

        // 1.5 读取报告签名信息(检验人/报告医生), 用于报告打印时手写签名图片匹配
        try {
            Map<String, Object> reportSign = technicianMapper.selectReportSignInfo(sampleId);
            if (reportSign != null) {
                String checker = toStringOrNull(reportSign.get("checker"));
                String reportDoctor = toStringOrNull(reportSign.get("report_doctor"));
                if (checker != null) patient.put("inspector", checker);
                if (reportDoctor != null) {
                    patient.put("reporter", reportDoctor);
                    patient.put("publisher", reportDoctor);
                }
            }
        } catch (Exception e) {
            log.warn("读取报告签名信息失败: sampleId={}, error={}", sampleId, e.getMessage());
        }

        // 2. 先构建申请单上所有预期检验项目的空结果列表 (保证14项指标都显示)
        List<Map<String, Object>> emptyResults;
        try {
            emptyResults = buildEmptyResults(sampleId, sample);
            log.info("构建预期结果指标: sampleId={}, count={}", sampleId, emptyResults.size());
        } catch (Exception e) {
            log.error("构建预期结果指标失败: sampleId={}, error={}", sampleId, e.getMessage(), e);
            emptyResults = new ArrayList<>();
        }

        // 3. 查询已存在的检验结果 (通讯程序回传的)
        List<Map<String, Object>> existingResults = technicianMapper.selectExistingResults(sampleId);
        log.info("查询已有结果: sampleId={}, count={}", sampleId, existingResults.size());

        // 4. 合并: 以空结果列表为底(保证所有预期项目都在), 已有结果覆盖对应项
        List<Map<String, Object>> results = new ArrayList<>(emptyResults);
        Set<Long> alreadyInBase = new HashSet<>();
        for (Map<String, Object> r : results) {
            Long id = toLong(r.get("testItemId"));
            if (id != null) alreadyInBase.add(id);
        }
        if (!existingResults.isEmpty()) {
            // 把已有结果按 testItemId 分组为 Map 便于快速查找
            Map<Long, Map<String, Object>> existingByItemId = new HashMap<>();
            for (Map<String, Object> row : existingResults) {
                Long itemId = toLong(row.get("test_item_id"));
                if (itemId != null) {
                    existingByItemId.put(itemId, convertResultRow(row));
                }
            }
            // 遍历每个空结果行，如果已有结果则做字段覆盖合并
            for (Map<String, Object> empty : results) {
                Long itemId = toLong(empty.get("testItemId"));
                if (itemId == null) continue;
                Map<String, Object> existing = existingByItemId.get(itemId);
                if (existing == null) continue;
                // 已存结果字段覆盖空行: 保留空行中的分组/参考范围信息，用已有结果的值字段做覆盖
                if (existing.get("id") != null) empty.put("id", existing.get("id"));
                if (existing.get("resultValue") != null) empty.put("resultValue", existing.get("resultValue"));
                if (existing.get("abnormalFlag") != null) empty.put("abnormalFlag", existing.get("abnormalFlag"));
                if (existing.get("isCritical") != null) empty.put("isCritical", existing.get("isCritical"));
                if (existing.get("instrumentId") != null) empty.put("instrumentId", existing.get("instrumentId"));
                // 若已有结果存了 unit/refRange，覆盖空行的默认值（优先显示仪器回传的原始单位/范围）
                if (existing.get("unit") != null) empty.put("unit", existing.get("unit"));
                if (existing.get("refRange") != null) empty.put("refRange", existing.get("refRange"));
                // 扩展字段回填
                if (existing.get("lastResultValue") != null) empty.put("lastResultValue", existing.get("lastResultValue"));
                if (existing.get("lastResultTime") != null) empty.put("lastResultTime", existing.get("lastResultTime"));
                if (existing.get("lastAbnormalFlag") != null) empty.put("lastAbnormalFlag", existing.get("lastAbnormalFlag"));
                if (existing.get("odValue") != null) empty.put("odValue", existing.get("odValue"));
                if (existing.get("cutoffValue") != null) empty.put("cutoffValue", existing.get("cutoffValue"));
                if (existing.get("scRatio") != null) empty.put("scRatio", existing.get("scRatio"));
                if (existing.get("autoAudit") != null) empty.put("autoAudit", existing.get("autoAudit"));
                if (existing.get("autoAuditMessage") != null) empty.put("autoAuditMessage", existing.get("autoAuditMessage"));
                if (existing.get("itemKnowledge") != null) empty.put("itemKnowledge", existing.get("itemKnowledge"));
                if (existing.get("imageUrl") != null) empty.put("imageUrl", existing.get("imageUrl"));
            }
            // ★ 关键修复: 空基准为空或已有结果不在预期列表时，追加已有结果（避免标本未正确关联项目时所有回传结果都不显示）
            for (Map.Entry<Long, Map<String, Object>> e : existingByItemId.entrySet()) {
                if (!alreadyInBase.contains(e.getKey())) {
                    results.add(e.getValue());
                }
            }
            log.info("结果合并完成: 预期 {} 项, 已回传 {} 项, 最终显示 {} 项",
                    emptyResults.size(), existingByItemId.size(), results.size());
        }

        // 3. 为每条结果填充"上次结果"等扩展字段 (批量查询, 避免 N+1)
        Long patientId = toLong(sample.get("patient_id"));
        LocalDateTime currentTime = sample.get("report_time") != null
                ? toLocalDateTime(sample.get("report_time"))
                : (sample.get("test_start_time") != null
                        ? toLocalDateTime(sample.get("test_start_time"))
                        : (sample.get("receive_time") != null ? toLocalDateTime(sample.get("receive_time")) : LocalDateTime.now()));
        fillLastResults(results, patientId, currentTime);
        // 按位置填充 "NO." 序号 (1-based)
        for (int i = 0; i < results.size(); i++) {
            results.get(i).put("itemNo", i + 1);
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("patient", patient);
        detail.put("results", results);
        // 标本级图像 (一个标本对应多个图像, 血常规/尿常规等形态图像) - 表缺失时容错, 不影响标本详情
        List<Map<String, Object>> images = new ArrayList<>();
        try {
            images = technicianMapper.selectSampleImages(sampleId);
        } catch (Exception e) {
            log.warn("查询标本图像失败(忽略): sampleId={}, error={}", sampleId, e.getMessage());
        }
        detail.put("images", images);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveResults(Long sampleId, List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) return;

        for (Map<String, Object> row : results) {
            Long testItemId = toLong(row.get("testItemId"));
            if (testItemId == null) continue;

            String resultValue = row.get("resultValue") != null ? String.valueOf(row.get("resultValue")) : null;

            // 查询是否已有结果记录
            List<Result> existing = resultMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Result>()
                            .eq(Result::getSampleId, sampleId)
                            .eq(Result::getTestItemId, testItemId));

            if (!existing.isEmpty()) {
                // 更新已有记录
                Result r = existing.get(0);
                r.setResultValue(resultValue);
                r.setUnit(row.get("unit") != null ? String.valueOf(row.get("unit")) : r.getUnit());
                r.setRefRange(row.get("refRange") != null ? String.valueOf(row.get("refRange")) : r.getRefRange());
                r.setAbnormalFlag(row.get("abnormalFlag") != null ? String.valueOf(row.get("abnormalFlag")) : null);
                r.setIsCritical(row.get("isCritical") != null && (Boolean) row.get("isCritical") ? 1 : 0);
                r.setOdValue(toBigDecimal(row.get("odValue")));
                r.setCutoffValue(toBigDecimal(row.get("cutoffValue")));
                r.setScRatio(toBigDecimal(row.get("scRatio")));
                if (row.get("autoAudit") != null) {
                    r.setAutoAudit(toInt(row.get("autoAudit")));
                }
                if (row.get("autoAuditMessage") != null) {
                    r.setAutoAuditMessage(String.valueOf(row.get("autoAuditMessage")));
                }
                if (row.get("itemKnowledge") != null) {
                    r.setItemKnowledge(String.valueOf(row.get("itemKnowledge")));
                }
                if (row.get("imageUrl") != null) {
                    r.setImageUrl(String.valueOf(row.get("imageUrl")));
                }
                r.setUpdateTime(LocalDateTime.now());
                resultMapper.updateById(r);
            } else {
                // 新增结果记录
                Result r = new Result();
                r.setSampleId(sampleId);
                r.setTestItemId(testItemId);
                r.setResultValue(resultValue);
                r.setUnit(row.get("unit") != null ? String.valueOf(row.get("unit")) : null);
                r.setRefRange(row.get("refRange") != null ? String.valueOf(row.get("refRange")) : null);
                r.setAbnormalFlag(row.get("abnormalFlag") != null ? String.valueOf(row.get("abnormalFlag")) : null);
                r.setIsCritical(row.get("isCritical") != null && (Boolean) row.get("isCritical") ? 1 : 0);
                r.setAuditStatus(Result.AUDIT_NONE);
                r.setOdValue(toBigDecimal(row.get("odValue")));
                r.setCutoffValue(toBigDecimal(row.get("cutoffValue")));
                r.setScRatio(toBigDecimal(row.get("scRatio")));
                r.setAutoAudit(row.get("autoAudit") != null ? toInt(row.get("autoAudit")) : 0);
                if (row.get("autoAuditMessage") != null) {
                    r.setAutoAuditMessage(String.valueOf(row.get("autoAuditMessage")));
                }
                if (row.get("itemKnowledge") != null) {
                    r.setItemKnowledge(String.valueOf(row.get("itemKnowledge")));
                }
                if (row.get("imageUrl") != null) {
                    r.setImageUrl(String.valueOf(row.get("imageUrl")));
                }
                r.setTestTime(LocalDateTime.now());
                r.setCreateTime(LocalDateTime.now());
                r.setUpdateTime(LocalDateTime.now());
                resultMapper.insert(r);
            }
        }
        log.info("保存检验结果: sampleId={}, 条数={}", sampleId, results.size());
    }

    @Override
    public Map<String, Object> getSummary(Long instrumentId, String startDate, String endDate) {
        Map<String, Object> stats = technicianMapper.selectSummaryStats(instrumentId, startDate, endDate);
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", toInt(stats.get("total")));
        summary.put("pending", toInt(stats.get("pending")));
        summary.put("urgent", toInt(stats.get("urgent")));
        summary.put("audited", toInt(stats.get("audited")));
        return summary;
    }

    // ==================== 私有方法 ====================

    /**
     * 构建空结果指标列表 (核收后, 通讯程序尚未回传结果)
     */
    private List<Map<String, Object>> buildEmptyResults(Long sampleId, Map<String, Object> sample) {
        // 1. 查询标本关联的检验项目 (is_panel=1) — 主路径: 通过 lis_sample_order_item 中间表
        List<Map<String, Object>> orderItems = technicianMapper.selectOrderItemsBySampleId(sampleId);
        log.info("查询检验项目: sampleId={}, 路径=sample_order_item, count={}", sampleId, orderItems.size());

        // 2. 若主路径未查到, 降级到通过 order_id 直接查 lis_order_item (标本拆分/未写入中间表时仍可解析项目)
        if (orderItems.isEmpty()) {
            Object orderIdObj = sample.get("order_id");
            Long orderId = orderIdObj != null ? toLong(orderIdObj) : null;
            if (orderId != null) {
                orderItems = technicianMapper.selectOrderItemsByOrderId(orderId);
                log.info("查询检验项目(降级): sampleId={}, orderId={}, 路径=order_item, count={}",
                        sampleId, orderId, orderItems.size());
            }
        }

        if (orderItems.isEmpty()) {
            log.warn("标本 {} 未关联检验项目(两个路径均未命中), 无法生成预期空结果指标", sampleId);
            return new ArrayList<>();
        }

        // 2. 收集检验项目ID
        List<Long> panelIds = orderItems.stream()
                .map(oi -> toLong(oi.get("test_item_id")))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        log.info("检验项目ID列表: sampleId={}, panelIds={}", sampleId, panelIds);
        
        if (panelIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 解析患者性别和年龄用于匹配参考范围
        Integer gender = parseGender(sample.get("patient_gender"));
        Integer age = parseAge(sample.get("patient_age"));
        log.info("患者性别年龄: sampleId={}, gender={}, age={}", sampleId, gender, age);

        // 4. 查询检验指标 (is_panel=0)
        List<Map<String, Object>> indicators;
        try {
            indicators = technicianMapper.selectTestIndicators(panelIds, gender, age);
            log.info("查询检验指标: sampleId={}, count={}", sampleId, indicators.size());
        } catch (Exception e) {
            log.error("查询检验指标失败: sampleId={}, error={}", sampleId, e.getMessage(), e);
            return new ArrayList<>();
        }

        // 5. 构建空结果列表
        return indicators.stream().map(ind -> {
            Map<String, Object> r = new HashMap<>();
            r.put("id", null);
            r.put("testItemId", ind.get("test_item_id"));
            r.put("testItemCode", ind.get("item_code"));
            r.put("testItemName", ind.get("item_name"));
            r.put("itemNameEn", ind.get("item_name_en"));
            r.put("resultValue", null); // 空结果值, 等待通讯程序回传
            r.put("unit", ind.get("unit"));
            r.put("refRange", buildRefRange(ind));
            r.put("abnormalFlag", null);
            r.put("isCritical", false);
            r.put("category", ind.get("category"));
            r.put("panelId", ind.get("parent_id"));
            r.put("panelName", ind.get("panel_name"));
            r.put("appliedToReport", true);
            // 扩展字段 (初始为空, 由通讯/自动审核/数据库读取回填)
            r.put("lastResultValue", null);
            r.put("lastResultTime", null);
            r.put("lastAbnormalFlag", null);
            r.put("odValue", null);
            r.put("cutoffValue", null);
            r.put("scRatio", null);
            r.put("autoAudit", 0);
            r.put("autoAuditMessage", null);
            r.put("itemKnowledge", null);
            r.put("imageUrl", null);
            return r;
        }).collect(Collectors.toList());
    }

    /**
     * 构建参考范围文本: "低-高" 或 文字参考范围
     */
    private String buildRefRange(Map<String, Object> ind) {
        String refText = ind.get("ref_text") != null ? String.valueOf(ind.get("ref_text")) : null;
        if (refText != null && !refText.isEmpty()) {
            return refText;
        }
        String refLow = ind.get("ref_low") != null ? String.valueOf(ind.get("ref_low")) : null;
        String refHigh = ind.get("ref_high") != null ? String.valueOf(ind.get("ref_high")) : null;
        if (refLow != null && refHigh != null) {
            return refLow + "-" + refHigh;
        }
        if (refLow != null) return ">" + refLow;
        if (refHigh != null) return "<" + refHigh;
        return null;
    }

    /**
     * 构建患者信息
     */
    private Map<String, Object> buildPatientInfo(Map<String, Object> sample) {
        Map<String, Object> p = new HashMap<>();
        p.put("patientId", sample.get("patient_id"));
        p.put("patientName", sample.get("patient_name") != null ? sample.get("patient_name") : sample.get("order_patient_name"));
        p.put("gender", sample.get("patient_gender") != null ? formatGender(sample.get("patient_gender")) : "");
        p.put("age", sample.get("patient_age") != null ? String.valueOf(sample.get("patient_age")) : "");
        p.put("visitNo", sample.get("visit_no"));
        p.put("visitType", sample.get("visit_type") != null ? formatVisitType(sample.get("visit_type")) : "");
        p.put("department", sample.get("apply_dept") != null ? sample.get("apply_dept") : sample.get("department"));
        p.put("doctor", sample.get("apply_doctor"));
        p.put("diagnosis", sample.get("clinical_diag"));
        p.put("idCard", sample.get("patient_id_card"));
        p.put("phone", sample.get("patient_phone"));
        p.put("address", sample.get("patient_address"));
        p.put("sampleType", sample.get("sample_type"));
        p.put("specimenNo", sample.get("sample_barcode"));
        p.put("sampleNo", sample.get("sample_no"));
        p.put("specimenSerialNo", sample.get("sample_no"));
        p.put("collectTime", sample.get("collect_time"));
        p.put("receiveTime", sample.get("receive_time"));
        p.put("testTime", sample.get("test_start_time"));
        p.put("reportTime", sample.get("report_time"));
        p.put("applyTime", sample.get("apply_time"));
        /* === 人员字段（按 SQL 新查询结果填充） === */
        // 采集人：优先 collector_name（通过 staff 表联查的姓名），否则 inspector 或空
        String collector = sample.get("collector_name") != null ? String.valueOf(sample.get("collector_name")) : "";
        p.put("collector", collector);
        p.put("collectorId", sample.get("collector_id"));
        // 签收人：SQL 已 COALESCE 处理好 receiver
        p.put("receiver", sample.get("receiver") != null ? sample.get("receiver") : "");
        p.put("receiverId", sample.get("receiver_id"));
        // 检验人：优先 specimen inspector 文本字段（冗余姓名），其次 inspector_user_id 联查
        String inspector = sample.get("inspector") != null && !String.valueOf(sample.get("inspector")).isEmpty()
                ? String.valueOf(sample.get("inspector"))
                : "";
        p.put("inspector", inspector);
        p.put("inspectorUserId", sample.get("inspector_user_id"));
        // 审核人：来自 lis_report 表的 auditor 关联 staff 姓名
        String auditor = sample.get("report_auditor_name") != null ? String.valueOf(sample.get("report_auditor_name")) : "";
        p.put("auditor", auditor);
        p.put("auditorId", sample.get("report_auditor_id"));
        p.put("auditTime", sample.get("report_audit_time"));
        // reporter 暂复用 auditor 显示（报告人≈审核人，LIS 常规语义）
        p.put("reporter", auditor);
        p.put("publisher", "");
        p.put("feeReceivable", 0);
        p.put("feeActual", 0);
        return p;
    }

    /**
     * 转换标本行数据为前端格式
     */
    private Map<String, Object> convertSampleRow(Map<String, Object> row) {
        Map<String, Object> s = new HashMap<>();
        s.put("id", row.get("id"));
        s.put("sampleBarcode", row.get("sample_barcode"));
        s.put("sampleNo", row.get("sample_no"));
        s.put("patientName", row.get("patient_name"));
        s.put("patientGender", formatGender(row.get("patient_gender")));
        s.put("patientAge", row.get("patient_age") != null ? String.valueOf(row.get("patient_age")) : "");
        s.put("visitNo", row.get("visit_no"));
        s.put("visitType", formatVisitType(row.get("visit_type")));
        s.put("testItems", row.get("test_items") != null ? String.valueOf(row.get("test_items")) : "");
        s.put("department", row.get("apply_dept") != null ? row.get("apply_dept") : row.get("department"));
        s.put("doctor", row.get("apply_doctor"));
        s.put("collectTime", row.get("collect_time"));
        s.put("receiveTime", row.get("receive_time"));
        s.put("testTime", row.get("test_start_time"));
        s.put("reportTime", row.get("report_time"));
        s.put("status", mapSampleStatus(row.get("status")));
        s.put("isUrgent", row.get("is_urgent"));
        s.put("abnormalCount", 0);
        s.put("criticalCount", 0);
        s.put("instrumentId", row.get("instrument_id"));

        // ===== 无主标本扩展字段（UNION ALL 子查询输出） =====
        Object isUnmatchedRaw = row.get("is_unmatched");
        boolean isUnmatched = Boolean.TRUE.equals(isUnmatchedRaw)
                || "true".equalsIgnoreCase(String.valueOf(isUnmatchedRaw));
        s.put("isUnmatched", isUnmatched);
        if (isUnmatched) {
            s.put("unmatchedSampleNo", row.get("unmatched_sample_no"));
            s.put("unmatchedInstrumentId", row.get("unmatched_instrument_id"));
            s.put("unmatchedInstrumentName", row.get("unmatched_instrument_name"));
            s.put("unmatchedResultCount", row.get("unmatched_result_count"));
            s.put("unmatchedResultSummary", row.get("unmatched_result_summary"));
            // 解析 CSV 形式的 ID 列表为 List<Long>，前端直接用于批量认领/作废
            String idsCsv = row.get("unmatched_result_ids") != null ? String.valueOf(row.get("unmatched_result_ids")) : "";
            List<Long> idList = new ArrayList<>();
            if (!idsCsv.isEmpty()) {
                for (String p : idsCsv.split("[,，;；\\s]+")) {
                    if (p == null || p.isEmpty()) continue;
                    try { idList.add(Long.parseLong(p.trim())); } catch (Exception ignore) {}
                }
            }
            s.put("unmatchedResultIds", idList);
        } else {
            s.put("unmatchedSampleNo", null);
            s.put("unmatchedInstrumentId", null);
            s.put("unmatchedInstrumentName", null);
            s.put("unmatchedResultCount", null);
            s.put("unmatchedResultSummary", null);
            s.put("unmatchedResultIds", null);
        }
        // ===== 无主标本扩展字段 结束 =====
        return s;
    }

    /**
     * 转换已有结果行数据为前端格式
     */
    private Map<String, Object> convertResultRow(Map<String, Object> row) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", row.get("id"));
        r.put("testItemId", row.get("test_item_id"));
        r.put("testItemCode", row.get("item_code"));
        r.put("testItemName", row.get("item_name"));
        r.put("itemNameEn", row.get("item_name_en"));
        r.put("resultValue", row.get("result_value"));
        r.put("unit", row.get("unit"));
        r.put("refRange", row.get("ref_range"));
        r.put("abnormalFlag", row.get("abnormal_flag"));
        r.put("isCritical", row.get("is_critical") != null && ((Number) row.get("is_critical")).intValue() == 1);
        r.put("instrumentId", row.get("instrument_id"));
        r.put("category", row.get("category"));
        r.put("panelId", row.get("parent_id"));
        r.put("panelName", row.get("panel_name"));
        r.put("appliedToReport", true);
        // 扩展字段: 若数据库已存 last_result 等, 直接返回
        r.put("lastResultValue", row.get("last_result_value"));
        r.put("lastResultTime", row.get("last_result_time"));
        r.put("lastAbnormalFlag", row.get("last_abnormal_flag"));
        r.put("odValue", row.get("od_value"));
        r.put("cutoffValue", row.get("cutoff_value"));
        r.put("scRatio", row.get("sc_ratio"));
        r.put("autoAudit", row.get("auto_audit"));
        r.put("autoAuditMessage", row.get("auto_audit_message"));
        r.put("itemKnowledge", row.get("item_knowledge"));
        r.put("imageUrl", row.get("image_url"));
        return r;
    }

    /**
     * 为一批结果批量填充"上次结果" (仅当该行本身未携带 lastResult 时查询, 避免重复 IO)。
     * 同时把数据库已存的 last_result 回填为 null (若存在)。
     */
    private void fillLastResults(List<Map<String, Object>> results, Long patientId, LocalDateTime currentTime) {
        if (results == null || results.isEmpty() || patientId == null) return;
        for (Map<String, Object> r : results) {
            Object testItemIdObj = r.get("testItemId");
            Long testItemId = toLong(testItemIdObj);
            if (testItemId == null) continue;
            // 若 convertResultRow 已从数据库读 last_result_value, 直接跳过 (保持原值)
            if (r.get("lastResultValue") != null) continue;
            try {
                Map<String, Object> last = technicianMapper.selectLastResult(patientId, testItemId, currentTime);
                if (last != null && !last.isEmpty()) {
                    r.put("lastResultValue", last.get("result_value"));
                    r.put("lastResultTime", last.get("test_time"));
                    r.put("lastAbnormalFlag", last.get("abnormal_flag"));
                }
            } catch (Exception e) {
                log.warn("查询上次结果失败: patientId={}, testItemId={}, error={}", patientId, testItemId, e.getMessage());
            }
        }
    }

    // ==================== 工具方法 ====================

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(String.valueOf(obj)); } catch (Exception e) { return null; }
    }

    /**
     * 安全转换数据库返回的时间值为 LocalDateTime (PostgreSQL driver 常返回 java.sql.Timestamp)
     */
    private LocalDateTime toLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
        if (obj instanceof Timestamp) return ((Timestamp) obj).toLocalDateTime();
        try { return LocalDateTime.parse(String.valueOf(obj)); } catch (Exception e) { return null; }
    }

    private Integer toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(String.valueOf(obj)); } catch (Exception e) { return 0; }
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
        try { return new BigDecimal(String.valueOf(obj).trim()); } catch (Exception e) { return null; }
    }

    /** 转字符串, null / 空白统一返回 null */
    private String toStringOrNull(Object obj) {
        if (obj == null) return null;
        String s = String.valueOf(obj).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 宽松地把入参转换为 Long 列表 (前端可能传 Integer / String)。
     * 非法元素被丢弃, 非 List 入参返回空列表。
     */
    private List<Long> toLongList(Object obj) {
        if (!(obj instanceof List)) {
            return new ArrayList<>();
        }
        List<?> raw = (List<?>) obj;
        List<Long> ids = new ArrayList<>(raw.size());
        for (Object o : raw) {
            Long v = toLong(o);
            if (v != null) {
                ids.add(v);
            }
        }
        return ids;
    }

    private Integer parseGender(Object obj) {
        if (obj == null) return null;
        String s = String.valueOf(obj).trim();
        if ("男".equals(s) || "1".equals(s)) return 1;
        if ("女".equals(s) || "2".equals(s)) return 2;
        return null;
    }

    private Integer parseAge(Object obj) {
        if (obj == null) return null;
        try {
            return Integer.parseInt(String.valueOf(obj).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String formatGender(Object obj) {
        if (obj == null) return "";
        String s = String.valueOf(obj).trim();
        if ("1".equals(s)) return "男";
        if ("2".equals(s)) return "女";
        return s;
    }

    private String formatVisitType(Object obj) {
        if (obj == null) return "";
        String s = String.valueOf(obj).trim();
        if ("1".equals(s)) return "门诊";
        if ("2".equals(s)) return "住院";
        if ("3".equals(s)) return "急诊";
        return s;
    }

    /**
     * 将标本状态映射为技师工作站状态
     * 标本状态: 4已核收 5检测中 6已报告
     * 技师工作站: 0待审 1终审 3已完成 99无主标本(待处理)
     */
    private int mapSampleStatus(Object obj) {
        int st = toInt(obj);
        if (st == 99) return 99;            // 特殊状态：无主标本（待认领/作废），透传给前端
        if (st >= 6) return 3;              // 已报告 -> 已完成
        if (st >= 5) return 0;              // 检测中 -> 待审
        return 0;                           // 已核收 -> 待审
    }

    // ==================== 批量操作接口实现 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult batchAudit(BatchAuditDTO dto, Long operatorId) {
        if (dto == null || dto.getSampleIds() == null || dto.getSampleIds().isEmpty()) {
            return BatchOpResult.of(0, 0);
        }
        List<Long> ids = dto.getSampleIds();
        List<String> msgs = new ArrayList<>();
        int success = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long sid : ids) {
            try {
                boolean pass = dto.getPass() != null && dto.getPass();
                String reason = dto.getReason();
                // 逐条覆盖
                if (dto.getPerSample() != null && dto.getPerSample().containsKey(sid)) {
                    Map<String, Object> one = dto.getPerSample().get(sid);
                    if (one.get("pass") != null) pass = Boolean.TRUE.equals(one.get("pass")) || "true".equals(String.valueOf(one.get("pass")));
                    if (one.get("reason") != null) reason = String.valueOf(one.get("reason"));
                }
                int newStatus = pass ? 6 : 4; // 6已报告(终审), 4核收后(驳回重新检测)
                int updated = technicianMapper.updateSampleStatus(sid, newStatus, now);
                if (updated > 0) {
                    success++;
                    log.info("批量审核: sampleId={}, pass={}, reason={}", sid, pass, reason);
                    // 审核通过(终审)时, 生成并发布报告(无需单独发布步骤); 失败不阻断主流程
                    if (pass) {
                        try {
                            reportService.finalizeAndPublish(sid, operatorId);
                        } catch (Exception e) {
                            log.warn("终审生成发布报告失败(不影响标本审核): sampleId={}, error={}", sid, e.getMessage(), e);
                        }
                    }
                } else {
                    msgs.add("标本 #" + sid + " 更新失败, 可能已被删除");
                }
            } catch (Exception e) {
                msgs.add("标本 #" + sid + " 异常: " + e.getMessage());
            }
        }
        return BatchOpResult.of(ids.size(), success, msgs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult batchUnaudit(List<Long> sampleIds) {
        if (sampleIds == null || sampleIds.isEmpty()) return BatchOpResult.of(0, 0);
        List<String> msgs = new ArrayList<>();
        int success = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long sid : sampleIds) {
            try {
                int updated = technicianMapper.unauditSample(sid, 4, now); // 退回已核收状态; 允许撤销已完成(status=6)标本的完成
                if (updated > 0) {
                    success++;
                    log.info("批量撤销审核/完成: sampleId={}", sid);
                } else {
                    msgs.add("标本 #" + sid + " 撤销失败(标本不存在或状态不允许撤销)");
                }
            } catch (Exception e) {
                msgs.add("标本 #" + sid + " 异常: " + e.getMessage());
            }
        }
        return BatchOpResult.of(sampleIds.size(), success, msgs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult batchModifyResult(BatchModifyResultDTO dto) {
        if (dto == null || dto.getSampleIds() == null || dto.getSampleIds().isEmpty()) return BatchOpResult.of(0, 0);
        List<String> msgs = new ArrayList<>();
        int success = 0;
        String scope = dto.getScope() != null ? dto.getScope() : "all";
        String mode = dto.getMode() != null ? dto.getMode() : "fixed";
        for (Long sid : dto.getSampleIds()) {
            try {
                Map<Long, Map<String, Object>> perS = dto.getPerSample();
                if (perS != null && perS.containsKey(sid)) {
                    // 逐条模式: 每个指标明确值
                    Map<String, Object> indicatorMap = perS.get(sid);
                    for (Map.Entry<String, Object> e : indicatorMap.entrySet()) {
                        String code = e.getKey();
                        String val = e.getValue() != null ? String.valueOf(e.getValue()) : null;
                        technicianMapper.upsertResultByCode(sid, code, val, LocalDateTime.now());
                    }
                    success++;
                    continue;
                }
                // 模式化修改
                List<Map<String, Object>> indicators = technicianMapper.selectIndicatorsForModify(
                        sid, scope, dto.getPanelIds(), dto.getIndicatorCodes());
                for (Map<String, Object> ind : indicators) {
                    Long testItemId = toLong(ind.get("test_item_id"));
                    String oldVal = ind.get("result_value") != null ? String.valueOf(ind.get("result_value")) : null;
                    String newVal = calcNewValue(oldVal, mode, dto.getFixedValue(), dto.getOffsetValue(), dto.getFactorValue());
                    if (newVal == null && (dto.getAllowEmpty() == null || !dto.getAllowEmpty())) continue;
                    technicianMapper.upsertResultById(sid, testItemId, newVal, LocalDateTime.now());
                }
                success++;
            } catch (Exception e) {
                msgs.add("标本 #" + sid + " 修改异常: " + e.getMessage());
            }
        }
        return BatchOpResult.of(dto.getSampleIds().size(), success, msgs);
    }

    /** 根据模式计算目标结果值 */
    private String calcNewValue(String oldVal, String mode, String fixed, String offset, Double factor) {
        switch (mode) {
            case "fixed":
                return fixed;
            case "offset":
                return applyOffset(oldVal, offset);
            case "factor":
                return applyFactor(oldVal, factor);
            default:
                return oldVal;
        }
    }

    private String applyOffset(String val, String offset) {
        if (offset == null || offset.isEmpty()) return val;
        try {
            boolean percent = offset.endsWith("%");
            double off = Double.parseDouble(percent ? offset.substring(0, offset.length() - 1) : offset);
            if (val == null || val.isEmpty()) return percent ? "0" : String.valueOf(off);
            double num = Double.parseDouble(val);
            double out = percent ? num * (1 + off / 100) : num + off;
            return formatNumber(out);
        } catch (Exception e) {
            return val;
        }
    }

    private String applyFactor(String val, Double factor) {
        if (factor == null) return val;
        try {
            if (val == null || val.isEmpty()) return "0";
            double num = Double.parseDouble(val);
            return formatNumber(num * factor);
        } catch (Exception e) {
            return val;
        }
    }

    private String formatNumber(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.format("%.3f", d);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult batchModifyInspector(BatchModifyInspectorDTO dto) {
        if (dto == null || dto.getSampleIds() == null || dto.getSampleIds().isEmpty()) return BatchOpResult.of(0, 0);
        List<String> msgs = new ArrayList<>();
        int success = 0;
        String inspector = dto.getInspector();
        LocalDateTime now = LocalDateTime.now();
        for (Long sid : dto.getSampleIds()) {
            try {
                int updated = technicianMapper.updateInspector(sid, inspector, dto.getInspectorUserId(), now);
                if (updated > 0) success++;
                else msgs.add("标本 #" + sid + " 更新失败");
            } catch (Exception e) {
                msgs.add("标本 #" + sid + " 异常: " + e.getMessage());
            }
        }
        return BatchOpResult.of(dto.getSampleIds().size(), success, msgs);
    }

    /**
     * 批量补打条码: 逐标本校验存在性 -> 取条码 -> 写入 lis_barcode_print_log。
     * 成功数为真实写入行数, 失败明细逐条回传。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult batchReprintBarcode(Map<String, Object> params) {
        Map<String, Object> p = params != null ? params : Collections.emptyMap();
        List<Long> ids = toLongList(p.get("sampleIds"));
        if (ids.isEmpty()) {
            return BatchOpResult.of(0, 0);
        }

        int copies = toInt(p.get("copies"));
        if (copies <= 0) {
            copies = 1;
        }
        String printerName = toStringOrNull(p.get("printerName"));
        String reason = toStringOrNull(p.get("reason"));
        Long operatorId = toLong(p.get("operatorId"));
        String operatorName = toStringOrNull(p.get("operatorName"));
        LocalDateTime now = LocalDateTime.now();

        List<String> msgs = new ArrayList<>();
        int success = 0;
        for (Long sid : ids) {
            try {
                Map<String, Object> sample = technicianMapper.selectSampleDetail(sid);
                if (sample == null) {
                    msgs.add("标本 #" + sid + " 不存在或已删除");
                    continue;
                }
                String barcode = toStringOrNull(sample.get("sample_barcode"));
                int inserted = technicianMapper.insertBarcodePrintLog(
                        sid, barcode, copies, printerName, reason, operatorId, operatorName, now);
                if (inserted > 0) {
                    success++;
                    log.info("补打条码: sampleId={}, barcode={}, copies={}, operator={}",
                            sid, barcode, copies, operatorName);
                } else {
                    msgs.add("标本 #" + sid + " 补打记录写入失败");
                }
            } catch (Exception e) {
                msgs.add("标本 #" + sid + " 补打异常: " + e.getMessage());
            }
        }
        return BatchOpResult.of(ids.size(), success, msgs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult waiveReceive(Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) params.get("sampleIds");
        if (ids == null) ids = Collections.emptyList();
        String reason = params.get("reason") != null ? String.valueOf(params.get("reason")) : "";
        List<String> msgs = new ArrayList<>();
        int success = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long sid : ids) {
            try {
                technicianMapper.markWaiveReceive(sid, reason, now);
                success++;
            } catch (Exception e) {
                msgs.add("标本 #" + sid + " 让步失败: " + e.getMessage());
            }
        }
        return BatchOpResult.of(ids.size(), success, msgs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult sampleCopy(Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) params.get("sampleIds");
        // 前端传 copyCount，兼容 times
        int times = toInt(params.get("copyCount"));
        if (times <= 0) times = toInt(params.get("times"));
        if (times <= 0) times = 1;
        if (ids == null) ids = Collections.emptyList();
        int total = ids.size() * times;
        int success = 0;
        List<String> msgs = new ArrayList<>();
        for (Long sid : ids) {
            for (int i = 0; i < times; i++) {
                try {
                    technicianMapper.copySample(sid, LocalDateTime.now());
                    success++;
                } catch (Exception e) {
                    msgs.add("标本 #" + sid + " 复制失败: " + e.getMessage());
                }
            }
        }
        return BatchOpResult.of(total, success, msgs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult sampleSplit(Map<String, Object> params) {
        Long sid = toLong(params.get("sampleId"));
        int splitCount = Math.max(2, toInt(params.getOrDefault("splitCount", 2)));
        String remark = params.get("remark") != null ? String.valueOf(params.get("remark")) : "";
        int newChildIds = 0;
        List<String> msgs = new ArrayList<>();
        try {
            for (int i = 0; i < splitCount - 1; i++) {
                technicianMapper.createSplitChild(sid, i + 1, remark, LocalDateTime.now());
                newChildIds++;
            }
            technicianMapper.markSplitted(sid, LocalDateTime.now());
        } catch (Exception e) {
            msgs.add("拆分失败: " + e.getMessage());
            throw new BusinessException("拆分失败: " + e.getMessage());
        }
        return BatchOpResult.of(1, newChildIds, msgs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult sampleUnsplit(Map<String, Object> params) {
        Long sid = toLong(params.get("sampleId"));
        List<String> msgs = new ArrayList<>();
        try {
            int removed = technicianMapper.removeSplitChildren(sid);
            technicianMapper.markUnsplitted(sid, LocalDateTime.now());
            return BatchOpResult.of(1, 1, msgs);
        } catch (Exception e) {
            msgs.add("撤销拆分失败: " + e.getMessage());
            throw new BusinessException("撤销拆分失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult sampleDestroy(Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) params.get("sampleIds");
        String reason = params.get("reason") != null ? String.valueOf(params.get("reason")) : "";
        // 前端传 approver，兼容 operator
        String operator = params.get("approver") != null ? String.valueOf(params.get("approver"))
                : (params.get("operator") != null ? String.valueOf(params.get("operator")) : "");
        if (ids == null) ids = Collections.emptyList();
        List<String> msgs = new ArrayList<>();
        int success = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long sid : ids) {
            try {
                technicianMapper.markDestroyed(sid, reason, operator, now);
                success++;
            } catch (Exception e) {
                msgs.add("标本 #" + sid + " 销毁失败: " + e.getMessage());
            }
        }
        return BatchOpResult.of(ids.size(), success, msgs);
    }

    /**
     * 仪器工作站签到 / 签退。
     * signin: 先关闭该仪器旧的在岗记录 (保证单一在岗) 再插入新记录; signout: 仅关闭在岗记录。
     *
     * @throws BusinessException instrumentId 为空时
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult instrumentSignin(Map<String, Object> params) {
        Map<String, Object> p = params != null ? params : Collections.emptyMap();
        Long instrumentId = toLong(p.get("instrumentId"));
        if (instrumentId == null) {
            throw new BusinessException("仪器ID不能为空");
        }
        return doSignin(StationSignin.TYPE_INSTRUMENT, instrumentId, instrumentId, p);
    }

    /**
     * 自动审核签到 / 签退。签到维度为操作人 (operator_id), instrument_id 恒为 null。
     *
     * @throws BusinessException operatorId 为空时
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult autoAuditSignin(Map<String, Object> params) {
        Map<String, Object> p = params != null ? params : Collections.emptyMap();
        Long operatorId = toLong(p.get("operatorId"));
        if (operatorId == null) {
            throw new BusinessException("操作人ID不能为空");
        }
        return doSignin(StationSignin.TYPE_AUTO_AUDIT, null, operatorId, p);
    }

    /**
     * 签到/签退统一落库逻辑。
     *
     * @param signinType   签到类型 INSTRUMENT / AUTO_AUDIT
     * @param instrumentId 仪器ID, AUTO_AUDIT 时为 null
     * @param dedupKeyId   去重维度主键: INSTRUMENT 用仪器ID, AUTO_AUDIT 用操作人ID
     * @param p            原始入参
     * @return 成功数取自真实影响行数
     */
    private BatchOpResult doSignin(String signinType, Long instrumentId, Long dedupKeyId, Map<String, Object> p) {
        String action = toStringOrNull(p.get("action"));
        if (action == null || action.trim().isEmpty()) {
            action = StationSignin.ACTION_SIGNIN;
        }
        Long operatorId = toLong(p.get("operatorId"));
        String operatorName = toStringOrNull(p.get("operatorName"));
        String remark = toStringOrNull(p.get("remark"));
        LocalDateTime now = LocalDateTime.now();
        List<String> msgs = new ArrayList<>();

        int affected;
        if (StationSignin.ACTION_SIGNOUT.equalsIgnoreCase(action)) {
            // 签退: 仅关闭在岗记录, 不插入新行
            affected = technicianMapper.closeOpenSignin(signinType, dedupKeyId, now);
            if (affected <= 0) {
                msgs.add("无在岗签到记录可签退: type=" + signinType + ", keyId=" + dedupKeyId);
            }
        } else {
            // 签到: 先关闭旧在岗记录 (保证单一在岗), 再插入新记录
            int closed = technicianMapper.closeOpenSignin(signinType, dedupKeyId, now);
            affected = technicianMapper.insertSignin(
                    signinType, instrumentId, operatorId, operatorName,
                    StationSignin.ACTION_SIGNIN, remark, now);
            if (affected <= 0) {
                msgs.add("签到记录写入失败: type=" + signinType + ", keyId=" + dedupKeyId);
            }
            log.info("工作站签到: type={}, keyId={}, 关闭旧在岗={}, 新增={}",
                    signinType, dedupKeyId, closed, affected);
        }
        log.info("工作站签到/签退完成: type={}, action={}, keyId={}, operator={}, affected={}",
                signinType, action, dedupKeyId, operatorName, affected);
        return BatchOpResult.of(1, Math.max(affected, 0), msgs);
    }

    // ==================== 收藏夹 ====================

    @Override
    public List<Map<String, Object>> getFavorites() {
        return technicianMapper.selectFavorites();
    }

    @Override
    public Map<String, Object> addFavorite(Map<String, Object> params) {
        String name = params.get("name") != null ? String.valueOf(params.get("name")) : "";
        String type = params.get("type") != null ? String.valueOf(params.get("type")) : "sample";
        Object payload = params.get("payload");
        String payloadJson = payload != null ? String.valueOf(payload) : "{}";
        // 尝试从 payload 中提取 sampleIds
        @SuppressWarnings("unchecked")
        List<Object> sampleIds = payload instanceof Map
                ? (List<Object>) ((Map<String, Object>) payload).get("sampleIds")
                : null;
        technicianMapper.insertFavorite(name, type, payloadJson, LocalDateTime.now());
        Map<String, Object> res = new HashMap<>();
        res.put("name", name);
        res.put("type", type);
        res.put("payload", payload);
        res.put("sampleIds", sampleIds);
        res.put("createTime", LocalDateTime.now().toString());
        res.put("success", true);
        return res;
    }

    @Override
    public void removeFavorite(Long id) {
        technicianMapper.deleteFavorite(id);
    }

    // ==================== 撤销核收 / 修改标本号 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult cancelReceive(List<Long> sampleIds) {
        if (sampleIds == null || sampleIds.isEmpty()) return BatchOpResult.of(0, 0);
        List<String> msgs = new ArrayList<>();
        int success = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long sid : sampleIds) {
            try {
                int updated = technicianMapper.cancelReceive(sid, now);
                if (updated > 0) {
                    success++;
                    log.info("撤销核收: sampleId={}", sid);
                } else {
                    msgs.add("标本 #" + sid + " 撤销核收失败, 可能非已核收状态");
                }
            } catch (Exception e) {
                msgs.add("标本 #" + sid + " 异常: " + e.getMessage());
            }
        }
        return BatchOpResult.of(sampleIds.size(), success, msgs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult updateSampleNo(Long sampleId, String sampleNo) {
        if (sampleId == null || sampleNo == null || sampleNo.trim().isEmpty()) {
            return BatchOpResult.of(1, 0, List.of("标本号不能为空"));
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = technicianMapper.updateSampleNo(sampleId, sampleNo.trim(), now);
        if (updated > 0) {
            log.info("修改标本号: sampleId={}, sampleNo={}", sampleId, sampleNo);
            return BatchOpResult.of(1, 1);
        }
        return BatchOpResult.of(1, 0, List.of("标本不存在或已删除"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult updateSampleInfo(Long sampleId, Map<String, Object> params) {
        if (sampleId == null) {
            return BatchOpResult.of(1, 0, List.of("标本ID不能为空"));
        }
        Map<String, Object> p = params != null ? params : Collections.emptyMap();

        // 标本号: 非空才更新
        String sampleNo = toStringOrNull(p.get("sampleNo"));
        // 时间字段: 仅当提供非空合法时间才更新
        LocalDateTime receiveTime = toLocalDateTimeSafe(p.get("receiveTime"));
        LocalDateTime testStartTime = toLocalDateTimeSafe(p.get("testTime"));
        LocalDateTime collectTime = toLocalDateTimeSafe(p.get("collectTime"));
        String receiver = toStringOrNull(p.get("receiver"));
        Long receiverId = toLong(p.get("receiverId"));

        if (sampleNo == null && receiveTime == null && testStartTime == null
                && collectTime == null && receiver == null && receiverId == null) {
            return BatchOpResult.of(1, 0, List.of("没有可修改的字段"));
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = technicianMapper.updateSampleInfo(
                sampleId, sampleNo, receiveTime, testStartTime, collectTime, receiver, receiverId, now);
        if (updated > 0) {
            log.info("修改标本信息: sampleId={}, sampleNo={}, receiveTime={}, testTime={}, receiver={}, receiverId={}",
                    sampleId, sampleNo, receiveTime, testStartTime, receiver, receiverId);
            return BatchOpResult.of(1, 1);
        }
        return BatchOpResult.of(1, 0, List.of("标本不存在或非核收未审核状态, 无法修改"));
    }

    /** 宽松解析时间字符串为 LocalDateTime, 非法返回 null (不阻断其它字段更新) */
    private LocalDateTime toLocalDateTimeSafe(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
        if (obj instanceof Timestamp) return ((Timestamp) obj).toLocalDateTime();
        String s = String.valueOf(obj).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            // 兼容前端 value-format="YYYY-MM-DD HH:mm:ss" (空格分隔) 与 ISO (T 分隔)
            return java.time.LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception e) {
            // 兼容 "YYYY-MM-DD" 格式
            try {
                return java.time.LocalDate.parse(s).atStartOfDay();
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
