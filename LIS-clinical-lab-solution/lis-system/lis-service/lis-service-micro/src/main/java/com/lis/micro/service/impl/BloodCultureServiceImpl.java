package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.micro.entity.BloodCulture;
import com.lis.micro.mapper.BloodCultureMapper;
import com.lis.micro.service.BloodCultureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 血培养服务实现
 */
@Service
public class BloodCultureServiceImpl extends ServiceImpl<BloodCultureMapper, BloodCulture> implements BloodCultureService {

    @Override
    public Page<BloodCulture> queryPage(long current, long size, Long sampleId, Integer status) {
        LambdaQueryWrapper<BloodCulture> wrapper = new LambdaQueryWrapper<>();
        if (sampleId != null) {
            wrapper.eq(BloodCulture::getSampleId, sampleId);
        }
        if (status != null) {
            wrapper.eq(BloodCulture::getStatus, status);
        }
        wrapper.orderByDesc(BloodCulture::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public BloodCulture getBySampleId(Long sampleId) {
        List<BloodCulture> list = this.list(new LambdaQueryWrapper<BloodCulture>()
                .eq(BloodCulture::getSampleId, sampleId)
                .orderByDesc(BloodCulture::getCreateTime));
        return CollectionUtils.isEmpty(list) ? null : list.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BloodCulture inoculate(BloodCulture bloodCulture) {
        if (bloodCulture.getSampleId() == null) {
            throw new BusinessException("标本ID不能为空");
        }
        if (bloodCulture.getInoculationTime() == null) {
            bloodCulture.setInoculationTime(LocalDateTime.now());
        }
        bloodCulture.setStatus(0);
        bloodCulture.setCreateTime(LocalDateTime.now());
        this.save(bloodCulture);
        return bloodCulture;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BloodCulture receiveAlarm(Long id, LocalDateTime alarmTime, Long operatorId) {
        BloodCulture bc = this.getById(id);
        if (bc == null) {
            throw new BusinessException("血培养记录不存在: " + id);
        }
        if (bc.getStatus() != 0) {
            throw new BusinessException("当前血培养状态不允许接收报警");
        }
        bc.setAlarmTime(alarmTime != null ? alarmTime : LocalDateTime.now());
        bc.setStatus(1);
        bc.setOperatorId(operatorId);
        // 计算 TTP 小时数
        if (bc.getInoculationTime() != null) {
            long minutes = Duration.between(bc.getInoculationTime(), bc.getAlarmTime()).toMinutes();
            bc.setTtpHours(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        }
        this.updateById(bc);
        return bc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BloodCulture reportFinal(Long id, String finalResult, Long operatorId) {
        BloodCulture bc = this.getById(id);
        if (bc == null) {
            throw new BusinessException("血培养记录不存在: " + id);
        }
        bc.setFinalResult(finalResult);
        bc.setFinalTime(LocalDateTime.now());
        bc.setOperatorId(operatorId);
        // POSITIVE=1, NEGATIVE=2, CONTAMINATED=3
        if ("POSITIVE".equalsIgnoreCase(finalResult)) {
            bc.setStatus(1);
        } else if ("NEGATIVE".equalsIgnoreCase(finalResult)) {
            bc.setStatus(2);
        } else if ("CONTAMINATED".equalsIgnoreCase(finalResult)) {
            bc.setStatus(3);
        }
        this.updateById(bc);
        return bc;
    }

    @Override
    public List<BloodCulture> listCulturing() {
        return this.list(new LambdaQueryWrapper<BloodCulture>()
                .eq(BloodCulture::getStatus, 0)
                .orderByAsc(BloodCulture::getInoculationTime));
    }
}
