package cn.jackbin.SimpleRecord.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
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

    /**
     * 带重试的锁获取
     */
    public boolean tryLockWithRetry(String lockKey, long timeoutSeconds, int maxRetries, long retryIntervalMs) {
        for (int i = 0; i <= maxRetries; i++) {
            if (tryLock(lockKey, timeoutSeconds)) {
                return true;
            }
            if (i < maxRetries) {
                try {
                    Thread.sleep(retryIntervalMs * (i + 1)); // exponential-ish backoff
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 带所有主的锁获取 (返回ownerId, null=失败)
     */
    public String tryLockWithOwner(String lockKey, long timeoutSeconds) {
        String ownerId = UUID.randomUUID().toString();
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, ownerId, timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result) ? ownerId : null;
    }

    /**
     * 释放所有者锁 (Lua脚本: 仅当值匹配时删除)
     */
    public boolean unlockWithOwner(String lockKey, String ownerId) {
        String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                           "return redis.call('del', KEYS[1]) else return 0 end";
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(lockKey), ownerId);
        return Long.valueOf(1).equals(result);
    }
}
