package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.CulturePlan;
import com.lis.micro.entity.CulturePlanItem;
import com.lis.micro.vo.CulturePlanVO;

import java.util.List;

/**
 * 培养方案服务
 */
public interface CulturePlanService extends IService<CulturePlan> {

    /** 分页查询 (带检验项目名称) */
    Page<CulturePlan> queryPage(long current, long size, String keyword);

    /** 方案详情 (含明细) */
    CulturePlanVO getDetail(Long id);

    /** 保存方案 (含明细, 事务) */
    CulturePlan savePlan(CulturePlan plan, List<CulturePlanItem> items);

    /** 删除方案 (含明细) */
    void deletePlan(Long id);

    /** 根据检验项目ID查询启用的方案 */
    List<CulturePlan> listByTestItemId(Long testItemId);
}