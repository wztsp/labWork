package com.lis.sample.service;

import com.lis.sample.entity.BarcodeRule;

import java.util.List;

/**
 * 条码 Service
 * 基于可配置规则生成条码, 支持多种格式: 纯数字/年月日+数字/2位年+月日+数字/字母+数字 等
 */
public interface BarcodeService {

    /**
     * 生成条码 (默认使用 SAMPLE 规则)
     */
    String generateBarcode();

    /**
     * 按规则编码生成条码
     * @param ruleCode 规则编码 (SAMPLE/ORDER/VISIT/PATIENT)
     */
    String generateBarcode(String ruleCode);

    /**
     * 校验条码唯一性
     */
    boolean validateBarcode(String barcode);

    /**
     * 查询所有条码规则
     */
    List<BarcodeRule> listRules();

    /**
     * 查询启用的规则
     */
    BarcodeRule getEnabledRule(String ruleCode);

    /**
     * 更新条码规则
     */
    void updateRule(BarcodeRule rule);
}
