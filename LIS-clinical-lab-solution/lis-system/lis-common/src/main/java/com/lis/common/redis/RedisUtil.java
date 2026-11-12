package com.lis.common.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * 如果 Redis 不可用，自动降级为内存缓存（仅用于开发测试）
 */
@Component
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean redisAvailable;
    private final Map<String, Object> localCache = new ConcurrentHashMap<>();

    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisAvailable = checkRedis();
        if (!redisAvailable) {
            System.out.println("警告: Redis 不可用，使用内存缓存替代（仅用于开发测试）");
        }
    }

    private boolean checkRedis() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Redis 是否真实可用。
     * 当 Redis 不可用(降级为内存缓存)时, 依赖 Redis 的序号自增等业务不得使用内存缓存,
     * 否则进程重启后序号会从 0 重新自增, 与数据库中已存在的序号冲突。
     */
    public boolean isRedisAvailable() {
        return redisAvailable;
    }

    public void set(String key, Object value) {
        if (redisAvailable) {
            redisTemplate.opsForValue().set(key, value);
        } else {
            localCache.put(key, value);
        }
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        if (redisAvailable) {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } else {
            localCache.put(key, value);
        }
    }

    public Object get(String key) {
        if (redisAvailable) {
            return redisTemplate.opsForValue().get(key);
        } else {
            return localCache.get(key);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        if (redisAvailable) {
            return (T) redisTemplate.opsForValue().get(key);
        } else {
            return (T) localCache.get(key);
        }
    }

    public Boolean delete(String key) {
        if (redisAvailable) {
            return redisTemplate.delete(key);
        } else {
            return localCache.remove(key) != null;
        }
    }

    public Boolean hasKey(String key) {
        if (redisAvailable) {
            return redisTemplate.hasKey(key);
        } else {
            return localCache.containsKey(key);
        }
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        if (redisAvailable) {
            return redisTemplate.expire(key, timeout, unit);
        } else {
            return true; // 内存缓存不支持过期，返回true
        }
    }

    /**
     * 分布式锁 - setIfAbsent
     */
    public Boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
        if (redisAvailable) {
            return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
        } else {
            return localCache.putIfAbsent(key, value) == null;
        }
    }

    /**
     * 释放锁
     */
    public void unlock(String key, String value) {
        if (redisAvailable) {
            Object currentValue = redisTemplate.opsForValue().get(key);
            if (value.equals(currentValue)) {
                redisTemplate.delete(key);
            }
        } else {
            Object currentValue = localCache.get(key);
            if (value.equals(currentValue)) {
                localCache.remove(key);
            }
        }
    }

    public Long incr(String key) {
        if (redisAvailable) {
            return redisTemplate.opsForValue().increment(key);
        } else {
            synchronized (localCache) {
                Long value = (Long) localCache.getOrDefault(key, 0L);
                value++;
                localCache.put(key, value);
                return value;
            }
        }
    }
}
