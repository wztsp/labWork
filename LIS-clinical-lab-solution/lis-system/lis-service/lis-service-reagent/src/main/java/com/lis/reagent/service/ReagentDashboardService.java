package com.lis.reagent.service;

import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.entity.ReagentOpening;

import java.util.List;
import java.util.Map;

public interface ReagentDashboardService {

    /** 看板总览: 试剂总数/库存总值/近效期数/低库存数/开瓶过期数 */
    Map<String, Object> getOverview();

    /** 近效期预警列表 */
    List<ReagentStock> getNearExpiryList(int days);

    /** 低库存预警列表 */
    List<Map<String, Object>> getLowStockList();

    /** 开瓶效期预警列表 */
    List<ReagentOpening> getOpenedExpiryList();
}
