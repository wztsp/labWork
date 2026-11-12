package com.lis.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.PageResult;
import com.lis.qc.entity.EqaRecord;
import com.lis.qc.mapper.EqaRecordMapper;
import com.lis.qc.service.EqaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 室间质控 (EQA) 服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EqaServiceImpl implements EqaService {

    private final EqaRecordMapper eqaRecordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqaRecord submitEqa(EqaRecord record) {
        if (record == null) {
            throw new BusinessException("EQA 数据不能为空");
        }
        if (record.getEqaBatchNo() == null || record.getEqaBatchNo().isBlank()) {
            throw new BusinessException("EQA 批号不能为空");
        }
        if (record.getTargetValue() == null || record.getMeasureValue() == null) {
            throw new BusinessException("靶值与实测值不能为空");
        }
        record.setCreateTime(LocalDateTime.now());
        if (record.getMeasureTime() == null) {
            record.setMeasureTime(LocalDateTime.now());
        }
        eqaRecordMapper.insert(record);
        log.info("EQA 数据已提交: id={}, batchNo={}, measureValue={}",
                record.getId(), record.getEqaBatchNo(), record.getMeasureValue());
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSubmitEqa(List<EqaRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        for (EqaRecord record : records) {
            if (record == null) {
                continue;
            }
            if (record.getEqaBatchNo() == null || record.getEqaBatchNo().isBlank()) {
                throw new BusinessException("EQA 批号不能为空");
            }
            if (record.getTargetValue() == null || record.getMeasureValue() == null) {
                throw new BusinessException("靶值与实测值不能为空");
            }
            record.setId(null);
            record.setCreateTime(now);
            if (record.getMeasureTime() == null) {
                record.setMeasureTime(now);
            }
            eqaRecordMapper.insert(record);
            success++;
        }
        log.info("批量提交 EQA 数据完成: 共 {} 条", success);
        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqaRecord calculateScore(Long id) {
        EqaRecord record = eqaRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("EQA 记录不存在: " + id);
        }
        if (record.getTargetValue() == null || record.getMeasureValue() == null) {
            throw new BusinessException("靶值或实测值为空，无法计算得分");
        }

        // 偏移度评估: 偏移度(%) = (实测值 - 靶值) / 靶值 * 100
        BigDecimal target = record.getTargetValue();
        BigDecimal measure = record.getMeasureValue();
        if (target.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("靶值为 0，无法计算偏移度");
        }
        BigDecimal biasPercent = measure.subtract(target)
                .multiply(BigDecimal.valueOf(100))
                .divide(target, 4, RoundingMode.HALF_UP)
                .abs();

        // 得分: 偏移度越小得分越高。允许偏移 20% 时记 0 分。
        BigDecimal maxBias = BigDecimal.valueOf(20);
        BigDecimal score;
        if (biasPercent.compareTo(maxBias) >= 0) {
            score = BigDecimal.ZERO;
        } else {
            score = BigDecimal.valueOf(100)
                    .subtract(biasPercent.multiply(BigDecimal.valueOf(5)))
                    .max(BigDecimal.ZERO);
        }
        score = score.setScale(2, RoundingMode.HALF_UP);
        record.setScore(score);

        // CV (变异系数): 若已有 sd，按 sd / target * 100 计算
        if (record.getSd() != null && target.compareTo(BigDecimal.ZERO) != 0) {
            record.setCv(record.getSd()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(target, 2, RoundingMode.HALF_UP));
        }

        // 等级: A(>=90) B(>=80) C(>=60) D(<60)
        String grade;
        boolean passed;
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) {
            grade = "A";
            passed = true;
        } else if (score.compareTo(BigDecimal.valueOf(80)) >= 0) {
            grade = "B";
            passed = true;
        } else if (score.compareTo(BigDecimal.valueOf(60)) >= 0) {
            grade = "C";
            passed = true;
        } else {
            grade = "D";
            passed = false;
        }
        record.setGrade(grade);
        record.setPassed(passed);
        record.setResultTime(LocalDateTime.now());

        eqaRecordMapper.updateById(record);
        log.info("EQA 得分计算完成: id={}, score={}, grade={}, passed={}",
                id, score, grade, passed);
        return record;
    }

    @Override
    public PageResult<EqaRecord> getEqaPage(int current, int size, String eqaBatchNo, Long testItemId,
                                            String testItemName, String instrumentName, Boolean passed) {
        LambdaQueryWrapper<EqaRecord> wrapper = new LambdaQueryWrapper<>();
        if (eqaBatchNo != null && !eqaBatchNo.isBlank()) {
            wrapper.like(EqaRecord::getEqaBatchNo, eqaBatchNo);
        }
        if (testItemId != null) {
            wrapper.eq(EqaRecord::getTestItemId, testItemId);
        }
        if (testItemName != null && !testItemName.isBlank()) {
            wrapper.like(EqaRecord::getTestItemName, testItemName);
        }
        if (instrumentName != null && !instrumentName.isBlank()) {
            wrapper.like(EqaRecord::getInstrumentName, instrumentName);
        }
        if (passed != null) {
            wrapper.eq(EqaRecord::getPassed, passed);
        }
        wrapper.orderByDesc(EqaRecord::getCreateTime);
        Page<EqaRecord> page = eqaRecordMapper.selectPage(new Page<>(current, size), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public EqaRecord getById(Long id) {
        EqaRecord record = eqaRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("EQA 记录不存在: " + id);
        }
        return record;
    }

    @Override
    public Map<String, Object> getEqaStats(String eqaBatchNo, String testItemName) {
        LambdaQueryWrapper<EqaRecord> wrapper = new LambdaQueryWrapper<>();
        if (eqaBatchNo != null && !eqaBatchNo.isBlank()) {
            wrapper.like(EqaRecord::getEqaBatchNo, eqaBatchNo);
        }
        if (testItemName != null && !testItemName.isBlank()) {
            wrapper.like(EqaRecord::getTestItemName, testItemName);
        }
        List<EqaRecord> records = eqaRecordMapper.selectList(wrapper);
        Map<String, Object> stats = new HashMap<>();
        long total = records.size();
        long passed = records.stream().filter(r -> Boolean.TRUE.equals(r.getPassed())).count();
        double passedRate = total == 0 ? 0.0 : (double) passed * 100.0 / (double) total;
        stats.put("total", total);
        stats.put("passed", passed);
        stats.put("passedRate", Math.round(passedRate * 100.0) / 100.0);
        return stats;
    }
}
