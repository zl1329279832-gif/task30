package cn.jackbin.SimpleRecord.service.impl;

import cn.jackbin.SimpleRecord.constant.CodeMsg;
import cn.jackbin.SimpleRecord.constant.RedisKey;
import cn.jackbin.SimpleRecord.entity.MonthLockDO;
import cn.jackbin.SimpleRecord.exception.BusinessException;
import cn.jackbin.SimpleRecord.mapper.MonthLockMapper;
import cn.jackbin.SimpleRecord.service.MonthLockService;
import cn.jackbin.SimpleRecord.service.SharedBookLogService;
import cn.jackbin.SimpleRecord.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class MonthLockServiceImpl extends ServiceImpl<MonthLockMapper, MonthLockDO>
        implements MonthLockService {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private SharedBookLogService sharedBookLogService;

    private static final long LOCK_CACHE_TTL = 3600; // 1小时

    @Override
    public void lockMonth(Integer recordBookId, String yearMonth, Integer userId) {
        // 检查是否已锁定
        if (isLocked(recordBookId, yearMonth)) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_MONTH_LOCKED);
        }
        MonthLockDO lockDO = MonthLockDO.builder()
                .recordBookId(recordBookId)
                .yearMonth(yearMonth)
                .lockedBy(userId)
                .lockTime(new Date())
                .status(0)
                .build();
        save(lockDO);
        // 缓存锁定状态
        String cacheKey = RedisKey.MONTH_LOCK_PREFIX + recordBookId + ":" + yearMonth;
        redisUtil.set(cacheKey, "1", LOCK_CACHE_TTL);
        sharedBookLogService.log(recordBookId, userId, "LOCK", lockDO.getId(),
                "锁定月份: " + yearMonth);
    }

    @Override
    public void unlockMonth(Integer recordBookId, String yearMonth, Integer userId) {
        MonthLockDO lock = getLock(recordBookId, yearMonth);
        if (lock == null) {
            return;
        }
        removeById(lock.getId());
        // 清除缓存
        String cacheKey = RedisKey.MONTH_LOCK_PREFIX + recordBookId + ":" + yearMonth;
        redisUtil.del(cacheKey);
        sharedBookLogService.log(recordBookId, userId, "UNLOCK", lock.getId(),
                "解锁月份: " + yearMonth);
    }

    @Override
    public boolean isLocked(Integer recordBookId, String yearMonth) {
        String cacheKey = RedisKey.MONTH_LOCK_PREFIX + recordBookId + ":" + yearMonth;
        // 先查Redis
        if (redisUtil.hasKey(cacheKey)) {
            return true;
        }
        // 查数据库
        MonthLockDO lock = getLock(recordBookId, yearMonth);
        if (lock != null) {
            redisUtil.set(cacheKey, "1", LOCK_CACHE_TTL);
            return true;
        }
        return false;
    }

    @Override
    public MonthLockDO getLock(Integer recordBookId, String yearMonth) {
        LambdaQueryWrapper<MonthLockDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonthLockDO::getRecordBookId, recordBookId)
                .eq(MonthLockDO::getYearMonth, yearMonth);
        return getOne(wrapper);
    }

    @Override
    public void checkNotLocked(Integer recordBookId, String yearMonth) {
        if (isLocked(recordBookId, yearMonth)) {
            throw new BusinessException(CodeMsg.SHARED_BOOK_MONTH_LOCKED);
        }
    }
}
