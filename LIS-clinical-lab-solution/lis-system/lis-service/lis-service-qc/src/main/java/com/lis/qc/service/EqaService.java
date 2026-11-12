package com.lis.qc.service;

import com.lis.common.core.result.PageResult;
import com.lis.qc.entity.EqaRecord;

import java.util.List;
import java.util.Map;

/**
 * 室间质控 (EQA) 服务
 */
public interface EqaService {

    /**
     * 提交 EQA 数据
     */
    EqaRecord submitEqa(EqaRecord record);

    /**
     * 批量提交 EQA 数据 (用于手工批量生成)
     */
    int batchSubmitEqa(List<EqaRecord> records);

    /**
     * 计算得分 (偏移度评估)
     */
    EqaRecord calculateScore(Long id);

    /**
     * 分页查询 (支持按批号、项目名称、仪器名称、是否合格过滤)
     */
    PageResult<EqaRecord> getEqaPage(int current, int size, String eqaBatchNo, Long testItemId,
                                     String testItemName, String instrumentName, Boolean passed);

    /**
     * 详情
     */
    EqaRecord getById(Long id);

    /**
     * 合格率统计 (支持按批号、项目名称过滤)
     * @return Map: { total, passed, passedRate }
     */
    Map<String, Object> getEqaStats(String eqaBatchNo, String testItemName);
}
