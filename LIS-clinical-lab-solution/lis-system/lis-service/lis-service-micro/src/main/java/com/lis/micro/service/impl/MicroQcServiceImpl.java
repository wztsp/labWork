package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.micro.entity.MicroQcRecord;
import com.lis.micro.mapper.MicroQcRecordMapper;
import com.lis.micro.service.MicroQcService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 微生物室内质控服务实现
 */
@Service
public class MicroQcServiceImpl extends ServiceImpl<MicroQcRecordMapper, MicroQcRecord> implements MicroQcService {

    @Override
    public Page<MicroQcRecord> queryPage(long current, long size, Integer itemType, String keyword, String result) {
        LambdaQueryWrapper<MicroQcRecord> wrapper = new LambdaQueryWrapper<>();
        if (itemType != null) {
            wrapper.eq(MicroQcRecord::getItemType, itemType);
        }
        if (result != null && !result.isBlank()) {
            wrapper.eq(MicroQcRecord::getResult, result);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(MicroQcRecord::getItemName, keyword)
                    .or().like(MicroQcRecord::getQcLotNo, keyword)
                    .or().like(MicroQcRecord::getControlStrain, keyword)
                    .or().like(MicroQcRecord::getItemCode, keyword));
        }
        wrapper.orderByDesc(MicroQcRecord::getMeasureTime, MicroQcRecord::getId);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroQcRecord saveRecord(MicroQcRecord record) {
        if (record == null || record.getItemType() == null) {
            throw new BusinessException("质控类别不能为空");
        }
        if (record.getItemName() == null || record.getItemName().isBlank()) {
            throw new BusinessException("物品名称不能为空");
        }
        // 自动判定: 预期与实际结果一致即合格
        if (record.getExpectedResult() != null && record.getActualResult() != null) {
            record.setResult(record.getExpectedResult().trim().equalsIgnoreCase(record.getActualResult().trim())
                    ? "PASS" : "FAIL");
        } else if (record.getResult() == null || record.getResult().isBlank()) {
            record.setResult("PASS");
        }
        LocalDateTime now = LocalDateTime.now();
        if (record.getId() == null || record.getId() == 0L) {
            if (record.getMeasureTime() == null) {
                record.setMeasureTime(now);
            }
            record.setCreateTime(now);
            this.save(record);
        } else {
            this.updateById(record);
        }
        return record;
    }

    @Override
    public Map<String, Object> stats(Integer itemType) {
        LambdaQueryWrapper<MicroQcRecord> wrapper = new LambdaQueryWrapper<>();
        if (itemType != null) {
            wrapper.eq(MicroQcRecord::getItemType, itemType);
        }
        List<MicroQcRecord> records = this.list(wrapper);
        int total = records.size();
        int pass = (int) records.stream().filter(r -> "PASS".equalsIgnoreCase(r.getResult())).count();
        int fail = total - pass;
        double passRate = total == 0 ? 0.0 : (double) pass * 100.0 / (double) total;
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pass", pass);
        stats.put("fail", fail);
        stats.put("passRate", Math.round(passRate * 100.0) / 100.0);
        return stats;
    }
}