package com.lis.base.service.impl;

import com.lis.base.entity.Instrument;
import com.lis.base.mapper.BaseInstrumentMapper;
import com.lis.base.service.InstrumentStatusService;
import com.lis.common.core.constant.CacheKey;
import com.lis.common.redis.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 仪器在线状态管理 Service 实现
 * 使用 Redis 缓存仪器在线状态，心跳更新
 */
@Service
@RequiredArgsConstructor
public class InstrumentStatusServiceImpl implements InstrumentStatusService {

    private final BaseInstrumentMapper instrumentMapper;
    private final RedisUtil redisUtil;

    /** 心跳超时时间(秒)，超过该时间未心跳则视为离线 */
    private static final long HEARTBEAT_TIMEOUT = 120L;

    @Override
    public List<Map<String, Object>> listStatus() {
        List<Instrument> instruments = instrumentMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Instrument ins : instruments) {
            result.add(buildStatus(ins));
        }
        return result;
    }

    @Override
    public Map<String, Object> getStatus(Long id) {
        Instrument ins = instrumentMapper.selectById(id);
        if (ins == null) {
            return null;
        }
        return buildStatus(ins);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void online(Long id) {
        String key = CacheKey.INSTRUMENT_STATUS + id;
        Map<String, Object> status = new HashMap<>();
        status.put("online", true);
        status.put("onlineTime", LocalDateTime.now().toString());
        status.put("lastHeartbeat", LocalDateTime.now().toString());
        redisUtil.set(key, status, HEARTBEAT_TIMEOUT, TimeUnit.SECONDS);

        // 更新仪器最后在线时间
        Instrument update = new Instrument();
        update.setId(id);
        update.setLastOnlineTime(LocalDateTime.now());
        instrumentMapper.updateById(update);
    }

    @Override
    public void offline(Long id) {
        String key = CacheKey.INSTRUMENT_STATUS + id;
        redisUtil.delete(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(Long id) {
        String key = CacheKey.INSTRUMENT_STATUS + id;
        Map<String, Object> status = new HashMap<>();
        status.put("online", true);
        status.put("lastHeartbeat", LocalDateTime.now().toString());
        redisUtil.set(key, status, HEARTBEAT_TIMEOUT, TimeUnit.SECONDS);

        Instrument update = new Instrument();
        update.setId(id);
        update.setLastOnlineTime(LocalDateTime.now());
        instrumentMapper.updateById(update);
    }

    /**
     * 构建状态视图: 优先从 Redis 读取在线状态，超时则视为离线
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildStatus(Instrument ins) {
        Map<String, Object> result = new HashMap<>();
        result.put("instrumentId", ins.getId());
        result.put("instrumentCode", ins.getInstrumentCode());
        result.put("instrumentName", ins.getInstrumentName());
        result.put("status", ins.getStatus());
        result.put("lastOnlineTime", ins.getLastOnlineTime());

        String key = CacheKey.INSTRUMENT_STATUS + ins.getId();
        Object cached = redisUtil.get(key);
        boolean online = false;
        if (cached instanceof Map) {
            Map<String, Object> statusMap = (Map<String, Object>) cached;
            online = Boolean.TRUE.equals(statusMap.get("online"));
            result.put("lastHeartbeat", statusMap.get("lastHeartbeat"));
        }
        result.put("online", online);
        return result;
    }
}
