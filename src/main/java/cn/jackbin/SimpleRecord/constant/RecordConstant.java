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

    // ========== 结转 & 调整 & 快照 审计操作类型 ==========

    public static final String ACTION_CARRYFORWARD_EXECUTED = "CARRYFORWARD_EXECUTED";
    public static final String ACTION_CARRYFORWARD_ROLLBACK = "CARRYFORWARD_ROLLBACK";
    public static final String ACTION_CARRYFORWARD_RULE_CREATED = "CARRYFORWARD_RULE_CREATED";
    public static final String ACTION_CARRYFORWARD_RULE_UPDATED = "CARRYFORWARD_RULE_UPDATED";
    public static final String ACTION_ADJUSTMENT_CREATED = "ADJUSTMENT_CREATED";
    public static final String ACTION_ADJUSTMENT_APPLIED = "ADJUSTMENT_APPLIED";
    public static final String ACTION_ADJUSTMENT_REVERSED = "ADJUSTMENT_REVERSED";
    public static final String ACTION_SNAPSHOT_CAPTURED = "SNAPSHOT_CAPTURED";
    public static final String ACTION_SNAPSHOT_RECALCULATED = "SNAPSHOT_RECALCULATED";
    public static final String ACTION_CLOSING_RECALCULATED = "CLOSING_RECALCULATED";

    // ========== 结转规则类型 ==========
    public static final String CARRYFORWARD_RULE_FULL = "FULL";
    public static final String CARRYFORWARD_RULE_PARTIAL = "PARTIAL";
    public static final String CARRYFORWARD_RULE_NONE = "NONE";
    public static final String CARRYFORWARD_RULE_CUSTOM = "CUSTOM";

    // ========== 超支处理模式 ==========
    public static final String OVERSPENT_CARRY_DEBT = "CARRY_DEBT";
    public static final String OVERSPENT_WRITE_OFF = "WRITE_OFF";
    public static final String OVERSPENT_CAP_AT_ZERO = "CAP_AT_ZERO";

    // ========== 调整类型 ==========
    public static final String ADJUST_REVERSAL_DIFF = "REVERSAL_DIFF";
    public static final String ADJUST_SUPPLEMENT = "SUPPLEMENT";
    public static final String ADJUST_AUDIT_DIFF = "AUDIT_DIFF";
    public static final String ADJUST_CORRECTION = "CORRECTION";

    // ========== 结转日志状态 ==========
    public static final int CARRYFORWARD_LOG_ACTIVE = 1;
    public static final int CARRYFORWARD_LOG_ROLLED_BACK = 2;

}
