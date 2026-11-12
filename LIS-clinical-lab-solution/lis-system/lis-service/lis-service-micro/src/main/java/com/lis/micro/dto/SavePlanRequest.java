package com.lis.micro.dto;

import com.lis.micro.entity.CulturePlan;
import com.lis.micro.entity.CulturePlanItem;

import java.util.List;

/**
 * 保存培养方案请求 (方案 + 明细)
 */
public class SavePlanRequest {

    private CulturePlan plan;

    private List<CulturePlanItem> items;

    public CulturePlan getPlan() {
        return plan;
    }

    public void setPlan(CulturePlan plan) {
        this.plan = plan;
    }

    public List<CulturePlanItem> getItems() {
        return items;
    }

    public void setItems(List<CulturePlanItem> items) {
        this.items = items;
    }
}