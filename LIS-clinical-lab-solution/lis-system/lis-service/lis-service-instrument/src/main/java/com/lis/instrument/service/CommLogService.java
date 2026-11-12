package com.lis.instrument.service;

import com.lis.common.core.result.PageResult;
import com.lis.instrument.entity.InstrumentCommLog;

/**
 * 仪器通讯日志服务
 */
public interface CommLogService {

    /**
     * 保存通讯日志
     */
    void saveLog(InstrumentCommLog log);

    /**
     * 分页查询
     */
    PageResult<InstrumentCommLog> queryPage(Long instrumentId, int current, int size);
}
