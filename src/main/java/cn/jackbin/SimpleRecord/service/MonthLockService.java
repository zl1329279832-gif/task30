package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.entity.MonthLockDO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface MonthLockService extends IService<MonthLockDO> {

    void lockMonth(Integer recordBookId, String yearMonth, Integer userId);

    void unlockMonth(Integer recordBookId, String yearMonth, Integer userId);

    boolean isLocked(Integer recordBookId, String yearMonth);

    MonthLockDO getLock(Integer recordBookId, String yearMonth);

    void checkNotLocked(Integer recordBookId, String yearMonth);
}
