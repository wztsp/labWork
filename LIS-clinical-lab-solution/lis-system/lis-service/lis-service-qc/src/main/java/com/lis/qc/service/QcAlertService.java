package com.lis.qc.service;

import com.lis.common.core.result.PageResult;
import com.lis.qc.entity.QcAlert;

import java.util.Map;

/**
 * 质控偏差趋势预警服务
 */
public interface QcAlertService {

    /** 保存预警 */
    void saveAlert(Map<String, Object> payload);

    /** 分页查询预警列表 */
    PageResult<QcAlert> queryPage(int current, int size, String status);

    /** 标记为已读 */
    boolean markRead(Long id);
}
