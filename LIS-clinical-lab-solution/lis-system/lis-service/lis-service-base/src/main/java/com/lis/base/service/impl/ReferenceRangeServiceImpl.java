package com.lis.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.base.entity.ReferenceRange;
import com.lis.base.mapper.ReferenceRangeMapper;
import com.lis.base.service.ReferenceRangeService;
import com.lis.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 参考范围 Service 实现
 */
@Service
@RequiredArgsConstructor
public class ReferenceRangeServiceImpl extends ServiceImpl<ReferenceRangeMapper, ReferenceRange> implements ReferenceRangeService {

    @Override
    public Page<ReferenceRange> queryPage(long current, long size, Long testItemId, String specimenType, Long instrumentId) {
        // 使用自定义查询, JOIN lis_test_item 获取指标名称
        IPage<ReferenceRange> page = new Page<>(current, size);
        IPage<ReferenceRange> result = baseMapper.selectPageWithItemName(page, testItemId, specimenType, instrumentId);
        Page<ReferenceRange> pageResult = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReferenceRange create(ReferenceRange referenceRange) {
        this.save(referenceRange);
        return referenceRange;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReferenceRange update(ReferenceRange referenceRange) {
        if (referenceRange.getId() == null) {
            throw new BusinessException("参考范围ID不能为空");
        }
        this.updateById(referenceRange);
        return referenceRange;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
    }

    @Override
    public List<ReferenceRange> listByItemId(Long testItemId) {
        LambdaQueryWrapper<ReferenceRange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReferenceRange::getTestItemId, testItemId);
        return this.list(wrapper);
    }

    @Override
    public ReferenceRange findMatch(Long testItemId, Integer gender, BigDecimal age) {
        return findMatch(testItemId, gender, age, null);
    }

    @Override
    public ReferenceRange findMatch(Long testItemId, Integer gender, BigDecimal age, String conditionRemark) {
        // 1. 优先匹配 conditionRemark 完全相同的记录
        if (conditionRemark != null && !conditionRemark.trim().isEmpty()) {
            LambdaQueryWrapper<ReferenceRange> wrapper = buildMatchWrapper(testItemId, gender, age);
            wrapper.eq(ReferenceRange::getConditionRemark, conditionRemark);
            wrapper.last("LIMIT 1");
            ReferenceRange result = this.getOne(wrapper);
            if (result != null) {
                return result;
            }
        }

        // 2. 回退: 按 testItemId + gender + age 匹配, 不限制 conditionRemark
        // (性别/年龄专属参考范围, 如"男性-成人", 其 conditionRemark 非空, 不应被排除)
        List<ReferenceRange> candidates = listCandidates(testItemId, gender, age);
        if (candidates.isEmpty()) {
            return null;
        }
        // 优先级: 精确性别 > 通用(0) > 未设置性别(NULL)
        if (gender != null) {
            for (ReferenceRange rr : candidates) {
                if (gender.equals(rr.getGender())) {
                    return rr;
                }
            }
        }
        for (ReferenceRange rr : candidates) {
            if (rr.getGender() != null && rr.getGender() == 0) {
                return rr;
            }
        }
        return candidates.get(0);
    }

    private List<ReferenceRange> listCandidates(Long testItemId, Integer gender, BigDecimal age) {
        LambdaQueryWrapper<ReferenceRange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReferenceRange::getTestItemId, testItemId);
        wrapper.and(w -> {
            if (gender != null) {
                w.eq(ReferenceRange::getGender, gender)
                        .or().eq(ReferenceRange::getGender, 0);
            }
            w.or().isNull(ReferenceRange::getGender);
        });
        if (age != null) {
            wrapper.and(w -> w.le(ReferenceRange::getAgeMin, age)
                    .ge(ReferenceRange::getAgeMax, age));
        }
        return this.list(wrapper);
    }

    /**
     * 构建基础匹配条件: testItemId + gender + age
     */
    private LambdaQueryWrapper<ReferenceRange> buildMatchWrapper(Long testItemId, Integer gender, BigDecimal age) {
        LambdaQueryWrapper<ReferenceRange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReferenceRange::getTestItemId, testItemId);
        // 优先匹配性别, 其次匹配通用(0), 再匹配未设置性别(NULL)
        wrapper.and(w -> w.eq(ReferenceRange::getGender, gender)
                .or().eq(ReferenceRange::getGender, 0)
                .or().isNull(ReferenceRange::getGender));
        if (age != null) {
            wrapper.and(w -> w.le(ReferenceRange::getAgeMin, age)
                    .ge(ReferenceRange::getAgeMax, age));
        }
        return wrapper;
    }
}
