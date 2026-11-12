package com.lis.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.api.dto.ReportAuditDTO;
import com.lis.api.dto.ResultDTO;
import com.lis.api.vo.ReportVO;
import com.lis.api.vo.ResultVO;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.PageResult;
import com.lis.report.entity.Report;
import com.lis.report.entity.ReportModifyLog;
import com.lis.report.entity.Result;
import com.lis.report.mapper.ReportMapper;
import com.lis.report.mapper.ReportModifyLogMapper;
import com.lis.report.mapper.ResultMapper;
import com.lis.report.mapper.TechnicianMapper;
import com.lis.api.vo.SampleImageVO;
import com.lis.report.service.ReportService;
import com.lis.report.service.audit.AuditResult;
import com.lis.report.service.audit.AutoAuditEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 报告服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final ResultMapper resultMapper;
    private final ReportModifyLogMapper modifyLogMapper;
    private final AutoAuditEngine autoAuditEngine;
    private final RabbitTemplate rabbitTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final TechnicianMapper technicianMapper;

    @Value("${mq.exchange:lis.exchange}")
    private String exchange;
    @Value("${mq.report.published-routing-key:report.published}")
    private String publishedRoutingKey;
    @Value("${reagent.c2.base-url:http://localhost:8092}")
    private String reagentBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVO generateReport(Long sampleId) {
        // 1. 校验标本结果存在
        List<Result> results = resultMapper.selectList(
                new LambdaQueryWrapper<Result>().eq(Result::getSampleId, sampleId));
        if (results.isEmpty()) {
            throw new BusinessException("标本 [" + sampleId + "] 无检验结果，无法生成报告");
        }

        // 2. 检查是否已生成报告
        Report exist = reportMapper.selectOne(
                new LambdaQueryWrapper<Report>().eq(Report::getSampleId, sampleId));
        if (exist != null) {
            return convertToVO(exist, results);
        }

        // 3. 创建报告草稿
        Report report = new Report();
        report.setSampleId(sampleId);
        report.setReportNo(generateReportNo(sampleId));
        // 简化: 从首个结果中取患者信息
        report.setPatientId(results.get(0).getOperatorId());
        report.setReportType(1);
        report.setStatus(Report.STATUS_DRAFT);
        report.setReportDoctor("系统生成");
        report.setHisSyncStatus(0);
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        reportMapper.insert(report);
        log.info("生成报告草稿: reportNo={}, sampleId={}", report.getReportNo(), sampleId);
        return convertToVO(report, results);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean autoAudit(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("报告不存在: " + reportId);
        }
        if (report.getStatus() != Report.STATUS_DRAFT) {
            throw new BusinessException("报告状态非草稿，无法自动审核");
        }
        ReportVO vo = getById(reportId);
        AuditResult result = autoAuditEngine.evaluate(vo);
        if (result.isPassed()) {
            // 审核通过进入 STATUS_AUDITED, 后续需调用 publish 完成发布
            report.setStatus(Report.STATUS_AUDITED);
            report.setAuditorId(0L); // 0 表示系统自动
            report.setAuditTime(LocalDateTime.now());
            report.setHisSyncStatus(0);
            report.setUpdateTime(LocalDateTime.now());
            reportMapper.updateById(report);
            // 同步更新结果审核状态
            markResultsAudited(report.getSampleId(), Result.AUDIT_AUTO, 0L);
            log.info("报告自动审核通过: reportId={}", reportId);
            // C2: 按检验项目反向扣减试剂库存 (不阻断主流程)
            triggerReagentAutoConsume(report.getSampleId(), 0L, report.getReportNo());
            return true;
        }
        log.warn("报告自动审核未通过: reportId={}, rule={}, msg={}",
                reportId, result.getRuleName(), result.getMessage());
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean manualAudit(ReportAuditDTO dto) {
        Report report = reportMapper.selectById(dto.getReportId());
        if (report == null) {
            throw new BusinessException("报告不存在: " + dto.getReportId());
        }
        if (dto.isPass()) {
            // 审核通过进入 STATUS_AUDITED, 后续需调用 publish 完成发布
            report.setStatus(Report.STATUS_AUDITED);
            report.setAuditorId(dto.getAuditorId());
            report.setAuditTime(LocalDateTime.now());
            report.setHisSyncStatus(0);
            markResultsAudited(report.getSampleId(), Result.AUDIT_MANUAL, dto.getAuditorId());
        } else {
            // 人工审核不通过，保留草稿状态，记录原因
            log.info("人工审核不通过: reportId={}, reason={}", dto.getReportId(), dto.getReason());
        }
        report.setUpdateTime(LocalDateTime.now());
        reportMapper.updateById(report);
        if (dto.isPass()) {
            log.info("报告人工审核通过: reportId={}", dto.getReportId());
            // C2: 按检验项目反向扣减试剂库存 (不阻断主流程)
            triggerReagentAutoConsume(report.getSampleId(), dto.getAuditorId(), report.getReportNo());
        }
        return dto.isPass();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publish(Long reportId, Long publisherId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("报告不存在: " + reportId);
        }
        if (report.getStatus() != Report.STATUS_AUDITED) {
            throw new BusinessException("报告未审核，无法发布");
        }
        report.setStatus(Report.STATUS_PUBLISHED);
        report.setPublisherId(publisherId);
        report.setPublishTime(LocalDateTime.now());
        report.setHisSyncStatus(0);
        report.setUpdateTime(LocalDateTime.now());
        reportMapper.updateById(report);

        // 发送 MQ 消息 (MQ不可用时不阻断主流程)
        ReportVO vo = getById(reportId);
        try { rabbitTemplate.convertAndSend(exchange, publishedRoutingKey, vo); } catch (Exception e) { log.warn("MQ发送失败,忽略: {}", e.getMessage()); }
        log.info("报告已发布，发送 MQ 消息: reportId={}", reportId);
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean finalizeAndPublish(Long sampleId, Long operatorId) {
        // 1. 生成报告(若不存在), 无检验结果时也生成一条记录(终审通过则视为已报告)
        Report report = reportMapper.selectOne(
                new LambdaQueryWrapper<Report>().eq(Report::getSampleId, sampleId));
        if (report == null) {
            report = new Report();
            report.setSampleId(sampleId);
            report.setReportNo(generateReportNo(sampleId));
            report.setReportType(1);
            report.setReportDoctor("系统生成");
            report.setHisSyncStatus(0);
            // 从标本中回填患者信息, 保证 NOT NULL 的 patient_id 及报告展示所需字段
            fillPatientInfoFromSample(report, sampleId);
            // 兜底: 标本查询失败或 patient_id 为空时, 以 sampleId 代替, 避免 NOT NULL 冲突
            if (report.getPatientId() == null) {
                report.setPatientId(sampleId);
            }
            report.setCreateTime(LocalDateTime.now());
            report.setUpdateTime(LocalDateTime.now());
            reportMapper.insert(report);
            report = reportMapper.selectOne(
                    new LambdaQueryWrapper<Report>().eq(Report::getSampleId, sampleId));
        }

        // 2. 直接设为已发布(终审), 无需单独发布步骤
        LocalDateTime now = LocalDateTime.now();
        report.setStatus(Report.STATUS_PUBLISHED);
        report.setAuditorId(operatorId);
        report.setAuditTime(now);
        report.setPublisherId(operatorId);
        report.setPublishTime(now);
        report.setHisSyncStatus(0);
        report.setUpdateTime(now);
        reportMapper.updateById(report);

        // 3. 发送 MQ 消息 (MQ不可用时不阻断主流程)
        try {
            ReportVO vo = getById(report.getId());
            rabbitTemplate.convertAndSend(exchange, publishedRoutingKey, vo);
        } catch (Exception e) {
            log.warn("MQ发送失败,忽略: {}", e.getMessage());
        }
        log.info("技师终审生成并发布报告: sampleId={}, reportId={}, operatorId={}",
                sampleId, report.getId(), operatorId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean modifyReport(Long reportId, List<ResultDTO> newResults, String reason) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("报告不存在: " + reportId);
        }
        if (report.getStatus() == Report.STATUS_DRAFT) {
            throw new BusinessException("草稿报告无需修改留痕，直接编辑即可");
        }

        for (ResultDTO dto : newResults) {
            Result old = resultMapper.selectOne(new LambdaQueryWrapper<Result>()
                    .eq(Result::getSampleId, report.getSampleId())
                    .eq(Result::getTestItemId, dto.getTestItemId()));
            if (old == null) {
                continue;
            }
            ReportModifyLog modifyLog = new ReportModifyLog();
            modifyLog.setReportId(reportId);
            modifyLog.setItemId(dto.getTestItemId());
            modifyLog.setOldValue(old.getResultValue());
            modifyLog.setNewValue(dto.getResultValue());
            modifyLog.setModifyReason(reason);
            modifyLog.setOperatorId(dto.getOperatorId());
            modifyLog.setModifyTime(LocalDateTime.now());
            modifyLogMapper.insert(modifyLog);

            // 更新结果
            old.setResultValue(dto.getResultValue());
            old.setResultText(dto.getResultText());
            old.setUpdateTime(LocalDateTime.now());
            resultMapper.updateById(old);
        }
        // 改为已修改状态，需重新审核
        report.setStatus(Report.STATUS_MODIFIED);
        report.setUpdateTime(LocalDateTime.now());
        reportMapper.updateById(report);
        return true;
    }

    @Override
    public ReportVO getById(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            return null;
        }
        List<Result> results = resultMapper.selectList(
                new LambdaQueryWrapper<Result>().eq(Result::getSampleId, report.getSampleId()));
        return convertToVO(report, results);
    }

    @Override
    public ReportVO getBySampleId(Long sampleId) {
        Report report = reportMapper.selectOne(
                new LambdaQueryWrapper<Report>().eq(Report::getSampleId, sampleId));
        if (report == null) {
            return null;
        }
        List<Result> results = resultMapper.selectList(
                new LambdaQueryWrapper<Result>().eq(Result::getSampleId, sampleId));
        return convertToVO(report, results);
    }

    @Override
    public PageResult<ReportVO> queryPage(int current, int size, String patientName, Integer status,
                                          String startDate, String endDate, Long patientId,
                                          Integer visitType, String department,
                                          String sampleNo, String sampleBarcode, String visitNo,
                                          String keyword) {
        // 解析 startDate/endDate 为 LocalDateTime: 支持 yyyy-MM-dd 和 yyyy-MM-dd HH:mm:ss 两种格式
        LocalDateTime startDt = parseDate(startDate, true);
        LocalDateTime endDt = parseDate(endDate, false);

        // 构建动态 SQL: LEFT JOIN 标本/申请/就诊 等表 (不依赖 deleted 字段, 不存在则列返回 NULL)
        StringBuilder from = new StringBuilder(
                "FROM lis_report r " +
                "LEFT JOIN lis_sample s ON r.sample_id = s.id " +
                "LEFT JOIN lis_lab_order o ON s.order_id = o.id " +
                "LEFT JOIN lis_visit v ON o.visit_id = v.id " +
                "WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (patientName != null && !patientName.isBlank()) {
            from.append("AND r.patient_name LIKE ? ");
            args.add("%" + patientName.trim() + "%");
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            from.append("AND (r.patient_name LIKE ? OR r.report_no LIKE ? OR s.sample_barcode LIKE ? OR v.visit_no LIKE ?) ");
            args.add(kw); args.add(kw); args.add(kw); args.add(kw);
        }
        if (status != null) {
            from.append("AND r.status = ? ");
            args.add(status);
        }
        if (patientId != null) {
            from.append("AND r.patient_id = ? ");
            args.add(patientId);
        }
        if (startDt != null) {
            from.append("AND COALESCE(r.publish_time, r.create_time) >= ?::timestamp ");
            args.add(java.sql.Timestamp.valueOf(startDt));
        }
        if (endDt != null) {
            from.append("AND COALESCE(r.publish_time, r.create_time) <= ?::timestamp ");
            args.add(java.sql.Timestamp.valueOf(endDt));
        }
        if (sampleBarcode != null && !sampleBarcode.isBlank()) {
            from.append("AND s.sample_barcode LIKE ? ");
            args.add("%" + sampleBarcode.trim() + "%");
        }
        if (sampleNo != null && !sampleNo.isBlank()) {
            String sn = sampleNo.trim();
            // 支持 1,2,3 枚举和 1-3 区间模式
            if (sn.matches("[\\d,\\s\\-]+")) {
                java.util.Set<Long> ids = new java.util.HashSet<>();
                for (String p : sn.split("[,;\\s]+")) {
                    String q = p.trim();
                    if (q.isEmpty()) continue;
                    if (q.contains("-")) {
                        String[] ab = q.split("-", 2);
                        try {
                            long a = Long.parseLong(ab[0].trim());
                            long b = Long.parseLong(ab[1].trim());
                            long mn = Math.min(a, b), mx = Math.max(a, b);
                            for (long i = mn; i <= mx; i++) ids.add(i);
                        } catch (Exception ignored) {}
                    } else {
                        try { ids.add(Long.parseLong(q)); } catch (Exception ignored) {}
                    }
                }
                if (!ids.isEmpty()) {
                    from.append("AND r.sample_id IN (");
                    for (int i = 0; i < ids.size(); i++) from.append(i == 0 ? "?" : ",?");
                    from.append(") ");
                    args.addAll(ids);
                } else {
                    from.append("AND CAST(r.sample_id AS TEXT) LIKE ? ");
                    args.add("%" + sn + "%");
                }
            } else {
                from.append("AND CAST(r.sample_id AS TEXT) LIKE ? ");
                args.add("%" + sn + "%");
            }
        }
        if (visitNo != null && !visitNo.isBlank()) {
            from.append("AND v.visit_no LIKE ? ");
            args.add("%" + visitNo.trim() + "%");
        }
        if (visitType != null) {
            from.append("AND (v.visit_type = ? OR (v.visit_type IS NULL AND ? = 1)) ");
            args.add(visitType); args.add(visitType);
        }
        if (department != null && !department.isBlank()) {
            String dp = "%" + department.trim() + "%";
            from.append("AND (v.department LIKE ? OR s.department LIKE ? OR o.apply_dept LIKE ?) ");
            args.add(dp); args.add(dp); args.add(dp);
        }

        String countSql = "SELECT COUNT(DISTINCT r.id) " + from;
        Integer total;
        try {
            total = jdbcTemplate.queryForObject(countSql, Integer.class, args.toArray());
        } catch (Exception e) {
            log.error("查询报告总数失败: {}", e.getMessage());
            total = 0;
        }
        if (total == null) total = 0;

        List<ReportVO> records = new ArrayList<>();
        if (total > 0) {
            int offset = Math.max(0, (current - 1)) * size;
            // 先分页取出 id 列表: 使用 GROUP BY r.id 聚合去重 + ORDER BY 聚合排序,
            // 规避 PostgreSQL "SELECT DISTINCT + ORDER BY 表达式必须出现在 SELECT 中" 的限制
            String idSelect = "SELECT r.id AS id, MAX(COALESCE(r.publish_time, r.create_time)) AS sort_key " +
                    from + " GROUP BY r.id ORDER BY sort_key DESC LIMIT ? OFFSET ?";
            List<Object> idArgs = new ArrayList<>(args);
            idArgs.add(size);
            idArgs.add(offset);
            List<Long> pageIds = new ArrayList<>();
            try {
                List<Map<String, Object>> idRows = jdbcTemplate.queryForList(idSelect, idArgs.toArray());
                for (Map<String, Object> ir : idRows) {
                    Long v = toLong(ir.get("id"));
                    if (v != null) pageIds.add(v);
                }
                log.debug("报告分页 id 查询 pageIds.size={}, total={}", pageIds.size(), total);
            } catch (Exception e) {
                log.error("报告分页 id 查询失败: {}, SQL={}, args={}", e.getMessage(), idSelect, idArgs, e);
                pageIds = new ArrayList<>();
            }
            if (!pageIds.isEmpty()) {
                // 用 IN (id...) 取详情行 (无 LIMIT/OFFSET, 不再带复杂条件, 直接 id 匹配)
                String inPh = pageIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
                String detailSql = "SELECT r.id, r.report_no, r.sample_id, r.patient_id, r.patient_name, " +
                        "r.status, r.auditor_id, r.audit_time, r.publisher_id, r.publish_time, " +
                        "r.report_doctor, r.checker, r.create_time, " +
                        "r.patient_gender AS patient_gender, " +
                        "COALESCE(r.patient_age, 0) AS patient_age, " +
                        "COALESCE(v.visit_type, 1) AS visit_type, " +
                        "COALESCE(v.department, s.department, o.apply_dept, '') AS department, " +
                        "COALESCE(v.doctor_name, o.apply_doctor, '') AS doctor, " +
                        "COALESCE(o.clinical_diag, '') AS clinical_diag, " +
                        "COALESCE(s.sample_barcode, '') AS sample_barcode, " +
                        "s.receive_time AS receive_time, " +
                        "v.visit_no AS visit_no " +
                        "FROM lis_report r " +
                        "LEFT JOIN lis_sample s ON r.sample_id = s.id " +
                        "LEFT JOIN lis_lab_order o ON s.order_id = o.id " +
                        "LEFT JOIN lis_visit v ON o.visit_id = v.id " +
                        "WHERE r.id IN (" + inPh + ") " +
                        "ORDER BY COALESCE(r.publish_time, r.create_time) DESC";
                List<Map<String, Object>> rows;
                try {
                    rows = jdbcTemplate.queryForList(detailSql, pageIds.toArray());
                } catch (Exception e) {
                    log.error("查询报告详情失败: {}", e.getMessage());
                    rows = new ArrayList<>();
                }
                // 收集 reportId 批量填充 testItems 字符串
                List<Long> reportIds = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    Object id = row.get("id");
                    if (id != null) reportIds.add(((Number) id).longValue());
                }
                Map<Long, String> reportItemMap = batchQueryReportItemNames(reportIds);

                for (Map<String, Object> row : rows) {
                    ReportVO vo = new ReportVO();
                    vo.setId(toLong(row.get("id")));
                    vo.setReportNo(toStringOrNull(row.get("report_no")));
                    vo.setSampleId(toLong(row.get("sample_id")));
                    vo.setPatientId(toLong(row.get("patient_id")));
                    vo.setPatientName(toStringOrNull(row.get("patient_name")));
                    vo.setStatus(toInt(row.get("status")));
                    vo.setAuditorId(toLong(row.get("auditor_id")));
                    vo.setAuditTime(toLocalDateTime(row.get("audit_time")));
                    vo.setPublisherId(toLong(row.get("publisher_id")));
                    vo.setPublishTime(toLocalDateTime(row.get("publish_time")));
                    vo.setReportDoctor(toStringOrNull(row.get("report_doctor")));
                    vo.setChecker(toStringOrNull(row.get("checker")));
                    vo.setCreateTime(toLocalDateTime(row.get("create_time")));
                    vo.setPatientGender(toSmallInt(row.get("patient_gender")));
                    vo.setPatientAge(toInt(row.get("patient_age")));
                    vo.setVisitType(toSmallInt(row.get("visit_type")));
                    vo.setHospitalName("连平县人民医院");
                    vo.setDepartment(toStringOrNull(row.get("department")));
                    vo.setDoctor(toStringOrNull(row.get("doctor")));
                    vo.setClinicalDiag(toStringOrNull(row.get("clinical_diag")));
                    vo.setSampleNo(vo.getSampleId() != null ? String.valueOf(vo.getSampleId()) : null);
                    vo.setSampleBarcode(toStringOrNull(row.get("sample_barcode")));
                    vo.setReceiveTime(toLocalDateTime(row.get("receive_time")));
                    vo.setVisitNo(toStringOrNull(row.get("visit_no")));
                    vo.setSource(sourceOf(vo.getVisitType()));
                    vo.setStatusText(statusText(vo.getStatus()));
                    vo.setTestItems(reportItemMap.getOrDefault(vo.getId(), ""));
                    records.add(vo);
                }
            }
        }
        return new PageResult<>(records, Long.valueOf(total), Long.valueOf(current), Long.valueOf(size));
    }

    /**
     * 批量查询报告的申请项目名称 (使用 sampleId -> lis_order_item 反查 lis_test_item)
     * LEFT JOIN, 即使没项目也不丢 report_id
     */
    private Map<Long, String> batchQueryReportItemNames(List<Long> reportIds) {
        Map<Long, String> result = new java.util.HashMap<>();
        if (reportIds == null || reportIds.isEmpty()) return result;
        // 先塞空串, 避免后续 null
        for (Long id : reportIds) result.put(id, "");
        String inClause = reportIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT r.id AS report_id, COALESCE(string_agg(DISTINCT ti.item_name, '、'), '') AS items " +
                "FROM lis_report r " +
                "LEFT JOIN lis_sample s ON r.sample_id = s.id " +
                "LEFT JOIN lis_order_item oi ON s.order_id = oi.order_id " +
                "LEFT JOIN lis_test_item ti ON oi.test_item_id = ti.id " +
                "WHERE r.id IN (" + inClause + ") GROUP BY r.id";
        try {
            jdbcTemplate.query(sql, rs -> {
                result.put(rs.getLong("report_id"), rs.getString("items"));
            });
        } catch (Exception e) {
            log.warn("批量查询报告项目名失败: {}", e.getMessage());
        }
        return result;
    }

    private String sourceOf(Integer visitType) {
        if (visitType == null) return "门";
        return switch (visitType) {
            case 1 -> "门";
            case 2 -> "住";
            case 3 -> "体";
            default -> "门";
        };
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
    private Integer toSmallInt(Object o) { return toInt(o); }
    private String toStringOrNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
    private LocalDateTime toLocalDateTime(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime) return (LocalDateTime) o;
        if (o instanceof java.sql.Timestamp) return ((java.sql.Timestamp) o).toLocalDateTime();
        try { return LocalDateTime.parse(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LocalDateTime parseDate(String s, boolean isStart) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        try {
            // 先尝试带时间格式
            if (v.length() > 10) return LocalDateTime.parse(v, DATETIME_FMT);
            // 仅日期部分: 起始=00:00, 结束=23:59:59
            LocalDate d = LocalDate.parse(v, DATE_FMT);
            return isStart ? d.atStartOfDay() : d.atTime(23, 59, 59);
        } catch (Exception e) {
            log.warn("解析日期失败: {}", v);
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVO mergeReports(List<Long> sampleIds, Long targetSampleId) {
        if (sampleIds == null || sampleIds.isEmpty()) {
            throw new BusinessException("待合并标本列表不能为空");
        }
        if (targetSampleId == null) {
            throw new BusinessException("目标标本ID不能为空");
        }
        if (sampleIds.contains(targetSampleId)) {
            throw new BusinessException("待合并标本列表不能包含目标标本");
        }

        LocalDateTime now = LocalDateTime.now();
        int mergedCount = 0;

        // 1. 将各标本结果复制到目标标本下
        for (Long sampleId : sampleIds) {
            List<Result> sourceResults = resultMapper.selectList(
                    new LambdaQueryWrapper<Result>().eq(Result::getSampleId, sampleId));
            if (sourceResults.isEmpty()) {
                log.warn("标本 {} 无检验结果, 跳过合并", sampleId);
                continue;
            }
            for (Result src : sourceResults) {
                Result dest = new Result();
                dest.setSampleId(targetSampleId);
                dest.setTestItemId(src.getTestItemId());
                dest.setResultValue(src.getResultValue());
                dest.setResultText(src.getResultText());
                dest.setUnit(src.getUnit());
                dest.setRefRange(src.getRefRange());
                dest.setAbnormalFlag(src.getAbnormalFlag());
                dest.setIsCritical(src.getIsCritical() == null ? 0 : src.getIsCritical());
                dest.setInstrumentId(src.getInstrumentId());
                dest.setOperatorId(src.getOperatorId());
                dest.setTestTime(src.getTestTime());
                dest.setAuditStatus(Result.AUDIT_NONE);
                dest.setCreateTime(now);
                dest.setUpdateTime(now);
                resultMapper.insert(dest);
                mergedCount++;
            }
        }

        // 2. 生成一份合并报告
        Report exist = reportMapper.selectOne(
                new LambdaQueryWrapper<Report>().eq(Report::getSampleId, targetSampleId));
        Report report;
        if (exist != null) {
            exist.setReportType(1);
            exist.setStatus(Report.STATUS_DRAFT);
            exist.setUpdateTime(now);
            reportMapper.updateById(exist);
            report = exist;
        } else {
            report = new Report();
            report.setSampleId(targetSampleId);
            report.setReportNo(generateReportNo(targetSampleId));
            report.setReportType(1);
            report.setStatus(Report.STATUS_DRAFT);
            report.setReportDoctor("系统合并");
            report.setHisSyncStatus(0);
            report.setCreateTime(now);
            report.setUpdateTime(now);
            reportMapper.insert(report);
        }

        // 3. 记录合并日志 (复用 ReportModifyLog 表)
        ReportModifyLog mergeLog = new ReportModifyLog();
        mergeLog.setReportId(report.getId());
        mergeLog.setItemId(0L);
        mergeLog.setOldValue(String.valueOf(sampleIds));
        mergeLog.setNewValue(String.valueOf(targetSampleId));
        mergeLog.setModifyReason("合并报告: 源标本" + sampleIds + " -> 目标标本" + targetSampleId
                + ", 合并结果数=" + mergedCount);
        mergeLog.setOperatorId(0L);
        mergeLog.setOperatorName("系统合并");
        mergeLog.setModifyTime(now);
        modifyLogMapper.insert(mergeLog);

        log.info("标本合并成功: 源={}, 目标={}, 合并结果数={}", sampleIds, targetSampleId, mergedCount);
        return getById(report.getId());
    }

    @Override
    public List<ReportVO> getPublishedByVisitNo(String visitNo) {
        if (visitNo == null || visitNo.isBlank()) {
            throw new BusinessException("就诊号不能为空");
        }
        List<Report> reports = reportMapper.selectPublishedByVisitNo(visitNo);
        return reports.stream().map(r -> convertToVO(r, null)).collect(Collectors.toList());
    }

    @Override
    public List<ReportVO> getPublishedByIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            throw new BusinessException("身份证号不能为空");
        }
        String idCardHash = sha256(idCard.trim());
        List<Report> reports = reportMapper.selectPublishedByIdCardHash(idCardHash);
        return reports.stream().map(r -> convertToVO(r, null)).collect(Collectors.toList());
    }

    @Override
    public List<ReportVO> getPublishedByVisitNoAndIdCard(String visitNo, String idCard) {
        if (visitNo == null || visitNo.isBlank() || idCard == null || idCard.isBlank()) {
            throw new BusinessException("就诊号与身份证号均不能为空");
        }
        String idCardHash = sha256(idCard.trim());
        List<Report> reports = reportMapper.selectPublishedByVisitNoAndIdCardHash(visitNo.trim(), idCardHash);
        return reports.stream().map(r -> convertToVO(r, null)).collect(Collectors.toList());
    }

    /**
     * 计算身份证号 SHA-256 哈希 (用于匹配 lis_patient.id_card_hash)
     */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("SHA-256 算法不可用");
        }
    }

    private void markResultsAudited(Long sampleId, int auditStatus, Long auditorId) {
        List<Result> results = resultMapper.selectList(
                new LambdaQueryWrapper<Result>().eq(Result::getSampleId, sampleId));
        LocalDateTime now = LocalDateTime.now();
        for (Result r : results) {
            r.setAuditStatus(auditStatus);
            r.setAuditorId(auditorId);
            r.setAuditTime(now);
            r.setUpdateTime(now);
            resultMapper.updateById(r);
        }
    }

    /**
     * C2集成: 报告审核通过后, 按检验项目反向扣减试剂库存
     * 对每个检验项目调用试剂服务的自动扣减接口, 库存不足时记录警告不阻断主流程
     */
    private void triggerReagentAutoConsume(Long sampleId, Long operatorId, String relatedNo) {
        try {
            List<Result> results = resultMapper.selectList(
                    new LambdaQueryWrapper<Result>().eq(Result::getSampleId, sampleId));
            // 按检验项目分组, 每个项目计为1次测试
            Map<Long, Long> itemTestCount = results.stream()
                    .filter(r -> r.getTestItemId() != null)
                    .collect(Collectors.groupingBy(Result::getTestItemId, Collectors.counting()));

            String url = reagentBaseUrl + "/api/reagent/consumption/auto";
            for (Map.Entry<Long, Long> entry : itemTestCount.entrySet()) {
                try {
                    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                    params.add("testItemId", String.valueOf(entry.getKey()));
                    params.add("testCount", String.valueOf(entry.getValue()));
                    if (operatorId != null) params.add("operatorId", String.valueOf(operatorId));
                    if (relatedNo != null) params.add("relatedNo", relatedNo);
                    restTemplate.postForEntity(url, params, String.class);
                    log.info("C2试剂扣减: testItemId={}, count={}, relatedNo={}", entry.getKey(), entry.getValue(), relatedNo);
                } catch (Exception e) {
                    log.warn("C2试剂扣减失败(不阻断): testItemId={}, err={}", entry.getKey(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("C2试剂扣减整体失败(不阻断): sampleId={}, err={}", sampleId, e.getMessage());
        }
    }

    private ReportVO convertToVO(Report report, List<Result> results) {
        ReportVO vo = new ReportVO();
        BeanUtils.copyProperties(report, vo);
        vo.setStatusText(statusText(report.getStatus()));
        // 填充扩展字段 (患者, 标本, 申请)
        enrichReportExtra(vo, report);

        if (results != null) {
            Long patientId = report.getPatientId();
            Long sampleId = report.getSampleId();
            // 先批量查字典
            List<Long> itemIds = new ArrayList<>();
            for (Result r : results) {
                if (r.getTestItemId() != null) itemIds.add(r.getTestItemId());
            }
            Map<Long, Map<String, Object>> itemInfoMap = batchQueryTestItemInfo(itemIds);

            List<ResultVO> resultVOs = new ArrayList<>();
            for (Result r : results) {
                ResultVO rvo = new ResultVO();
                BeanUtils.copyProperties(r, rvo);
                rvo.setAuditStatusText(auditStatusText(r.getAuditStatus()));
                // 字典回填
                Map<String, Object> info = r.getTestItemId() != null ? itemInfoMap.get(r.getTestItemId()) : null;
                if (info != null) {
                    rvo.setTestItemCode(toStringOrNull(info.get("item_code")));
                    rvo.setEnglishName(toStringOrNull(info.get("item_name_en")));
                    rvo.setTestItemName(firstNonNull(rvo.getTestItemName(), toStringOrNull(info.get("item_name"))));
                    rvo.setPanelId(toLong(info.get("parent_id")));
                    rvo.setPanelName(toStringOrNull(info.get("panel_name")));
                    rvo.setCategory(toStringOrNull(info.get("category")));
                    rvo.setInterpretation(toStringOrNull(info.get("interpretation")));
                    if (rvo.getUnit() == null || rvo.getUnit().isBlank()) {
                        rvo.setUnit(toStringOrNull(info.get("unit")));
                    }
                    if (rvo.getRefRange() == null || rvo.getRefRange().isBlank()) {
                        rvo.setRefRange(buildRefRangeFromMap(info));
                    }
                }
                // 填充上次结果 (历史结果对比)
                if (patientId != null && sampleId != null && r.getTestItemId() != null) {
                    try {
                        Result last = resultMapper.selectLastPublishedByPatientAndItem(
                                patientId, r.getTestItemId(), sampleId);
                        if (last != null) {
                            rvo.setLastResult(last.getResultValue());
                            rvo.setLastFlag(last.getAbnormalFlag());
                            rvo.setLastTestTime(last.getTestTime());
                        }
                    } catch (Exception e) {
                        log.debug("查询历史结果失败(忽略): patientId={}, itemId={}", patientId, r.getTestItemId());
                    }
                }
                resultVOs.add(rvo);
            }
            vo.setResults(resultVOs);
            // testItems 字符串
            if (vo.getTestItems() == null || vo.getTestItems().isBlank()) {
                java.util.Set<String> names = new java.util.LinkedHashSet<>();
                for (ResultVO rv : resultVOs) {
                    if (rv.getPanelName() != null && !rv.getPanelName().isBlank()) names.add(rv.getPanelName());
                    else if (rv.getTestItemName() != null && !rv.getTestItemName().isBlank()) names.add(rv.getTestItemName());
                }
                vo.setTestItems(String.join("、", names));
            }
        }

        // 查询标本图像 (血常规/尿常规等形态图像, 用于报告打印展示)
        Long sampleId = report.getSampleId();
        if (sampleId != null) {
            try {
                List<Map<String, Object>> imageRows = technicianMapper.selectSampleImages(sampleId);
                if (imageRows != null && !imageRows.isEmpty()) {
                    List<SampleImageVO> images = new ArrayList<>();
                    for (Map<String, Object> row : imageRows) {
                        SampleImageVO img = new SampleImageVO();
                        img.setId(toLong(row.get("id")));
                        img.setSampleId(toLong(row.get("sampleId")));
                        img.setImageUrl(toStringOrNull(row.get("imageUrl")));
                        img.setImageName(toStringOrNull(row.get("imageName")));
                        img.setImageType(toStringOrNull(row.get("imageType")));
                        img.setSortNo(toInt(row.get("sortNo")));
                        images.add(img);
                    }
                    vo.setSampleImages(images);
                }
            } catch (Exception e) {
                log.debug("查询标本图像失败(忽略): sampleId={}, err={}", sampleId, e.getMessage());
            }
        }

        return vo;
    }

    private String firstNonNull(String... arr) {
        if (arr == null) return null;
        for (String s : arr) { if (s != null && !s.isBlank()) return s; }
        return null;
    }

    private String buildRefRangeFromMap(Map<String, Object> info) {
        String refText = toStringOrNull(info.get("ref_text"));
        if (refText != null) return refText;
        String low = toStringOrNull(info.get("ref_low"));
        String high = toStringOrNull(info.get("ref_high"));
        if (low != null && high != null) return low + "-" + high;
        if (low != null) return ">" + low;
        if (high != null) return "<" + high;
        return null;
    }

    /**
     * 批量查询检验项目字典信息: code/英文/中文/面板/分类/单位/参考范围
     */
    private Map<Long, Map<String, Object>> batchQueryTestItemInfo(List<Long> itemIds) {
        Map<Long, Map<String, Object>> map = new java.util.HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) return map;
        // 去重
        String inClause = itemIds.stream().distinct().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT t.id, t.item_code, t.item_name, t.item_name_en, t.parent_id, " +
                "t.category, t.unit, t.status, " +
                "p.item_name AS panel_name, r.ref_text, r.ref_low, r.ref_high, " +
                "''::TEXT AS interpretation " +
                "FROM lis_test_item t " +
                "LEFT JOIN lis_test_item p ON p.id = t.parent_id " +
                "LEFT JOIN LATERAL (SELECT ref_text, ref_low, ref_high " +
                "  FROM lis_reference_range rr WHERE rr.test_item_id = t.id " +
                "  ORDER BY rr.id LIMIT 1) r ON TRUE " +
                "WHERE t.id IN (" + inClause + ")";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("id"));
            if (id != null) map.put(id, row);
        }
        return map;
    }

    /**
     * 为 ReportVO 填充报告查询页需要的额外字段 (标本/患者/申请)
     */
    private void enrichReportExtra(ReportVO vo, Report report) {
        vo.setHospitalName("连平县人民医院");
        vo.setSource(sourceOf(vo.getVisitType()));
        try {
            String sql = "SELECT " +
                    "s.sample_barcode AS sample_barcode, s.receive_time AS receive_time, s.order_id, " +
                    "o.apply_dept, o.apply_doctor, o.clinical_diag, o.visit_id, " +
                    "v.visit_type AS visit_type, " +
                    "p.gender AS patient_gender, p.age AS patient_age " +
                    "FROM lis_sample s " +
                    "LEFT JOIN lis_lab_order o ON s.order_id = o.id " +
                    "LEFT JOIN lis_visit v ON o.visit_id = v.id " +
                    "LEFT JOIN lis_patient p ON s.patient_id = p.id " +
                    "WHERE s.id = ?";
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, report.getSampleId());
            vo.setSampleBarcode(toStringOrNull(row.get("sample_barcode")));
            vo.setSampleNo(report.getSampleId() != null ? String.valueOf(report.getSampleId()) : null);
            vo.setReceiveTime(toLocalDateTime(row.get("receive_time")));
            vo.setDepartment(firstNonNull(toStringOrNull(row.get("apply_dept"))));
            vo.setDoctor(toStringOrNull(row.get("apply_doctor")));
            vo.setClinicalDiag(toStringOrNull(row.get("clinical_diag")));
            if (vo.getVisitType() == null) vo.setVisitType(toSmallInt(row.get("visit_type")));
            vo.setSource(sourceOf(vo.getVisitType()));
            if (vo.getPatientGender() == null) vo.setPatientGender(toSmallInt(row.get("patient_gender")));
            if (vo.getPatientAge() == null) vo.setPatientAge(toInt(row.get("patient_age")));
        } catch (Exception e) {
            log.debug("enrichReportExtra 失败(忽略): sampleId={}, err={}", report.getSampleId(), e.getMessage());
        }
    }

    private String statusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "已审核";
            case 2 -> "已发布";
            case 3 -> "已修改";
            default -> "未知";
        };
    }

    private String auditStatusText(Integer s) {
        if (s == null) return "未审核";
        return switch (s) {
            case 0 -> "未审核";
            case 1 -> "自动审核";
            case 2 -> "人工审核";
            default -> "未知";
        };
    }

    private String generateReportNo(Long sampleId) {
        // report_no 列为 varchar(32), 需控制在 32 字符内。使用 时间戳(14位) + 标本号后10位 保证唯一且不超长
        String suffix = String.format("%010d", Math.abs(sampleId % 1_000_000_0000L));
        return "RPT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + suffix;
    }

    /**
     * 从标本表回填患者信息到报告。标本缺失或查询失败时不影响主流程(patient_id 缺失由调用方兜底)。
     */
    private void fillPatientInfoFromSample(Report report, Long sampleId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT patient_id, patient_name FROM lis_sample WHERE id = ?",
                    sampleId);
            if (rows != null && !rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                Number pid = row.get("patient_id") instanceof Number ? (Number) row.get("patient_id") : null;
                if (pid != null) {
                    report.setPatientId(pid.longValue());
                }
                report.setPatientName(row.get("patient_name") != null ? String.valueOf(row.get("patient_name")) : null);
            }
        } catch (Exception e) {
            log.warn("回填报告患者信息失败(忽略): sampleId={}, error={}", sampleId, e.getMessage());
        }
    }
}
