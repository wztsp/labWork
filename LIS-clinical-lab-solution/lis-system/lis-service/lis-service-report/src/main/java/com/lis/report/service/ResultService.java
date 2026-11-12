package com.lis.report.service;

import com.lis.api.dto.ResultDTO;
import com.lis.api.vo.ResultVO;

import java.util.List;
import java.util.Map;

/**
 * 检验结果服务
 */
public interface ResultService {

    /**
     * 保存检验结果 (仪器采集后调用)
     */
    Long saveResult(ResultDTO dto);

    /**
     * 批量保存结果
     */
    List<Long> batchSaveResults(List<ResultDTO> dtos);

    /**
     * 根据标本ID获取结果列表
     */
    List<ResultVO> getResultsBySampleId(Long sampleId);

    /**
     * 趋势分析: 查询该患者该项目最近20次结果, 返回时间序列数据
     */
    List<Map<String, Object>> trendAnalysis(Long patientId, Long testItemId);
}
