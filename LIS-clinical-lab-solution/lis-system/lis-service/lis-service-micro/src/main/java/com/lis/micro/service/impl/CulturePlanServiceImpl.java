package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.micro.entity.CulturePlan;
import com.lis.micro.entity.CulturePlanItem;
import com.lis.micro.mapper.CulturePlanItemMapper;
import com.lis.micro.mapper.CulturePlanMapper;
import com.lis.micro.service.CulturePlanService;
import com.lis.micro.vo.CulturePlanVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 培养方案服务实现
 */
@Service
public class CulturePlanServiceImpl extends ServiceImpl<CulturePlanMapper, CulturePlan> implements CulturePlanService {

    private final CulturePlanItemMapper planItemMapper;

    public CulturePlanServiceImpl(CulturePlanItemMapper planItemMapper) {
        this.planItemMapper = planItemMapper;
    }

    @Override
    public Page<CulturePlan> queryPage(long current, long size, String keyword) {
        LambdaQueryWrapper<CulturePlan> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(CulturePlan::getPlanName, keyword)
                    .or().like(CulturePlan::getTestItemName, keyword));
        }
        wrapper.orderByDesc(CulturePlan::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public CulturePlanVO getDetail(Long id) {
        CulturePlan plan = this.getById(id);
        if (plan == null) {
            throw new BusinessException("培养方案不存在: " + id);
        }
        CulturePlanVO vo = new CulturePlanVO();
        BeanUtils.copyProperties(plan, vo);
        vo.setItems(planItemMapper.selectList(new LambdaQueryWrapper<CulturePlanItem>()
                .eq(CulturePlanItem::getPlanId, id)
                .orderByAsc(CulturePlanItem::getSortOrder)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CulturePlan savePlan(CulturePlan plan, List<CulturePlanItem> items) {
        if (plan.getTestItemId() == null) {
            throw new BusinessException("请选择绑定的检验项目");
        }
        if (plan.getStatus() == null) {
            plan.setStatus(1);
        }
        if (plan.getIsDefault() == null) {
            plan.setIsDefault(0);
        }
        // 存在其它方案时, 同一检验项目只能有一个默认方案
        if (plan.getId() == null) {
            this.save(plan);
        } else {
            this.updateById(plan);
            // 删除旧的明细
            planItemMapper.delete(new LambdaQueryWrapper<CulturePlanItem>()
                    .eq(CulturePlanItem::getPlanId, plan.getId()));
        }
        // 保存明细
        if (!CollectionUtils.isEmpty(items)) {
            int sort = 0;
            for (CulturePlanItem item : items) {
                item.setId(null);
                item.setPlanId(plan.getId());
                if (item.getQuantity() == null || item.getQuantity() < 1) {
                    item.setQuantity(1);
                }
                item.setSortOrder(sort++);
                planItemMapper.insert(item);
            }
        }
        return plan;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long id) {
        planItemMapper.delete(new LambdaQueryWrapper<CulturePlanItem>()
                .eq(CulturePlanItem::getPlanId, id));
        this.removeById(id);
    }

    @Override
    public List<CulturePlan> listByTestItemId(Long testItemId) {
        return this.list(new LambdaQueryWrapper<CulturePlan>()
                .eq(CulturePlan::getTestItemId, testItemId)
                .eq(CulturePlan::getStatus, 1)
                .orderByDesc(CulturePlan::getIsDefault));
    }
}