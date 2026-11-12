package com.lis.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.PageResult;
import com.lis.qc.entity.OccApproval;
import com.lis.qc.entity.OccEvent;
import com.lis.qc.entity.OccStep;
import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.mapper.OccApprovalMapper;
import com.lis.qc.mapper.OccEventMapper;
import com.lis.qc.mapper.OccStepMapper;
import com.lis.qc.service.OccService;
import com.lis.qc.service.rule.QCResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 失控报告(OCC)服务实现——七步法/根因强制校验/三级审批。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OccServiceImpl implements OccService {

    private static final String[] STEP_NAMES = {
            "紧急暂停", "全面回顾", "保留现场", "原因排查", "纠正措施", "验证在控", "记录上报"
    };
    private static final String[] LEVEL_NAMES = {"专业组组长", "质控负责人", "科主任"};

    private final OccEventMapper eventMapper;
    private final OccStepMapper stepMapper;
    private final OccApprovalMapper approvalMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 查询 ====================

    @Override
    public PageResult<OccEvent> page(long current, long size, String status, String keyword, String range) {
        LambdaQueryWrapper<OccEvent> qw = new LambdaQueryWrapper<OccEvent>()
                .eq(status != null && !status.isBlank(), OccEvent::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(OccEvent::getOccNo, keyword)
                        .or().like(OccEvent::getProjectName, keyword))
                .orderByDesc(OccEvent::getFoundAt);
        if (range != null) {
            LocalDate today = LocalDate.now();
            switch (range) {
                case "30D": qw.ge(OccEvent::getFoundAt, today.minusDays(30).atStartOfDay()); break;
                case "90D": qw.ge(OccEvent::getFoundAt, today.minusDays(90).atStartOfDay()); break;
                case "YEAR": qw.ge(OccEvent::getFoundAt, today.withDayOfYear(1).atStartOfDay()); break;
                default: break;
            }
        }
        Page<OccEvent> page = eventMapper.selectPage(new Page<>(current, size), qw);
        page.getRecords().forEach(this::attachChildren);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public OccEvent detail(Long id) {
        OccEvent event = requireEvent(id);
        attachChildren(event);
        return event;
    }

    private void attachChildren(OccEvent e) {
        e.setSteps(stepMapper.selectList(new LambdaQueryWrapper<OccStep>()
                .eq(OccStep::getOccId, e.getId()).orderByAsc(OccStep::getStepNo)));
        e.setApprovals(approvalMapper.selectList(new LambdaQueryWrapper<OccApproval>()
                .eq(OccApproval::getOccId, e.getId()).orderByAsc(OccApproval::getLevelNo)));
    }

    // ==================== 建档 ====================

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public OccEvent create(OccEvent event, String operator) {
        OccEvent ev = event == null ? new OccEvent() : event;
        ev.setId(null);
        if (ev.getOccNo() == null || ev.getOccNo().isBlank()) {
            ev.setOccNo(generateOccNo());
        }
        if (ev.getStatus() == null) ev.setStatus("PENDING");
        if (ev.getCurrentStep() == null) ev.setCurrentStep(1);
        if (ev.getFoundAt() == null) ev.setFoundAt(LocalDateTime.now());
        if (ev.getFoundBy() == null || ev.getFoundBy().isBlank()) ev.setFoundBy(operator);
        ev.setCreateTime(LocalDateTime.now());
        ev.setUpdateTime(LocalDateTime.now());
        ev.setDeleted(0);
        eventMapper.insert(ev);
        initSteps(ev);
        initApprovals(ev);
        attachChildren(ev);
        return ev;
    }

    /**
     * 由失控判定自动产生失控报告(或复用未关闭事件)。
     * 失控数据与失控报告通过 lot_id 关联; 该批号已存在未关闭事件时复用并回填规则/质控值。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public OccEvent createFromReject(QCLot lot, QCRecord record, QCResult result, String operator) {
        if (lot == null || record == null || result == null) {
            throw new BusinessException("失控判定数据不完整, 无法产生失控报告");
        }
        String rule = result.getRuleName();
        // 查找该批号未关闭事件 (PENDING/INVESTIGATING/CORRECTED 均视为在处置中)
        OccEvent exist = eventMapper.selectOne(new LambdaQueryWrapper<OccEvent>()
                .eq(OccEvent::getLotId, lot.getId())
                .in(OccEvent::getStatus, "PENDING", "INVESTIGATING", "CORRECTED")
                .orderByDesc(OccEvent::getFoundAt)
                .last("LIMIT 1"));
        if (exist != null) {
            // 复用: 合并触发规则 (去重), 回填靶值/SD (避免编辑后为空), 记录最新发现时间
            if (rule != null && !rule.isBlank()
                    && (exist.getTriggerRules() == null || !exist.getTriggerRules().contains(rule))) {
                exist.setTriggerRules(exist.getTriggerRules() == null || exist.getTriggerRules().isBlank()
                        ? rule : exist.getTriggerRules() + "," + rule);
            }
            if (exist.getTargetValue() == null) exist.setTargetValue(lot.getTargetValue());
            if (exist.getSd() == null) exist.setSd(lot.getSd());
            exist.setFoundAt(record.getMeasureTime() != null ? record.getMeasureTime() : LocalDateTime.now());
            exist.setUpdateTime(LocalDateTime.now());
            // 追加本次失控质控值快照
            String val = record.getMeasureValue() == null ? null : record.getMeasureValue().stripTrailingZeros().toPlainString();
            if (val != null && !val.isBlank()) {
                exist.setQcValues(exist.getQcValues() == null || exist.getQcValues().isBlank()
                        ? val : exist.getQcValues() + "," + val);
            }
            eventMapper.updateById(exist);
            attachChildren(exist);
            return exist;
        }
        // 新建事件
        OccEvent ev = new OccEvent();
        ev.setOccNo(generateOccNo());
        ev.setLotId(lot.getId());
        ev.setTriggerRules(rule);
        ev.setTargetValue(lot.getTargetValue());
        ev.setSd(lot.getSd());
        ev.setFoundAt(record.getMeasureTime() != null ? record.getMeasureTime() : LocalDateTime.now());
        ev.setFoundBy(operator);
        ev.setStatus("PENDING");
        ev.setCurrentStep(1);
        ev.setErrorType("SYSTEM");
        ev.setQcValues(record.getMeasureValue() == null ? null
                : record.getMeasureValue().stripTrailingZeros().toPlainString());
        // 关联检验项目信息 (project_code / project_name / group_name)
        fillProjectInfo(ev, lot.getTestItemId());
        ev.setCreateTime(LocalDateTime.now());
        ev.setUpdateTime(LocalDateTime.now());
        ev.setDeleted(0);
        eventMapper.insert(ev);
        initSteps(ev);
        initApprovals(ev);
        attachChildren(ev);
        return ev;
    }

    /** 按检验项目ID回填失控报告的 项目编码/名称/专业组 */
    private void fillProjectInfo(OccEvent ev, Long testItemId) {
        if (testItemId == null) return;
        try {
            List<Object[]> rows = jdbcTemplate.query(
                    "SELECT item_code, item_name, category FROM lis_test_item WHERE id = ?",
                    rs -> {
                        java.util.List<Object[]> list = new ArrayList<>();
                        while (rs.next()) {
                            list.add(new Object[]{rs.getString("item_code"), rs.getString("item_name"), rs.getString("category")});
                        }
                        return list;
                    }, testItemId);
            if (!rows.isEmpty()) {
                Object[] r = rows.get(0);
                ev.setProjectCode((String) r[0]);
                ev.setProjectName((String) r[1]);
                ev.setGroupName((String) r[2]);
            }
        } catch (Exception e) {
            log.warn("回填失控报告检验项目信息失败: testItemId={}, msg={}", testItemId, e.getMessage());
        }
    }

    private String generateOccNo() {
        String prefix = "OCC-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        List<OccEvent> today = eventMapper.selectList(new LambdaQueryWrapper<OccEvent>().likeRight(OccEvent::getOccNo, prefix));
        int max = 0;
        for (OccEvent e : today) {
            String tail = e.getOccNo() == null ? "" : e.getOccNo().substring(prefix.length());
            try { max = Math.max(max, Integer.parseInt(tail)); } catch (NumberFormatException ignored) { }
        }
        return prefix + String.format("%03d", max + 1);
    }

    private void initSteps(OccEvent ev) {
        for (int i = 0; i < STEP_NAMES.length; i++) {
            OccStep s = new OccStep();
            s.setOccId(ev.getId());
            s.setStepNo(i + 1);
            s.setName(STEP_NAMES[i]);
            s.setStatus(i == 0 ? "CURRENT" : "PENDING");
            s.setChecklist(defaultChecklist(i + 1));
            stepMapper.insert(s);
        }
    }

    private void initApprovals(OccEvent ev) {
        for (int i = 0; i < LEVEL_NAMES.length; i++) {
            OccApproval a = new OccApproval();
            a.setOccId(ev.getId());
            a.setLevelNo(i + 1);
            a.setLevelName(LEVEL_NAMES[i]);
            a.setStatus("PENDING");
            approvalMapper.insert(a);
        }
    }

    /** 各步骤默认排查项 */
    private String defaultChecklist(int stepNo) {
        String[][] defs = {
            {"已停止相关项目检测", "已张贴“失控待处理”标识", "已通知当班人员与专业组组长"},
            {"已核对质控品与批号效期", "已核对仪器状态与校准", "已核对操作流程", "已核对数据录入"},
            {"已保留质控数据", "已保留原瓶质控品", "已记录试剂批号与校准记录"},
            {"已排除质控品因素", "已排除试剂因素", "已排除校准品因素", "已排除仪器因素", "已排除环境/操作因素"},
            {"已更换质控品/试剂", "已规范操作流程(SOP)"},
            {"新条件连续≥2批次在控", "已恢复相关项目检测"},
            {"已填写失控报告", "已评估既往结果影响", "已制定预防措施", "已提交复核"}
        };
        String[] arr = defs[Math.max(0, Math.min(stepNo, 7) - 1)];
        List<Map<String, Object>> list = new ArrayList<>();
        for (String t : arr) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", t);
            m.put("done", false);
            list.add(m);
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ==================== 步骤更新 ====================

    @Override
    @Transactional
    public OccEvent updateStep(Long id, Integer stepNo, Map<String, Object> payload, String operator) {
        OccEvent ev = requireEvent(id);
        OccStep step = findStep(ev.getId(), stepNo);
        if (payload.get("checklist") != null) {
            step.setChecklist(toJson(payload.get("checklist")));
        }
        if (payload.get("conclusion") != null) step.setConclusion(String.valueOf(payload.get("conclusion")));
        step.setOperator(operator);
        if (stepNo <= 3 && "PENDING".equals(ev.getStatus())) ev.setStatus("INVESTIGATING");

        boolean allDone = isAllDone(step.getChecklist());
        if (allDone && !"DONE".equals(step.getStatus())) {
            step.setStatus("DONE");
            step.setOperatedAt(LocalDateTime.now());
            advance(ev, stepNo);
        } else if ("DONE".equals(step.getStatus())) {
            step.setOperatedAt(LocalDateTime.now());
        }
        stepMapper.updateById(step);
        if (stepNo == 6 && "DONE".equals(step.getStatus())) ev.setStatus("CORRECTED");
        if (stepNo == 7 && "DONE".equals(step.getStatus()) && "CORRECTED".equals(ev.getStatus())) {
            // 保持 CORRECTED, 待审批
        }
        ev.setUpdateTime(LocalDateTime.now());
        eventMapper.updateById(ev);
        return detail(id);
    }

    private void advance(OccEvent ev, int doneStep) {
        if (doneStep < STEP_NAMES.length) {
            int next = doneStep + 1;
            OccStep nextStep = findStep(ev.getId(), next);
            nextStep.setStatus("CURRENT");
            stepMapper.updateById(nextStep);
            ev.setCurrentStep(next);
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private boolean isAllDone(String checklistJson) {
        if (checklistJson == null || checklistJson.isBlank()) return false;
        try {
            List<Map<String, Object>> list = objectMapper.readValue(checklistJson, new TypeReference<List<Map<String, Object>>>() {});
            if (list.isEmpty()) return false;
            for (Map<String, Object> m : list) {
                if (!Boolean.TRUE.equals(m.get("done"))) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 根因确认(强制校验) ====================

    @Override
    @Transactional
    public OccEvent confirmRootCause(Long id, Map<String, Object> payload, String operator) {
        OccEvent ev = requireEvent(id);
        // 前置: 步骤1-4 必须全部完成
        for (int no = 1; no <= 4; no++) {
            OccStep s = findStep(ev.getId(), no);
            if (!"DONE".equals(s.getStatus())) {
                throw new BusinessException("尚有排查步骤未完成，请完成步骤 " + no + " 后，再确认根因判定");
            }
        }
        ev.setRootCauseCategory(payload == null ? null : String.valueOf(payload.get("category")));
        ev.setRootCauseDesc(payload == null ? null : String.valueOf(payload.get("description")));
        ev.setRootCauseBy(operator);
        ev.setRootCauseAt(LocalDateTime.now());
        if (!"CORRECTED".equals(ev.getStatus())) ev.setStatus("INVESTIGATING");
        ev.setUpdateTime(LocalDateTime.now());
        eventMapper.updateById(ev);
        return detail(id);
    }

    // ==================== 审批 ====================

    @Override
    @Transactional
    public OccEvent approve(Long id, String approver, Integer levelNo, boolean approved, String comment) {
        OccEvent ev = requireEvent(id);
        OccApproval ap = approvalMapper.selectOne(new LambdaQueryWrapper<OccApproval>()
                .eq(OccApproval::getOccId, id).eq(OccApproval::getLevelNo, levelNo));
        if (ap == null) throw new BusinessException("审批环节不存在: level " + levelNo);
        ap.setStatus(approved ? "APPROVED" : "REJECTED");
        ap.setApprover(approver);
        ap.setComment(comment);
        ap.setApprovedAt(LocalDateTime.now());
        approvalMapper.updateById(ap);

        if (approved && allApproved(id)) {
            ev.setStatus("CLOSED");
        }
        ev.setUpdateTime(LocalDateTime.now());
        eventMapper.updateById(ev);
        return detail(id);
    }

    private boolean allApproved(Long id) {
        List<OccApproval> list = approvalMapper.selectList(new LambdaQueryWrapper<OccApproval>().eq(OccApproval::getOccId, id));
        return !list.isEmpty() && list.stream().allMatch(a -> "APPROVED".equals(a.getStatus()));
    }

    @Override
    public List<OccEvent> listAllEvents() {
        return eventMapper.selectList(new LambdaQueryWrapper<OccEvent>().orderByDesc(OccEvent::getFoundAt));
    }

    // ==================== 工具 ====================

    private OccEvent requireEvent(Long id) {
        OccEvent ev = eventMapper.selectById(id);
        if (ev == null) throw new BusinessException("失控事件不存在: " + id);
        return ev;
    }

    private OccStep findStep(Long occId, int stepNo) {
        OccStep s = stepMapper.selectOne(new LambdaQueryWrapper<OccStep>()
                .eq(OccStep::getOccId, occId).eq(OccStep::getStepNo, stepNo));
        if (s == null) throw new BusinessException("步骤不存在: " + stepNo);
        return s;
    }
}