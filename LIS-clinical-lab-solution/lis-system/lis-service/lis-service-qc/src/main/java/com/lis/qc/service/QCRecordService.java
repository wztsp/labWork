package com.lis.qc.service;

import com.lis.api.dto.QCRecordDTO;
import com.lis.api.dto.SampleToQcDTO;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.service.rule.QCResult;
import com.lis.common.core.result.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 质控记录服务
 */
public interface QCRecordService {

    /**
     * 录入质控数据，自动计算 Z 分数并调用 Westgard 规则判定
     * @return 判定结果 (含规则触发信息)
     */
    QCResult submitRecord(QCRecordDTO dto);

    PageResult<QCRecord> queryPage(int current, int size, Long lotId, Long testItemId,
                                   Long instrumentId, String measureDate, String beginDate, String endDate);

    List<QCRecord> listByLotId(Long lotId);

    /**
     * 获取该批号最近 N 条记录 (按时间升序，最后一条为最新)
     */
    List<QCRecord> listRecentByLotId(Long lotId, int limit);

    /**
     * 更新质控记录 (修改测量值或时间)
     */
    QCResult updateRecord(Long id, QCRecordDTO dto);

    /**
     * 批量重算指定批号在时间范围内的质控记录失控判定.
     * 按时间顺序逐条重跑 Westgard 规则, 更新 z_score / qc_result / rule_violation.
     * 用于质控图页面"计算失控"按钮: 当靶值/SD/规则配置变更后, 对历史记录重新判定.
     *
     * @param lotId     质控品批号ID
     * @param beginDate 开始日期 (YYYY-MM-DD, 可空)
     * @param endDate   结束日期 (YYYY-MM-DD, 可空)
     * @return 统计: {total=范围内记录数, rejectCount=失控数, updatedCount=判定发生变化的记录数}
     */
    Map<String, Integer> batchRecalculate(Long lotId, String beginDate, String endDate);

    /**
     * 技师工作站：将手工标本的检验结果转换为质控记录。
     * 按 mappings 中每个"检验指标->质控批号"映射，从 lis_result 读取该标本对应指标的实测值，
     * 生成质控记录 (自动计算 Z 分数并运行 Westgard 规则判定)，并将标本标记为质控类型。
     *
     * @param dto 转换请求体
     * @return 生成的质控记录列表
     */
    List<QCRecord> createFromSample(SampleToQcDTO dto);

    /**
     * 取消质控：删除指定来源标本生成的全部质控记录，并清除标本的质控标记。
     *
     * @param sampleId 来源标本ID
     * @return 删除的质控记录数
     */
    int deleteBySampleId(Long sampleId);
}
