package cn.jackbin.SimpleRecord.service;

import cn.jackbin.SimpleRecord.bo.PageBO;
import cn.jackbin.SimpleRecord.entity.MonthlyClosingDO;

import java.util.Date;

/**
 * 月结服务
 */
public interface MonthlyClosingService {

    /**
     * 执行月结
     */
    void closeMonth(Integer bookId, Integer userId, String yearMonth, String remark);

    /**
     * 检查某月是否已结
     */
    boolean isMonthClosed(Integer bookId, String yearMonth);

    /**
     * 校验月未结 (已结则抛异常)
     */
    void checkNotClosed(Integer bookId, Date occurTime);

    /**
     * 获取某月结信息
     */
    MonthlyClosingDO getClosing(Integer bookId, String yearMonth);

    /**
     * 分页获取月结记录
     */
    void getClosingsByPage(Integer bookId, PageBO<MonthlyClosingDO> pageBO);
}
