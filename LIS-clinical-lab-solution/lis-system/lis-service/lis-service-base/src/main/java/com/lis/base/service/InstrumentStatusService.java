package com.lis.base.service;

import com.lis.base.entity.Instrument;

import java.util.List;
import java.util.Map;

/**
 * 仪器在线状态管理 Service
 */
public interface InstrumentStatusService {

    /**
     * 查询全部仪器在线状态
     */
    List<Map<String, Object>> listStatus();

    /**
     * 查询单台仪器在线状态
     */
    Map<String, Object> getStatus(Long id);

    /**
     * 仪器上线(心跳更新)
     */
    void online(Long id);

    /**
     * 仪器下线
     */
    void offline(Long id);

    /**
     * 心跳更新
     */
    void heartbeat(Long id);
}
