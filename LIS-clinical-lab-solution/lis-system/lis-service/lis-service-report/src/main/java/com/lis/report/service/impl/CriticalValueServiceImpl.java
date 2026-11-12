package com.lis.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.report.dto.CriticalRecordDTO;
import com.lis.report.entity.CriticalRecord;
import com.lis.report.entity.Result;
import com.lis.report.mapper.CriticalRecordMapper;
import com.lis.report.mapper.ResultMapper;
import com.lis.report.service.CriticalValueService;
import com.lis.report.vo.CriticalRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 危急值服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CriticalValueServiceImpl implements CriticalValueService {

    private final ResultMapper resultMapper;
    private final CriticalRecordMapper criticalRecordMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${mq.exchange:lis.exchange}")
    private String exchange;
    @Value("${mq.critical.alert-routing-key:critical.alert}")
    private String criticalRoutingKey;
    @Value("${report.critical.confirm-timeout-seconds:1800}")
    private long confirmTimeoutSeconds;

    private static final String CONFIRM_KEY_PREFIX = "lis:critical:confirm:";

    @Override
    public boolean checkCritical(Long resultId) {
        Result r = resultMapper.selectById(resultId);
        if (r == null) {
            throw new BusinessException("结果不存在: " + resultId);
        }
        return r.getIsCritical() != null && r.getIsCritical() == 1;
    }

    @Override
    public boolean alertCritical(Long resultId) {
        Result r = resultMapper.selectById(resultId);
        if (r == null) {
            throw new BusinessException("结果不存在: " + resultId);
        }
        if (r.getIsCritical() == null || r.getIsCritical() != 1) {
            throw new BusinessException("结果非危急值，无需通知: " + resultId);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("resultId", resultId);
        payload.put("sampleId", r.getSampleId());
        payload.put("testItemId", r.getTestItemId());
        payload.put("resultValue", r.getResultValue());
        payload.put("unit", r.getUnit());
        payload.put("alertTime", LocalDateTime.now().toString());

        rabbitTemplate.convertAndSend(exchange, criticalRoutingKey, payload);
        // 设置确认超时
        stringRedisTemplate.opsForValue().set(
                CONFIRM_KEY_PREFIX + resultId, "PENDING",
                confirmTimeoutSeconds, TimeUnit.SECONDS);
        log.warn("危急值已发送通知: resultId={}, value={}", resultId, r.getResultValue());
        return true;
    }

    @Override
    public List<CriticalRecordVO> getCriticalList(String startDate, String endDate) {
        return criticalRecordMapper.selectCriticalList(startDate, endDate);
    }

    @Override
    public boolean confirmCritical(Long resultId, Long confirmerId) {
        Result r = resultMapper.selectById(resultId);
        if (r == null) {
            throw new BusinessException("结果不存在: " + resultId);
        }
        // 标记已确认
        stringRedisTemplate.opsForValue().set(
                CONFIRM_KEY_PREFIX + resultId, "CONFIRMED:" + confirmerId,
                confirmTimeoutSeconds, TimeUnit.SECONDS);
        log.info("危急值已被确认: resultId={}, confirmerId={}", resultId, confirmerId);
        return true;
    }

    @Override
    public boolean notifyCritical(Long resultId, CriticalRecordDTO dto) {
        Result r = resultMapper.selectById(resultId);
        if (r == null) {
            throw new BusinessException("结果不存在: " + resultId);
        }
        if (r.getIsCritical() == null || r.getIsCritical() != 1) {
            throw new BusinessException("结果非危急值，无需通知: " + resultId);
        }
        LocalDateTime now = LocalDateTime.now();
        CriticalRecord record = criticalRecordMapper.selectOne(
                new LambdaQueryWrapper<CriticalRecord>().eq(CriticalRecord::getResultId, resultId));
        boolean isNew = record == null;
        if (isNew) {
            record = new CriticalRecord();
            record.setResultId(resultId);
            record.setSampleId(r.getSampleId());
            record.setTestItemId(r.getTestItemId());
            record.setResultValue(r.getResultValue());
            record.setUnit(r.getUnit());
            record.setCreateTime(now);
            // 填充冗余的患者/项目/危急值范围
            CriticalRecordVO detail = criticalRecordMapper.selectCriticalDetail(resultId);
            if (detail != null) {
                record.setPatientName(detail.getPatientName());
                record.setTestItemName(detail.getTestItemName());
                record.setCriticalLow(detail.getCriticalLow());
                record.setCriticalHigh(detail.getCriticalHigh());
            }
        }
        record.setNotifier(dto.getNotifier());
        record.setNotifyTime(dto.getNotifyTime() != null ? dto.getNotifyTime() : now);
        record.setSender(dto.getSender());
        record.setSendTime(dto.getSendTime() != null ? dto.getSendTime() : now);
        record.setRemark(dto.getRemark());
        record.setStatus(CriticalRecord.STATUS_NOTIFIED);
        record.setUpdateTime(now);
        if (isNew) {
            criticalRecordMapper.insert(record);
        } else {
            criticalRecordMapper.updateById(record);
        }
        // 同步发送 MQ 危急值通知 (失败不影响主流程)
        try {
            alertCritical(resultId);
        } catch (Exception e) {
            log.warn("危急值通知 MQ 发送失败(不影响记录保存): resultId={}, err={}", resultId, e.getMessage());
        }
        log.warn("危急值已通知: resultId={}, notifier={}, sender={}", resultId, record.getNotifier(), record.getSender());
        return true;
    }

    @Override
    public boolean cancelNotify(Long resultId) {
        CriticalRecord record = criticalRecordMapper.selectOne(
                new LambdaQueryWrapper<CriticalRecord>().eq(CriticalRecord::getResultId, resultId));
        if (record == null) {
            throw new BusinessException("该危急值尚未通知，无法取消: " + resultId);
        }
        if (record.getStatus() != null && record.getStatus() == CriticalRecord.STATUS_CANCELLED) {
            throw new BusinessException("该危急值通知已取消，请勿重复操作");
        }
        record.setStatus(CriticalRecord.STATUS_CANCELLED);
        record.setUpdateTime(LocalDateTime.now());
        criticalRecordMapper.updateById(record);
        log.info("危急值通知已取消: resultId={}", resultId);
        return true;
    }

    @Override
    public boolean updateRecord(Long resultId, CriticalRecordDTO dto) {
        CriticalRecord record = criticalRecordMapper.selectOne(
                new LambdaQueryWrapper<CriticalRecord>().eq(CriticalRecord::getResultId, resultId));
        if (record == null) {
            throw new BusinessException("该危急值尚无处理记录，请先进行通知: " + resultId);
        }
        if (dto.getNotifier() != null) record.setNotifier(dto.getNotifier());
        if (dto.getNotifyTime() != null) record.setNotifyTime(dto.getNotifyTime());
        if (dto.getSender() != null) record.setSender(dto.getSender());
        if (dto.getSendTime() != null) record.setSendTime(dto.getSendTime());
        if (dto.getRemark() != null) record.setRemark(dto.getRemark());
        record.setUpdateTime(LocalDateTime.now());
        criticalRecordMapper.updateById(record);
        log.info("危急值处理记录已修改: resultId={}", resultId);
        return true;
    }

    @Override
    public Map<String, Object> getStats(String startDate, String endDate) {
        LocalDateTime startAt = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime endAt = LocalDateTime.parse(endDate + "T00:00:00").plusDays(1);

        // 时间范围内的危急值结果 (以结果创建时间统计)
        LambdaQueryWrapper<Result> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Result::getIsCritical, 1)
                .ge(Result::getCreateTime, startAt)
                .lt(Result::getCreateTime, endAt);
        List<Result> criticalResults = resultMapper.selectList(wrapper);
        long total = criticalResults.size();

        // 确认数: 复用结果审核状态作为确认标志 (auditStatus > 0 视为已确认)
        // 同时校验 Redis 中确认标记是否存在
        long confirmed = 0;
        long totalConfirmSeconds = 0L;
        for (Result r : criticalResults) {
            // Redis 不可用时降级处理: 仅依赖审核状态确认, 不阻断统计
            String confirmValue = null;
            try {
                confirmValue = stringRedisTemplate.opsForValue()
                        .get(CONFIRM_KEY_PREFIX + r.getId());
            } catch (Exception e) {
                log.debug("Redis 不可用, 危急值确认标记降级为审核状态: resultId={}, err={}",
                        r.getId(), e.getMessage());
            }
            boolean auditConfirmed = r.getAuditStatus() != null && r.getAuditStatus() > 0
                    && r.getAuditTime() != null;
            if (confirmValue != null && confirmValue.startsWith("CONFIRMED")) {
                confirmed++;
                // Redis 中无确认时间戳, 使用审核时间作为近似估算
                if (r.getAuditTime() != null && r.getCreateTime() != null) {
                    totalConfirmSeconds += Duration.between(r.getCreateTime(), r.getAuditTime()).getSeconds();
                }
            } else if (auditConfirmed) {
                confirmed++;
                totalConfirmSeconds += Duration.between(r.getCreateTime(), r.getAuditTime()).getSeconds();
            }
        }
        double confirmRate = total == 0 ? 0.0 : (confirmed * 100.0) / total;
        double avgConfirmSeconds = confirmed == 0 ? 0.0 : (double) totalConfirmSeconds / confirmed;

        Map<String, Object> stats = new HashMap<>();
        stats.put("startDate", startDate);
        stats.put("endDate", endDate);
        stats.put("total", total);
        stats.put("confirmed", confirmed);
        stats.put("unconfirmed", total - confirmed);
        stats.put("confirmRate", Math.round(confirmRate * 100) / 100.0);
        stats.put("avgConfirmSeconds", Math.round(avgConfirmSeconds));
        stats.put("avgConfirmMinutes", Math.round(avgConfirmSeconds / 60.0));
        log.info("危急值统计: {} ~ {}, total={}, confirmed={}, confirmRate={}%",
                startDate, endDate, total, confirmed, Math.round(confirmRate * 100) / 100.0);
        return stats;
    }
}