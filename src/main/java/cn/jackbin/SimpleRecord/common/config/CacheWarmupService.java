package cn.jackbin.SimpleRecord.common.config;

import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.MonthlyClosingDO;
import cn.jackbin.SimpleRecord.mapper.MonthlyClosingMapper;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时预热缓存
 */
@Component
public class CacheWarmupService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmupService.class);

    @Autowired
    private MonthlyClosingMapper monthlyClosingMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public void run(String... args) {
        try {
            warmupMonthlyClosingCache();
            log.info("Cache warmup completed");
        } catch (Exception e) {
            log.warn("Cache warmup failed (may be first startup): {}", e.getMessage());
        }
    }

    private void warmupMonthlyClosingCache() {
        List<MonthlyClosingDO> closings = monthlyClosingMapper.selectList(null);
        for (MonthlyClosingDO closing : closings) {
            String key = RedisKey.MONTHLY_CLOSING_PREFIX + closing.getBookId() + ":" + closing.getYearMonth();
            redisUtil.set(key, 1);
        }
        log.info("Warmed up {} monthly closing cache entries", closings.size());
    }
}
