package com.lis.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.base.entity.TestTube;
import com.lis.base.mapper.TestTubeMapper;
import com.lis.base.service.TestTubeService;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检验试管 Service 实现
 */
@Service
@RequiredArgsConstructor
public class TestTubeServiceImpl extends ServiceImpl<TestTubeMapper, TestTube> implements TestTubeService {

    @Override
    public Page<TestTube> queryPage(long current, long size, String keyword, String tubeType, Integer status) {
        LambdaQueryWrapper<TestTube> wrapper = new LambdaQueryWrapper<>();
        if (StringUtil.isNotEmpty(keyword)) {
            wrapper.and(w -> w.like(TestTube::getTubeCode, keyword)
                    .or().like(TestTube::getTubeName, keyword));
        }
        if (StringUtil.isNotEmpty(tubeType)) {
            wrapper.eq(TestTube::getTubeType, tubeType);
        }
        if (status != null) {
            wrapper.eq(TestTube::getStatus, status);
        }
        wrapper.orderByAsc(TestTube::getSortOrder)
               .orderByDesc(TestTube::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public TestTube getByCode(String tubeCode) {
        LambdaQueryWrapper<TestTube> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestTube::getTubeCode, tubeCode);
        return this.getOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestTube create(TestTube testTube) {
        if (this.getByCode(testTube.getTubeCode()) != null) {
            throw new BusinessException("管编码已存在: " + testTube.getTubeCode());
        }
        if (testTube.getStatus() == null) {
            testTube.setStatus(1);
        }
        if (testTube.getSortOrder() == null) {
            testTube.setSortOrder(0);
        }
        testTube.setCreateTime(LocalDateTime.now());
        this.save(testTube);
        return testTube;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestTube update(TestTube testTube) {
        if (testTube.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(testTube);
        return testTube;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
    }

    @Override
    public List<TestTube> listAllEnabled() {
        LambdaQueryWrapper<TestTube> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestTube::getStatus, 1);
        wrapper.orderByAsc(TestTube::getSortOrder);
        return this.list(wrapper);
    }
}
