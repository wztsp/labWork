package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.SamplePlate;

import java.util.List;

/**
 * 标本培养皿条码服务
 */
public interface SamplePlateService extends IService<SamplePlate> {

    /**
     * 根据标本条码生成培养皿条码 (优先按 planId, 其次按标本类型+检验项目匹配)
     * @param sampleBarcode 标本条码
     * @param sampleId 标本ID (可选)
     * @param sampleType 标本类型 (医生下达申请的标本类型, 用于匹配标本类型专属方案)
     * @param planId 前端选中的培养方案ID (优先按此方案生成, 为NULL时按sampleType+testItemIds匹配)
     * @param testItemIds 标本对应的检验项目ID列表 (用于按检验项目反向匹配培养方案)
     * @param patientName 患者姓名 (打印标签用)
     * @param operatorId 操作人ID
     * @param operatorName 操作人姓名
     * @return 生成的培养皿列表
     */
    List<SamplePlate> generatePlates(String sampleBarcode, Long sampleId,
                                     String sampleType, Long planId,
                                     java.util.List<Long> testItemIds, String patientName,
                                     Long operatorId, String operatorName);

    /** 按标本查询已生成的培养皿 */
    List<SamplePlate> listBySampleId(Long sampleId);

    /** 按标本条码查询已生成的培养皿 */
    List<SamplePlate> listBySampleBarcode(String sampleBarcode);

    /**
     * 取消单条培养基条码生成 (软删除+状态置为作废).
     * 只有未核收的(status=0或NULL)才能取消；已核收需先撤销微生物报告。
     * @param plateId 培养皿记录ID
     */
    void cancelPlate(Long plateId);

    /**
     * 取消某标本所有培养基条码生成 (批量).
     * 只要有一条培养基已核收则整体禁止取消，防止部分遗漏。
     */
    void cancelAllBySample(Long sampleId, String sampleBarcode);
}