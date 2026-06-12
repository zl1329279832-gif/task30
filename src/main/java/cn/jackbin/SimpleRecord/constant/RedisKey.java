package cn.jackbin.SimpleRecord.constant;

/**
 * @author: create by bin
 * @version: v1.0
 * @description: cn.jackbin.SimpleRecord.constant
 * @date: 2021/4/22 22:03
 **/
public class RedisKey {
    public final static String WECHAT_ACCESS_TOKEN = "wechatAccessToken";

    /** 共享账本成员集合: shared_book:members:{bookId} */
    public final static String SHARED_BOOK_MEMBERS = "shared_book:members:";

    /** 成员权限缓存: shared_book:perms:{bookId}:{userId} */
    public final static String SHARED_BOOK_PERMS = "shared_book:perms:";

    /** 预算已使用金额: budget:used:{bookId}:{yearMonth} */
    public final static String BUDGET_USED_PREFIX = "budget:used:";

    /** 月结锁定标记: monthly_closing:{bookId}:{yearMonth} */
    public final static String MONTHLY_CLOSING_PREFIX = "monthly_closing:";

    /** 分布式锁前缀: lock: */
    public final static String LOCK_PREFIX = "lock:";

    /** 邀请码缓存: invite_code:{code} */
    public final static String INVITE_CODE_PREFIX = "invite_code:";

    /** 结转已执行标记: carryforward:executed:{bookId}:{yearMonth} */
    public final static String CARRYFORWARD_EXECUTED_PREFIX = "carryforward:executed:";

    /** 结转锁前缀: lock:carryforward:{bookId}:{yearMonth} */
    public final static String LOCK_CARRYFORWARD_PREFIX = "lock:carryforward:";

    /** 调整记录幂等键: idemp:adj:{key} */
    public final static String IDEMP_ADJUSTMENT_PREFIX = "idemp:adj:";

    /** 调整锁前缀: lock:adjustment:{bookId}:{sourceYearMonth} */
    public final static String LOCK_ADJUSTMENT_PREFIX = "lock:adjustment:";

    /** 调整预算写入锁: lock:budget:adjust:{bookId}:{targetYearMonth} */
    public final static String LOCK_BUDGET_ADJUST_PREFIX = "lock:budget:adjust:";

    /** 重算锁前缀: lock:recalc:{bookId}:{yearMonth} */
    public final static String LOCK_RECALC_PREFIX = "lock:recalc:";
}
