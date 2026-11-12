package com.lis.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.base.entity.TestItem;
import com.lis.base.mapper.TestItemMapper;
import com.lis.base.service.TestItemService;
import com.lis.common.core.constant.CacheKey;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.util.StringUtil;
import com.lis.common.redis.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 检验项目 Service 实现
 */
@Service
@RequiredArgsConstructor
public class TestItemServiceImpl extends ServiceImpl<TestItemMapper, TestItem> implements TestItemService {

    private final RedisUtil redisUtil;

    @Override
    public Page<TestItem> queryPage(long current, long size, String keyword, String category, Integer isPanel, Integer status, String specimenType) {
        LambdaQueryWrapper<TestItem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtil.isNotEmpty(keyword)) {
            wrapper.and(w -> w.like(TestItem::getItemCode, keyword)
                    .or().like(TestItem::getItemName, keyword)
                    .or().like(TestItem::getShortName, keyword));
        }
        if (StringUtil.isNotEmpty(category)) {
            wrapper.eq(TestItem::getCategory, category);
        }
        if (isPanel != null) {
            wrapper.eq(TestItem::getIsPanel, isPanel);
        }
        if (status != null) {
            wrapper.eq(TestItem::getStatus, status);
        }
        if (StringUtil.isNotEmpty(specimenType)) {
            wrapper.eq(TestItem::getSpecimenType, specimenType);
        }
        wrapper.orderByDesc(TestItem::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public TestItem getByCode(String itemCode) {
        LambdaQueryWrapper<TestItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestItem::getItemCode, itemCode);
        return this.getOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestItem create(TestItem testItem) {
        if (this.getByCode(testItem.getItemCode()) != null) {
            throw new BusinessException("项目编码已存在: " + testItem.getItemCode());
        }
        if (testItem.getStatus() == null) {
            testItem.setStatus(1);
        }
        if (testItem.getIsPanel() == null) {
            testItem.setIsPanel(0);
        }
        testItem.setCreateTime(LocalDateTime.now());
        this.save(testItem);
        refreshDictCache();
        return testItem;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestItem update(TestItem testItem) {
        if (testItem.getId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        this.updateById(testItem);
        refreshDictCache();
        return testItem;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
        refreshDictCache();
    }

    @Override
    public List<TestItem> listPanelItems(Long parentId) {
        LambdaQueryWrapper<TestItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestItem::getParentId, parentId);
        wrapper.eq(TestItem::getIsPanel, 0);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void managePanel(Long parentId, List<Long> childIds) {
        TestItem panel = this.getById(parentId);
        if (panel == null) {
            throw new BusinessException("检验项目不存在: " + parentId);
        }
        if (panel.getIsPanel() != 1) {
            panel.setIsPanel(1);
            this.updateById(panel);
        }
        if (childIds != null && !childIds.isEmpty()) {
            childIds.forEach(childId -> {
                TestItem child = new TestItem();
                child.setId(childId);
                child.setParentId(parentId);
                this.updateById(child);
            });
        }
        refreshDictCache();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<TestItem> loadDictFromCache() {
        Object cached = redisUtil.get(CacheKey.TEST_ITEM_DICT);
        if (cached instanceof List) {
            return (List<TestItem>) cached;
        }
        LambdaQueryWrapper<TestItem> qw = new LambdaQueryWrapper<>();
        qw.eq(TestItem::getStatus, 1);
        qw.orderByDesc(TestItem::getCreateTime);
        List<TestItem> dict = this.list(qw);
        redisUtil.set(CacheKey.TEST_ITEM_DICT, dict, CacheKey.DICT_TTL, TimeUnit.SECONDS);
        return dict;
    }

    /**
     * 刷新项目字典缓存
     */
    private void refreshDictCache() {
        LambdaQueryWrapper<TestItem> qw = new LambdaQueryWrapper<>();
        qw.eq(TestItem::getStatus, 1);
        qw.orderByDesc(TestItem::getCreateTime);
        List<TestItem> dict = this.list(qw);
        redisUtil.set(CacheKey.TEST_ITEM_DICT, dict, CacheKey.DICT_TTL, TimeUnit.SECONDS);
    }
}
