package cn.jackbin.SimpleRecord.constant;

/**
 * @author: create by bin
 * @version: v1.0
 * @description: cn.jackbin.SimpleRecord.constant
 * @date: 2021/4/22 22:03
 **/
public class RedisKey {
    public final static String WECHAT_ACCESS_TOKEN = "wechatAccessToken";

    /**
     * 预算执行缓存 key: budget:execution:{bookId}:{yearMonth}
     */
    public final static String BUDGET_EXECUTION_PREFIX = "budget:execution:";

    /**
     * 预算预警缓存 key: budget:warn:{bookId}:{yearMonth}
     */
    public final static String BUDGET_WARN_PREFIX = "budget:warn:";

    /**
     * 月锁定缓存 key: month:lock:{bookId}:{yearMonth}
     */
    public final static String MONTH_LOCK_PREFIX = "month:lock:";

    /**
     * 共享账本成员权限缓存 key: shared:book:member:{bookId}:{userId}
     */
    public final static String SHARED_BOOK_MEMBER_PREFIX = "shared:book:member:";

    /**
     * 结算计算锁 key: settle:lock:{bookId}:{yearMonth}
     */
    public final static String SETTLE_CALC_LOCK_PREFIX = "settle:lock:";
}
