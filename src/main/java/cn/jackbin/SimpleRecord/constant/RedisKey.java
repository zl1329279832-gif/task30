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

    /** 有效预算缓存: budget:effective:{bookId}:{yearMonth} */
    public final static String BUDGET_EFFECTIVE_PREFIX = "budget:effective:";

    /** 结转规则缓存: carryover:rule:{bookId} */
    public final static String CARRYOVER_RULE_PREFIX = "carryover:rule:";
}
