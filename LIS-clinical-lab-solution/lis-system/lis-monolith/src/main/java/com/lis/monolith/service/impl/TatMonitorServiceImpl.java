package com.lis.monolith.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.monolith.entity.Message;
import com.lis.monolith.entity.TatMonitor;
import com.lis.monolith.mapper.TatDataMapper;
import com.lis.monolith.mapper.TatMonitorMapper;
import com.lis.monolith.service.MessageService;
import com.lis.monolith.service.TatMonitorService;
import com.lis.monolith.service.TatRuleService;
import com.lis.monolith.vo.TatScanRow;
import com.lis.system.entity.SysUser;
import com.lis.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TAT 业务监控服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TatMonitorServiceImpl implements TatMonitorService {

    private final TatMonitorMapper tatMonitorMapper;
    private final TatDataMapper tatDataMapper;
    private final TatRuleService tatRuleService;
    private final MessageService messageService;
    private final SysUserMapper sysUserMapper;

    @Override
    public int runScan() {
        if (!tatRuleService.isEnabled()) {
            log.info("TAT规则未启用, 跳过扫描");
            return 0;
        }
        List<TatScanRow> rows = tatDataMapper.selectTatScanRows();
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int processed = 0;
        for (TatScanRow row : rows) {
            if (row.getSignOffTime() == null) {
                continue;
            }
            int timeout = (row.getIsUrgent() != null && row.getIsUrgent() == 1)
                    ? (row.getUrgentTimeout() != null ? row.getUrgentTimeout() : 0)
                    : (row.getNormalTimeout() != null ? row.getNormalTimeout() : 0);
            if (timeout <= 0) {
                continue;
            }
            long elapsedMin = Duration.between(row.getSignOffTime(), now).toMinutes();
            boolean overdue = elapsedMin > timeout;

            // 查已有记录 (按 标本+项目 去重)
            TatMonitor exist = tatMonitorMapper.selectOne(new LambdaQueryWrapper<TatMonitor>()
                    .eq(TatMonitor::getSampleId, row.getSampleId())
                    .eq(TatMonitor::getTestItemId, row.getTestItemId()));
            if (exist == null) {
                TatMonitor m = new TatMonitor();
                m.setSampleId(row.getSampleId());
                m.setSampleBarcode(row.getSampleBarcode());
                m.setPatientName(row.getPatientName());
                m.setTestItemId(row.getTestItemId());
                m.setTestItemName(row.getTestItemName());
                m.setSpecimenType(row.getSpecimenType());
                m.setIsUrgent(row.getIsUrgent());
                m.setSignOffTime(row.getSignOffTime());
                m.setTimeoutMinutes((int) timeout);
                m.setElapsedMinutes((int) elapsedMin);
                m.setIsOverdue(overdue ? 1 : 0);
                m.setOperatorId(row.getOperatorId());
                m.setDepartment(row.getDepartment());
                m.setNotified(0);
                m.setCreateTime(now);
                tatMonitorMapper.insert(m);
                processed++;
                if (overdue) {
                    notifyTatOverdue(m);
                }
            } else {
                exist.setElapsedMinutes((int) elapsedMin);
                exist.setIsOverdue(overdue ? 1 : 0);
                // 若已恢复(报告)由业务状态决定, 这里仅刷新耗时
                tatMonitorMapper.updateById(exist);
                if (overdue && (exist.getNotified() == null || exist.getNotified() == 0)) {
                    notifyTatOverdue(exist);
                }
            }
        }
        log.info("TAT扫描完成: 处理 {} 条, 标本-项目 {} 条", processed, rows.size());
        return processed;
    }

    private void notifyTatOverdue(TatMonitor m) {
        String title = "TAT超时提醒";
        String content = String.format("标本 %s 患者 %s 检验项目 %s 已超时 %s 分钟(时限 %s 分钟)",
                m.getSampleBarcode(), m.getPatientName(), m.getTestItemName(),
                m.getElapsedMinutes(), m.getTimeoutMinutes());
        // 优先通知操作人所在科室, 否则全员
        Long deptId = resolveDeptId(m.getOperatorId());
        if (deptId != null) {
            messageService.sendToDepartment(deptId, Message.TYPE_TAT, title, content,
                    m.getSampleId(), m.getSampleId(), m.getTestItemId());
        } else {
            messageService.broadcast(Message.TYPE_TAT, title, content,
                    m.getSampleId(), m.getSampleId(), m.getTestItemId());
        }
        m.setNotified(1);
        tatMonitorMapper.updateById(m);
    }

    private Long resolveDeptId(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            SysUser user = sysUserMapper.selectById(userId);
            return user != null ? user.getDepartmentId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public IPage<TatMonitor> page(int page, int size, Integer isOverdue, String keyword,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<TatMonitor> qw = new LambdaQueryWrapper<>();
        if (isOverdue != null) {
            qw.eq(TatMonitor::getIsOverdue, isOverdue);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(TatMonitor::getSampleBarcode, keyword)
                    .or().like(TatMonitor::getPatientName, keyword)
                    .or().like(TatMonitor::getTestItemName, keyword));
        }
        // 时间范围过滤: 按监控记录创建时间 (对应扫描当天)
        if (startTime != null) {
            qw.ge(TatMonitor::getCreateTime, startTime);
        }
        if (endTime != null) {
            qw.le(TatMonitor::getCreateTime, endTime);
        }
        qw.orderByDesc(TatMonitor::getId);
        return tatMonitorMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), qw);
    }

    @Override
    public TatMonitor getById(Long id) {
        return tatMonitorMapper.selectById(id);
    }

    @Override
    public Map<String, Object> stats(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<TatMonitor> rangeQw = new LambdaQueryWrapper<>();
        if (startTime != null) {
            rangeQw.ge(TatMonitor::getCreateTime, startTime);
        }
        if (endTime != null) {
            rangeQw.le(TatMonitor::getCreateTime, endTime);
        }
        Long total = tatMonitorMapper.selectCount(rangeQw);

        LambdaQueryWrapper<TatMonitor> overdueQw = new LambdaQueryWrapper<>();
        if (startTime != null) overdueQw.ge(TatMonitor::getCreateTime, startTime);
        if (endTime != null) overdueQw.le(TatMonitor::getCreateTime, endTime);
        overdueQw.eq(TatMonitor::getIsOverdue, 1);
        Long overdue = tatMonitorMapper.selectCount(overdueQw);

        Long notOverdue = total != null && overdue != null ? total - overdue : 0L;

        LambdaQueryWrapper<TatMonitor> pendingQw = new LambdaQueryWrapper<>();
        if (startTime != null) pendingQw.ge(TatMonitor::getCreateTime, startTime);
        if (endTime != null) pendingQw.le(TatMonitor::getCreateTime, endTime);
        pendingQw.eq(TatMonitor::getIsOverdue, 1).eq(TatMonitor::getNotified, 0);
        Long pending = tatMonitorMapper.selectCount(pendingQw);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("overdue", overdue == null ? 0 : overdue);
        result.put("notOverdue", notOverdue);
        result.put("pendingNotify", pending == null ? 0 : pending);
        result.put("enabled", tatRuleService.isEnabled());
        return result;
    }
}