package com.lis.report.service;

import com.lis.api.vo.MicroReportVO;
import com.lis.common.core.result.PageResult;
import com.lis.report.entity.MicroAlert;

import java.util.List;

/**
 * 微生物报告服务
 */
public interface MicroReportService {

    /**
     * 创建微生物报告 (默认初步报告状态)
     */
    MicroReportVO createReport(MicroReportVO vo);

    /**
     * 手动触发指定报告的耐药规则评估, 返回本次生成的预警列表
     */
    List<MicroAlert> evaluateExpertRules(Long microReportId);

    /**
     * 计算指定报告的专家/耐药规则命中项(只读, 不落库, 用于报告标注)
     */
    List<MicroAlert> computeRuleHits(Long microReportId);

    /**
     * 仪器导入: 一次性创建报告 + 细菌鉴定 + 药敏结果
     */
    MicroReportVO importReport(MicroReportVO vo);

    /**
     * 通信程序回传结果: 将仪器药敏/细菌数据写入「已存在」的微生物报告。
     * - 匹配策略: 先按 organismCode 或 organismName + antibioticCode 或 antibioticName 做去重
     * - 若已存在相同细菌: 仅 upsert 药敏结果 (覆盖 MIC/Result/Breakpoint)
     * - 若不存在相同细菌: 新增后插入药敏
     * - 完成后自动触发耐药规则评估
     * @param reportId 目标报告ID
     * @param vo 携带 organisms.sensitivities 数据
     * @return 更新后的报告详情
     */
    MicroReportVO pushInstrumentResults(Long reportId, MicroReportVO vo);

    /**
     * 通信程序回传结果(按标本/培养条码匹配目标报告):
     * - 优先 plateBarcode 匹配 lis_micro_report.plate_barcode = 唯一一条报告
     * - 其次 sampleBarcode 匹配最近创建的未最终报告
     * @param plateBarcode 培养条码(培养基条码)
     * @param sampleBarcode 采集条码
     * @param vo 携带 organisms.sensitivities 数据
     * @return 更新后的报告详情
     */
    MicroReportVO pushInstrumentResultsByBarcode(String plateBarcode, String sampleBarcode, MicroReportVO vo);

    /**
     * 添加细菌鉴定
     */
    MicroReportVO.OrganismVO addOrganism(Long microReportId, MicroReportVO.OrganismVO organismVO);

    /** 修改细菌鉴定 */
    MicroReportVO.OrganismVO updateOrganism(Long microReportId, Long organismId, MicroReportVO.OrganismVO organismVO);

    /** 删除细菌鉴定 (同时删除关联药敏结果) */
    void deleteOrganism(Long microReportId, Long organismId);

    /**
     * 添加药敏结果
     */
    MicroReportVO.SensitivityVO addSensitivity(Long microReportId, MicroReportVO.SensitivityVO sensitivityVO);

    /** 修改药敏结果 */
    MicroReportVO.SensitivityVO updateSensitivity(Long microReportId, Long sensitivityId, MicroReportVO.SensitivityVO sensitivityVO);

    /** 删除药敏结果 */
    void deleteSensitivity(Long microReportId, Long sensitivityId);

    /**
     * 发布报告 (多级: 初步 -> 鉴定 -> 最终)
     *
     * @param microReportId 报告ID
     * @param targetStatus 目标状态 (1=初步 2=鉴定 3=最终)
     * @param auditorId 审核人ID
     */
    MicroReportVO publishReport(Long microReportId, int targetStatus, Long auditorId);

    /**
     * 保存革兰氏染色结果
     */
    MicroReportVO saveGramStain(Long microReportId, MicroReportVO vo);

    /**
     * 保存阴性报告
     */
    MicroReportVO saveNegativeReport(Long microReportId, MicroReportVO vo);

    /** 保存血培养信息 */
    MicroReportVO saveBloodCulture(Long microReportId, MicroReportVO vo);

    /** 添加转种记录 */
    MicroReportVO.SubcultureVO addSubculture(Long microReportId, MicroReportVO.SubcultureVO subcultureVO);

    /** 删除转种记录 */
    void deleteSubculture(Long microReportId, Long subcultureId);

    /**
     * 根据 ID 获取报告 (含细菌鉴定和药敏结果)
     */
    MicroReportVO getById(Long id);

    /**
     * 分页查询
     * @param current 页码
     * @param size 每页大小
     * @param patientName 患者姓名(模糊)
     * @param reportStatus 报告状态
     * @param beginDate 开始时间 (支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss)
     * @param endDate 结束时间 (支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss)
     * @param keyword 关键字 (姓名/报告号/标本条码 模糊匹配)
     * @param reportNo 报告号(模糊)
     * @param sampleBarcode 采集条码(模糊)
     * @param patientId 患者ID
     */
    PageResult<MicroReportVO> queryPage(int current, int size, String patientName, Integer reportStatus,
                                        String beginDate, String endDate,
                                        String keyword, String reportNo, String sampleBarcode, Long patientId);

    /**
     * 通过培养基条码核收微生物报告：
     * 1. 根据培养皿条码找到对应标本(含标本条码/患者/检验项目信息)
     * 2. 若该标本尚未有微生物报告, 则自动创建一条(默认初步报告状态)
     * 3. 返回已核收的微生物报告 VO
     */
    MicroReportVO receiveByPlateBarcode(String plateBarcode);

    /**
     * 撤销核收：将「初步报告」状态的微生物报告撤消，同时回滚关联培养基条码的核收状态为未核收。
     * 不允许撤销已发布（鉴定/最终）的报告。
     * @param reportId 微生物报告ID
     */
    void cancelReceive(Long reportId);

    /**
     * 取消最终报告：将「最终报告」状态回退为「鉴定报告」，使报告恢复可编辑、可重新发布。
     * 适用于最终报告审核通过后发现错误需退回修改的场景。
     * @param reportId 微生物报告ID
     */
    MicroReportVO revokeFinalReport(Long reportId);
}
