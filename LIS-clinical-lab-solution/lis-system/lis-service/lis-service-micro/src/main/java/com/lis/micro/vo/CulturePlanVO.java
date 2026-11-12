package com.lis.micro.vo;

import com.lis.micro.entity.CulturePlan;
import com.lis.micro.entity.CulturePlanItem;

import java.util.List;

/**
 * 培养方案详情 VO (含明细)
 */
public class CulturePlanVO extends CulturePlan {

    /** 方案明细 (培养基及数量) */
    private List<CulturePlanItem> items;

    public List<CulturePlanItem> getItems() {
        return items;
    }

    public void setItems(List<CulturePlanItem> items) {
        this.items = items;
    }
}