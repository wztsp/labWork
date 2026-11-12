package com.lis.report.service;

import com.lis.report.entity.AntibioticSensitivity;
import com.lis.report.entity.MicroAlert;
import com.lis.report.entity.MicroOrganism;

import java.util.ArrayList;
import java.util.List;

/**
 * 微生物耐药规则引擎: 检测特殊耐药模式 + 危急值触发
 */
public interface MicroExpertRuleService {

    /**
     * 对单个细菌的所有药敏结果进行耐药规则评估, 返回需要创建的预警列表
     *
     * @param reportId    微生物报告ID
     * @param organism    细菌鉴定
     * @param sensitivities 该细菌的药敏结果列表
     * @return 预警列表 (可能为空)
     */
    List<MicroAlert> evaluate(Long reportId, MicroOrganism organism, List<AntibioticSensitivity> sensitivities);
}
