package cn.jackbin.SimpleRecord.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis分布式锁工具
 */
@Component
public class RedisLockUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 尝试获取锁
     */
    public boolean tryLock(String lockKey, long timeoutSeconds) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 释放锁
     */
    public void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
