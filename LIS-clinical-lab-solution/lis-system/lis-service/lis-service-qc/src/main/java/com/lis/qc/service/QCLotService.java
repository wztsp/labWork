package com.lis.qc.service;

import com.lis.qc.entity.QCLot;

import java.util.List;
import java.util.Map;

/**
 * 质控品批号服务
 */
public interface QCLotService {

    Long create(QCLot lot);

    boolean update(QCLot lot);

    boolean delete(Long id);

    QCLot getById(Long id);

    List<QCLot> listAll();

    List<QCLot> listEnabled();

    /**
     * 基于历史在控(ACCEPT)质控记录重新计算靶值和标准差,
     * 写回 lis_qc_lot.target_value / sd / cv 字段, 并记录操作人及时间.
     * 使用最小样本数保护(默认20), 样本不足时抛出业务异常.
     *
     * @return {oldTarget, oldSd, newTarget, newSd, newCv, sampleCount}
     */
    Map<String, Object> reestimate(Long lotId, Integer minSamples, String operator);
}
