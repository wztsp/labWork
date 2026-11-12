package com.lis.report.service;

import com.lis.api.dto.ReportAuditDTO;
import com.lis.api.dto.ResultDTO;
import com.lis.api.vo.ReportVO;
import com.lis.common.core.result.PageResult;

import java.util.List;

/**
 * 报告服务
 */
public interface ReportService {

    /**
     * 根据标本结果生成报告草稿
     */
    ReportVO generateReport(Long sampleId);

    /**
     * 自动审核 (调用 AutoAuditEngine)
     */
    boolean autoAudit(Long reportId);

    /**
     * 人工审核
     */
    boolean manualAudit(ReportAuditDTO dto);

    /**
     * 发布报告，发送 MQ 消息 report.published
     */
    boolean publish(Long reportId, Long publisherId);

    /**
     * 技师终审: 生成报告(若不存在)并直接设为已发布(终审), 同时设置审核人/发布人/审核时间/发布时间。
     * 报告审核通过后无需再单独发布, 一步到位。
     */
    boolean finalizeAndPublish(Long sampleId, Long operatorId);

    /**
     * 修改报告 (留痕)
     */
    boolean modifyReport(Long reportId, List<ResultDTO> newResults, String reason);

    /**
     * 根据 ID 获取报告
     */
    ReportVO getById(Long id);

    /**
     * 根据标本 ID 获取报告
     */
    ReportVO getBySampleId(Long sampleId);

    /**
     * 分页查询 (扩展版, 支持报告查询页所有筛选条件)
     * size/current 分页, patientName/keyword 模糊搜, status 状态,
     * startDate/endDate 按发布/创建时间区间, patientId 患者ID,
     * visitType 就诊类型(1门诊/2住院/3体检), department 申请科室,
     * sampleNo/sampleBarcode 标本号/条码搜, visitNo 就诊号搜
     */
    PageResult<ReportVO> queryPage(int current, int size, String patientName, Integer status,
                                   String startDate, String endDate, Long patientId,
                                   Integer visitType, String department,
                                   String sampleNo, String sampleBarcode, String visitNo,
                                   String keyword);

    /**
     * 合并多份标本报告到目标标本: 将各标本结果复制到目标标本下, 生成一份合并报告, 记录合并日志
     */
    ReportVO mergeReports(List<Long> sampleIds, Long targetSampleId);

    /**
     * 临床端: 按就诊号查询已发布报告
     */
    List<ReportVO> getPublishedByVisitNo(String visitNo);

    /**
     * 临床端: 按身份证号查询已发布报告 (内部会做 SHA-256 哈希后匹配 id_card_hash)
     */
    List<ReportVO> getPublishedByIdCard(String idCard);

    /**
     * 临床端: 按就诊号 + 身份证号双条件查询已发布报告 (双重校验, 内部对身份证做 SHA-256 哈希后匹配)
     */
    List<ReportVO> getPublishedByVisitNoAndIdCard(String visitNo, String idCard);
}
