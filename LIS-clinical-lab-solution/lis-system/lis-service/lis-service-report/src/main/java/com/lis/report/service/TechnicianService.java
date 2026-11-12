package com.lis.report.service;

import com.lis.report.dto.BatchAuditDTO;
import com.lis.report.dto.BatchModifyInspectorDTO;
import com.lis.report.dto.BatchModifyResultDTO;
import com.lis.report.dto.BatchOpResult;

import java.util.List;
import java.util.Map;

/**
 * 技师工作站服务
 */
public interface TechnicianService {

    /**
     * 查询已核收标本列表 (可按核收仪器筛选, 支持分页)
     * @param current 页码 (从1开始)
     * @param size    每页条数
     */
    List<Map<String, Object>> getSamples(String keyword, String visitType, String startDate, String endDate, int current, int size, Long instrumentId);

    /**
     * 查询标本详情 (含患者信息 + 检验结果/指标)
     * 核收后若无结果, 返回空结果指标 (等待通讯程序回传)
     */
    Map<String, Object> getDetail(Long sampleId);

    /**
     * 保存检验结果 (技师手工录入或修改)
     */
    void saveResults(Long sampleId, List<Map<String, Object>> results);

    /**
     * 统计汇总
     */
    Map<String, Object> getSummary(Long instrumentId, String startDate, String endDate);

    /* ============ 批量操作 ============ */
    BatchOpResult batchAudit(BatchAuditDTO dto, Long operatorId);

    BatchOpResult batchUnaudit(List<Long> sampleIds);

    BatchOpResult batchModifyResult(BatchModifyResultDTO dto);

    BatchOpResult batchModifyInspector(BatchModifyInspectorDTO dto);

    BatchOpResult batchReprintBarcode(Map<String, Object> params);

    BatchOpResult waiveReceive(Map<String, Object> params);

    BatchOpResult sampleCopy(Map<String, Object> params);

    BatchOpResult sampleSplit(Map<String, Object> params);

    BatchOpResult sampleUnsplit(Map<String, Object> params);

    BatchOpResult sampleDestroy(Map<String, Object> params);

    BatchOpResult instrumentSignin(Map<String, Object> params);

    BatchOpResult autoAuditSignin(Map<String, Object> params);

    /* ============ 收藏夹 ============ */
    List<Map<String, Object>> getFavorites();

    Map<String, Object> addFavorite(Map<String, Object> params);

    void removeFavorite(Long id);

    /* ============ 撤销核收 / 修改标本号 ============ */
    /** 撤销核收: 状态退回已签收, 清除核收信息 */
    BatchOpResult cancelReceive(List<Long> sampleIds);

    /** 修改标本号 */
    BatchOpResult updateSampleNo(Long sampleId, String sampleNo);

    /**
     * 修改技师工作站可编辑的标本信息 (标本号/核收时间/核收人/检验时间/采集时间)。
     * 仅允许核收后未审核 (status 4/5) 的标本修改。
     */
    BatchOpResult updateSampleInfo(Long sampleId, Map<String, Object> params);
}
