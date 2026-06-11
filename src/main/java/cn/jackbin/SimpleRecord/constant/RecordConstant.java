package cn.jackbin.SimpleRecord.constant;

/**
 * @author: create by bin
 * @version: v1.0
 * @description: cn.jackbin.SimpleRecord.constant
 * @date: 2020/10/12 21:51
 **/
public class RecordConstant {
    public static final String EXPEND_RECORD_TYPE = "expendType";

    public static final String INCOME_RECORD_TYPE = "incomeType";

    public static final String RECORD_TYPE = "recordType";

    public static final String ACCOUNT_TYPE = "accountType";

    public static final String PAYMENT_ACCOUNT = "payment"; // 应收应付账户类型

    public static final String BXK = "报销款";

    /**
     * 用户默认
     */
    public static final int USER_DEFAULT = 1;

    /**
     * 非用户默认
     */
    public static final int NOT_USER_DEFAULT = 2;

    /**
     * 属于
     */
    public static final int BUSINESS_YES = 1;

    /**
     * 不属于
     */
    public static final int BUSINESS_NOT = 2;

    /**
     * 非报销
     */
    public static final int NOT_RECOVERABLE = 1;

    /**
     * 待报销
     */
    public static final int TO_RECOVERABLE = 2;

    /**
     * 已报销
     */
    public static final int IS_RECOVERABLE = 3;

    /**
     * 默认账单名称
     */
    public static final String DEFAULT_RECORD_BOOK_NAME = "默认账单";

    // ========== 共享账本相关常量 ==========

    /**
     * 账本类型：个人
     */
    public static final int BOOK_TYPE_PERSONAL = 1;

    /**
     * 账本类型：共享
     */
    public static final int BOOK_TYPE_SHARED = 2;

    /**
     * 审核状态：待审核
     */
    public static final int AUDIT_PENDING = 1;

    /**
     * 审核状态：已入账
     */
    public static final int AUDIT_APPROVED = 2;

    /**
     * 审核状态：已驳回
     */
    public static final int AUDIT_REJECTED = 3;

    /**
     * 审核状态：已冲正
     */
    public static final int AUDIT_REVERSED = 4;

    /**
     * 共享账本权限：录入
     */
    public static final String PERM_RECORD = "record";

    /**
     * 共享账本权限：审核
     */
    public static final String PERM_AUDIT = "audit";

    /**
     * 共享账本权限：查看
     */
    public static final String PERM_VIEW = "view";

    /**
     * 共享账本权限：结算
     */
    public static final String PERM_SETTLE = "settle";

    /**
     * 全部权限
     */
    public static final String PERM_ALL = "record,audit,view,settle";

    /**
     * 预算类型：总预算
     */
    public static final int BUDGET_TYPE_TOTAL = 1;

    /**
     * 预算类型：分类预算
     */
    public static final int BUDGET_TYPE_CATEGORY = 2;

    /**
     * 预算类型：成员预算
     */
    public static final int BUDGET_TYPE_MEMBER = 3;

    /**
     * 结算状态：待结算
     */
    public static final int SETTLE_PENDING = 1;

    /**
     * 结算状态：已结算
     */
    public static final int SETTLE_DONE = 2;

    /**
     * 邀请状态：待接受
     */
    public static final int INVITE_PENDING = 0;

    /**
     * 邀请状态：已接受
     */
    public static final int INVITE_ACCEPTED = 1;

    /**
     * 邀请状态：已过期
     */
    public static final int INVITE_EXPIRED = 2;

    /**
     * 邀请状态：已取消
     */
    public static final int INVITE_CANCELLED = 3;

}
