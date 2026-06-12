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

    // ========== 共享账本权限 ==========

    /** 录入权限 */
    public static final String PERM_ENTRY = "entry";

    /** 审核权限 */
    public static final String PERM_REVIEW = "review";

    /** 查看权限 */
    public static final String PERM_VIEW = "view";

    /** 结算权限 */
    public static final String PERM_SETTLEMENT = "settlement";

    /** 账本类型: 个人 */
    public static final int BOOK_TYPE_PERSONAL = 0;

    /** 账本类型: 共享 */
    public static final int BOOK_TYPE_SHARED = 1;

    // ========== 审核状态 ==========

    /** 无需审核(个人账本) */
    public static final int REVIEW_NONE = 0;

    /** 待审核 */
    public static final int REVIEW_PENDING = 1;

    /** 已入账 */
    public static final int REVIEW_POSTED = 2;

    /** 已驳回 */
    public static final int REVIEW_REJECTED = 3;

    /** 已冲正 */
    public static final int REVIEW_REVERSED = 4;

    // ========== 冲正审核状态 ==========

    /** 冲正申请待审核 */
    public static final int REVERSAL_PENDING = 1;

    /** 冲正申请已通过 */
    public static final int REVERSAL_APPROVED = 2;

    /** 冲正申请已驳回 */
    public static final int REVERSAL_REJECTED = 3;

    // ========== 预算结转规则类型 ==========

    /** 全额结转 */
    public static final String CARRYOVER_TYPE_FULL = "FULL";

    /** 按比例结转 */
    public static final String CARRYOVER_TYPE_PERCENTAGE = "PERCENTAGE";

    /** 限额结转 */
    public static final String CARRYOVER_TYPE_CAPPED = "CAPPED";

    // ========== 待审核记录处理策略 ==========

    /** 忽略待审核记录 */
    public static final String PENDING_POLICY_IGNORE = "IGNORE";

    /** 预留待审核金额 */
    public static final String PENDING_POLICY_RESERVE = "RESERVE";

    // ========== 调整单类型 ==========

    /** 冲正调整 */
    public static final String ADJUSTMENT_REVERSAL = "REVERSAL";

    /** 补审核调整 */
    public static final String ADJUSTMENT_SUPPLEMENTARY = "SUPPLEMENTARY_AUDIT";

}
