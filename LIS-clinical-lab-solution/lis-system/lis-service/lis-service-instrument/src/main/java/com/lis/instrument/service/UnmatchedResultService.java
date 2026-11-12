package com.lis.instrument.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.api.dto.ResultDTO;
import com.lis.api.dto.UnmatchedBatchToQcDTO;
import com.lis.instrument.entity.InstrumentUnmatchedResult;

import java.util.List;
import java.util.Map;

/**
 * 仪器「无主标本」结果服务：落库、查询、手工调整、匹配回业务标本、转质控。
 */
public interface UnmatchedResultService {

    /** 状态：0待处理 1已匹配(到患者标本) 2已作废 3已转质控 */
    int STATUS_PENDING   = 0;
    int STATUS_MATCHED   = 1;
    int STATUS_DISCARDED = 2;
    int STATUS_TO_QC     = 3;

    /**
     * 保存来自仪器的未匹配结果（批量，状态=0待处理）。
     */
    void saveUnmatched(Long instrumentId, List<ResultDTO> results);

    /**
     * 分页查询无主标本结果（可按状态/仪器过滤，回填仪器名称）。
     */
    Page<InstrumentUnmatchedResult> page(long current, long size,
                                         Integer status, Long instrumentId);

    /**
     * 手工调整无主标本结果的字段（结果值/单位/范围/异常标志/备注等）。
     */
    InstrumentUnmatchedResult updateResult(Long id, InstrumentUnmatchedResult dto);

    /**
     * 将无主标本结果按条码/样本号匹配到业务标本并落库为正式结果，状态置为已匹配。
     *
     * @return 匹配上的业务标本 ID；未找到可匹配标本时抛出业务异常
     */
    Long matchToSample(Long id, String sampleBarcode);

    /**
     * 批量将一组无主标本结果匹配到同一个业务标本（按相同条码）。
     * 适用于「标本列表」中一张无主卡片包含多条指标结果时的一键认领场景。
     *
     * @return 成功匹配的结果条数
     */
    int matchBatchToSample(List<Long> ids, String sampleBarcode);

    /**
     * 作废一条无主标本结果（状态=2）。
     */
    void discard(Long id);

    /**
     * 批量作废一组无主标本结果（状态=2）。
     */
    void discardBatch(List<Long> ids);

    /**
     * 批量将无主标本结果转换为质控记录。
     * <p>
     * 按用户指定的质控品批号 (qcLotId) 匹配:
     *   1) 批号表 instrumentId 必须与无主记录的 instrumentId 一致
     *   2) 批号表的 testItemId 必须能通过「指标代码配置表」反向解析到无主记录的 testCode
     * 满足条件则将 resultValue 作为质控测量值调用 QCRecordService.submitRecord
     * 生成质控记录, 并更新无主状态为 3=已转质控。
     *
     * @return {successCount, failCount, generatedQcCount, failDetails:[{id,reason}]}
     */
    Map<String, Object> batchConvertToQc(UnmatchedBatchToQcDTO dto);
}